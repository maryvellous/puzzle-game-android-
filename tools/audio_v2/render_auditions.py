#!/usr/bin/env python3
"""Experimental V2 listening previews driven by real Java membrane CSV.

This is a RESEARCH RENDERER, not an Android DSP engine. It chooses and mixes
partial Bloop material for listening without ever replacing a complete fusion.
Future shipping DSP must have one implementation reusable offline and on Android.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import wave
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.signal import resample_poly

from material_bank import BLOOPS, RATE, decode, regions, split, write_wav

SCENARIOS = ("impact", "slide", "pressure", "release", "fusion")
TOTAL_SECONDS = 2.0


def playback(src: np.ndarray, rate: float) -> np.ndarray:
    if not .5 <= rate <= 1.5:
        raise ValueError("Out-of-range playback rate")
    # Preserves the complete source in its resampled duration; no cut.
    from fractions import Fraction
    frac = Fraction(1.0 / rate).limit_denominator(320)
    return resample_poly(src, frac.numerator, frac.denominator)


def blob_rate(radius: float) -> float:
    if radius <= 0:
        return 1.0
    return float(np.clip((32.0 / radius) ** .40, .70, 1.35))


def place(mix: np.ndarray, start: float, voice: np.ndarray, gain: float) -> None:
    begin = int(round(start * RATE))
    if begin < 0:
        raise ValueError("Negative voice start")
    end = begin + len(voice)
    if end > len(mix):
        # Rendering output must be extended to keep COMPLETE fusion tails.
        raise ValueError("A voice would be truncated by the render buffer")
    mix[begin:end] += voice * gain


def read_csv(path: Path) -> dict[str, list[dict]]:
    rows = defaultdict(list)
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row["scenario"] in SCENARIOS:
                rows[row["scenario"]].append(row)
    for scenario in SCENARIOS:
        if not rows[scenario]:
            raise ValueError(f"Missing real physics scenario: {scenario}")
    return rows


def group_pairs(rows: list[dict]) -> dict[tuple[int, int], list[dict]]:
    grouped = defaultdict(list)
    for row in rows:
        if row["type"] != "contact":
            continue
        a, b = int(row["blob_a"]), int(row["blob_b"])
        if a < 1 or b <= a:
            raise ValueError("Invalid stable pair identity")
        grouped[(a, b)].append(row)
    return grouped


def smoothed(values: np.ndarray, attack_s: float, release_s: float) -> np.ndarray:
    result = np.zeros_like(values)
    current = 0.0
    up = 1.0 - math.exp(-1.0 / (RATE * attack_s))
    down = 1.0 - math.exp(-1.0 / (RATE * release_s))
    for i, goal in enumerate(values):
        current += (goal - current) * (up if goal > current else down)
        result[i] = current
    return result


def tail_texture(tail: np.ndarray, length: int) -> np.ndarray:
    """Very low level continuous tail texture, overlap-added to avoid clicks."""
    if len(tail) < 128:
        raise ValueError("Too little Bloop tail for a continuous texture")
    grain = tail.copy()
    n = len(grain)
    hop = max(1, n // 3)
    win = np.hanning(n)
    output = np.zeros(length, dtype=np.float64)
    weights = np.zeros(length, dtype=np.float64)
    # Offset the first grain so the beginning has a fade rather than a click.
    for position in range(0, length, hop):
        take = min(n, length - position)
        output[position:position + take] += grain[:take] * win[:take]
        weights[position:position + take] += win[:take]
    mask = weights > 0.01
    output[mask] /= weights[mask]
    output[~mask] = 0.0
    return output


def contact_envelope(rows: list[dict], count: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """One acoustic control per pair and simulation step, never per solver pass."""
    slip = np.zeros(count)
    pressure = np.zeros(count)
    normal = np.zeros(count)
    for row in rows:
        at = min(count - 1, max(0, round(float(row["time"]) * RATE)))
        width = max(1, round(float(row["dt"]) * RATE))
        stop = min(count, at + width)
        slip[at:stop] = max(0, float(row["tangent_speed"]))
        pressure[at:stop] = np.clip(float(row["compression"]), 0, 1)
        normal[at:stop] = max(0, float(row["normal_speed"]))
    return slip, pressure, normal



def contact_texture(observations: list[dict], tail: np.ndarray,
                    count: int) -> np.ndarray:
    """Continuously driven tail: quiet at rest, no independent repeated sample."""
    slip, pressure, _ = contact_envelope(observations, count)
    radius = (float(observations[0]["radius_a"]) +
              float(observations[0]["radius_b"])) / 2
    texture = tail_texture(playback(tail, blob_rate(radius)), count)
    rms = float(np.sqrt(np.mean(texture * texture)))
    if rms > 1e-7:
        texture *= min(4.0, .14 / rms)
    varying_pressure = np.abs(np.diff(pressure, prepend=pressure[0]))
    local_motion = np.minimum(1.0, slip / 130.0) ** .65
    deformation = np.minimum(1.0, varying_pressure * 14.0)
    drive = np.minimum(1.0, .75 * local_motion + .25 * deformation)
    drive *= (slip > 4) | (varying_pressure > .002)
    envelope = smoothed(drive, attack_s=.025, release_s=.085)
    return texture * envelope


def render_scenario(scenario: str, rows: list[dict], complete: list[np.ndarray],
                    attacks: list[np.ndarray], tails: list[np.ndarray]) -> tuple[np.ndarray, dict]:
    count = int(TOTAL_SECONDS * RATE)
    result = np.zeros(count, dtype=np.float64)
    types = defaultdict(int)
    for row in rows:
        types[row["type"]] += 1
    if scenario == "fusion":
        merges = [r for r in rows if r["type"] == "merge"]
        if len(merges) != 1:
            raise ValueError("One real fusion must produce exactly one full Bloop")
        # Select one entire original source, without clipping the tail.
        for i, merge in enumerate(merges):
            voice = playback(complete[i % 4], 1.0)
            place(result, float(merge["time"]), voice, .48)
        assert types["contact"] == 0, "Same-tier fusion shouldn't cause impact"
    else:
        grouped = group_pairs(rows)
        for idx, (pair, observations) in enumerate(sorted(grouped.items())):
            slip, pressure, approach = contact_envelope(observations, count)
            # A real closing impulse can excite the attack. A mere persistently
            # touching pair does NOT cause a replay every frame.
            if scenario == "impact":
                first = min(float(row["time"]) for row in observations)
                peak = float(np.max(approach))
                if peak > 18:
                    strength = np.clip(math.log1p((peak - 18) / 30) /
                                       math.log1p(300 / 30), 0, 1)
                    radius = (float(observations[0]["radius_a"]) +
                              float(observations[0]["radius_b"])) / 2
                    voice = playback(attacks[idx % 4], blob_rate(radius))
                    place(result, first, voice, .18 * strength)
            if scenario in ("slide", "release", "pressure"):
                result += contact_texture(observations, tails[idx % 4], count) * .18
    peak_before = float(np.max(np.abs(result)))
    # Whole-mix headroom, not clipping, voice stealing, or truncation.
    if peak_before > .87:
        result *= .87 / peak_before
    metric = {
        "scenario": scenario,
        "events": dict(types),
        "peak_before_gain": round(peak_before, 6),
        "peak_after_gain": round(float(np.max(np.abs(result))), 6),
        "seconds": TOTAL_SECONDS,
        "nonfusion_full_bloops": 0 if scenario != "fusion" else None,
        "complete_fusion_voices": int(types["merge"]),
    }
    return result, metric



def render_real_scene_stress(rows: list[dict], complete: list[np.ndarray],
                             attacks: list[np.ndarray],
                             tails: list[np.ndarray]) -> tuple[np.ndarray, dict]:
    """Actual multiple-blob interactions; never invent a merge or contact."""
    count = RATE * 3
    output = np.zeros(count, dtype=np.float64)
    groups = group_pairs(rows)
    # Limit *collective* texture gain rather than letting N contact voices
    # increase sound linearly. The mixer is still an offline research prototype.
    texture_gain = .13 / math.sqrt(max(1., len(groups) / 3.0))
    impact_count = 0
    for i, (pair, observations) in enumerate(sorted(groups.items())):
        output += contact_texture(observations, tails[i % 4], count) * texture_gain
        first = float(observations[0]["time"])
        # Only first contact is a possible attack, not each of the 5 solver
        # passes or subsequent 60Hz contact observations.
        early = [s for s in observations if float(s["time"]) <= first + .055]
        peak = max(float(s["normal_speed"]) for s in early)
        if peak > 18:
            amount = float(np.clip(math.log1p((peak - 18) / 30) /
                                   math.log1p(300 / 30), 0, 1))
            radius = (float(observations[0]["radius_a"]) +
                      float(observations[0]["radius_b"])) / 2
            place(output, first, playback(attacks[i % 4], blob_rate(radius)),
                  .11 * amount)
            impact_count += 1
    merges = [row for row in rows if row["type"] == "merge"]
    for i, event in enumerate(merges):
        # Include the full source, including its own natural decay/padding.
        place(output, float(event["time"]), playback(complete[i % 4], 1.0), .42)
    before = float(np.max(np.abs(output)))
    if before > .87:
        output *= .87 / before
    return output, {
        "label": "REAL_12_BLOB_SIMULATION_FROM_JAVA_SOLVER",
        "measured_pair_count": len(groups),
        "significant_initial_impacts": impact_count,
        "full_merged_bloops": len(merges),
        "peak_before_gain": round(before, 6),
        "peak_after_gain": round(float(np.max(np.abs(output))), 6),
        "no_voice_truncated": True,
    }


def render_stress(complete: list[np.ndarray], attacks: list[np.ndarray]) -> tuple[np.ndarray, dict]:
    """Synthetic scheduling stress test ONLY, not an invented physics trace."""
    count = RATE * 7
    result = np.zeros(count, dtype=np.float64)
    for i in range(12):
        place(result, .22 + i * .145, playback(complete[i % 4], 1.0), .42)
    for i in range(80):
        place(result, .02 + i * .056, playback(attacks[i % 4], 1.0), .07)
    peak = float(np.max(np.abs(result)))
    if peak > .87:
        result *= .87 / peak
    return result, {
        "label": "SYNTHETIC_SCHEDULING_STRESS_NOT_REAL_PHYSICS",
        "complete_merge_voices": 12, "short_partial_voices": 80,
        "peak_before_gain": round(peak, 6),
        "peak_after_gain": round(float(np.max(np.abs(result))), 6),
        "no_voice_was_truncated": True,
    }


def run(sources: Path, trace_path: Path, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    complete, attacks, tails = [], [], []
    for name in BLOOPS:
        full = decode(sources / f"{name}.mp3")
        r = regions(full)
        attack, tail, _ = split(full, r)
        complete.append(full)
        attacks.append(attack)
        tails.append(tail)
    by_scenario = read_csv(trace_path)
    metrics = {}
    clips = []
    for scenario in SCENARIOS:
        rendered, metric = render_scenario(
            scenario, by_scenario[scenario], complete, attacks, tails)
        write_wav(output / f"{scenario}_from_real_membranes.wav", rendered)
        metrics[scenario] = metric
        clips.append(rendered)
    # Order is explicitly documented; no secret fusion appended to non-fusion.
    spacing = np.zeros(round(RATE * .3))
    write_wav(output / "listen_real_physics_v2.wav",
              np.concatenate([x for clip in clips for x in (clip, spacing)]))
    stress, info = render_stress(complete, attacks)
    write_wav(output / "synthetic_mixer_stress.wav", stress)
    metrics["synthetic_stress"] = info
    real_trace = trace_path.parent / "scene_stress_trace.csv"
    if not real_trace.is_file():
        raise ValueError("Missing true multiple-blob stress trace")
    with real_trace.open(newline="", encoding="utf-8") as handle:
        scene_rows = [row for row in csv.DictReader(handle)
                      if row["scenario"] == "stress_real"]
    if not scene_rows:
        raise ValueError("Real multiple-blob trace has no observations")
    real_scene, real_info = render_real_scene_stress(
        scene_rows, complete, attacks, tails)
    write_wav(output / "real_12_blob_scene_stress.wav", real_scene)
    metrics["real_scene_stress"] = real_info
    (output / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    (output / "LISTENING_ORDER.md").write_text(
        "# Audio V2 — offline research audition\n\n"
        "1. impact_from_real_membranes.wav — attack fragment ONLY\n"
        "2. slide_from_real_membranes.wav — tail texture ONLY\n"
        "3. pressure_from_real_membranes.wav — tail modulated by moving compression; "
        "static compression can be silent\n"
        "4. release_from_real_membranes.wav — no new sound at release\n"
        "5. fusion_from_real_membranes.wav — one WHOLE original Bloop\n\n"
        "listen_real_physics_v2.wav contains these five clips **in this order** "
        "with 300ms silent gaps; there is no fusion at the end of clips 1–4. "
        "synthetic_mixer_stress.wav is a technical nonphysical overlap test. "
        "real_12_blob_scene_stress.wav uses actual overlapping membrane contact "
        "data (not manually manufactured events); fusion voices remain complete. "
        "All previews are unapproved prototypes. No Android integration.\n",
        encoding="utf-8")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--sources", type=Path, required=True)
    p.add_argument("--trace", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    run(a.sources, a.trace, a.output)
