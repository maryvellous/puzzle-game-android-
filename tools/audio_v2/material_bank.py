#!/usr/bin/env python3
"""Offline Bloop material analysis for Lava Blobs Audio V2.

This tool is an audition/measurement fixture, NOT a runtime synthesizer.
It never edits the four original MP3 files and never publishes extracted
stems. Every region boundary is a measured HEURISTIC awaiting listening review.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
import subprocess
import wave
from pathlib import Path

import numpy as np

RATE = 44100
BLOOPS = ("bloop_1", "bloop_2", "bloop_3", "bloop_4")


def decode(path: Path) -> np.ndarray:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    process = subprocess.run(
        [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(path),
         "-ac", "1", "-ar", str(RATE), "-f", "f32le", "pipe:1"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
    )
    audio = np.frombuffer(process.stdout, dtype="<f4").astype(np.float64)
    if audio.size < RATE // 10 or not np.all(np.isfinite(audio)):
        raise ValueError(f"Invalid Bloop source: {path}")
    return np.clip(audio, -1, 1)


def rms_frames(audio: np.ndarray, milliseconds: int = 5) -> np.ndarray:
    stride = round(RATE * milliseconds / 1000)
    padded = np.pad(audio, (0, (-len(audio)) % stride))
    frames = padded.reshape(-1, stride)
    levels = np.sqrt(np.mean(frames * frames, axis=1) + 1e-14)
    return np.convolve(levels, np.ones(3) / 3, mode="same")


def regions(audio: np.ndarray) -> dict:
    """Estimate onset / attack / core / tail from measured RMS, not MP3 length.

    Boundaries are acoustic candidates. Do not treat them as editorially
    approved or as a universal division of every Bloop.
    """
    if len(audio) < int(RATE * .10):
        raise ValueError("Bloop source is too short")
    env = rms_frames(audio)
    stride = round(RATE * .005)
    peak_level = float(np.max(env))
    threshold = max(10 ** (-50 / 20), .025 * peak_level)
    active = np.flatnonzero(env >= threshold)
    if active.size == 0:
        raise ValueError("No active signal")
    onset_frame = int(active[0])
    end_frame = int(active[-1]) + 1
    first = onset_frame * stride
    last = min(end_frame * stride, len(audio))
    if last - first < int(.065 * RATE):
        raise ValueError("Too little active material to segment")
    peak_frame = int(np.argmax(env[onset_frame:end_frame]) + onset_frame)
    # The ATTACK should not absorb the Bloop's dominant recognizable nucleus.
    # Use the measured onset and the first energy crest, capped conservatively.
    attack_frame = min(onset_frame + 4, max(onset_frame + 2, peak_frame - 2))
    attack_frame = max(onset_frame + 2, attack_frame)
    attack_end = min(attack_frame * stride, last - int(.030 * RATE))
    # Candidate tail begins on the measured decay side of the loudest crest.
    decay_begin = max(peak_frame + 3, int(attack_end / stride) + 3)
    candidates = [i for i in range(decay_begin, end_frame - 2)
                  if env[i] <= peak_level * .45 and
                  float(np.mean(env[i:i + 3])) <= peak_level * .60]
    fallback = False
    if candidates:
        tail_start = candidates[0] * stride
    else:
        # No well-defined decay measured: provide a *flagged* conservative
        # audition candidate rather than claiming an automatic segmentation.
        tail_start = last - int(.025 * RATE)
        fallback = True
    tail_start = max(attack_end + int(.012 * RATE), tail_start)
    tail_start = min(tail_start, last - int(.012 * RATE))
    if tail_start <= attack_end or tail_start >= last:
        raise ValueError("Regions cannot be separated; manual review required")
    return {
        "onset": first,
        "attack_end": int(attack_end),
        "tail_start": int(tail_start),
        "active_end": int(last),
        "peak_sample": peak_frame * stride,
        "active_rms_threshold": round(float(threshold), 7),
        "tail_decay_not_found": fallback,
        "review_required": True,
    }


def smooth_edges(audio: np.ndarray, milliseconds: float = 6) -> np.ndarray:
    result = audio.copy()
    count = min(round(RATE * milliseconds / 1000), len(result) // 2)
    if count:
        envelope = np.sin(np.linspace(0, np.pi / 2, count)) ** 2
        result[:count] *= envelope
        result[-count:] *= envelope[::-1]
    return result


def join_partial(attack: np.ndarray, tail: np.ndarray) -> np.ndarray:
    """Short crossfade. Never includes the core of the original Bloop."""
    count = min(round(RATE * .007), len(attack) // 3, len(tail) // 3)
    if count < 1:
        return np.concatenate((attack, tail))
    cross = np.linspace(0, np.pi / 2, count)
    bridge = attack[-count:] * np.cos(cross) ** 2 + \
             tail[:count] * np.sin(cross) ** 2
    return np.concatenate((attack[:-count], bridge, tail[count:]))


def split(audio: np.ndarray, r: dict) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    # Use only ATTACK and TAIL, excluding the entire candidate core.
    attack = smooth_edges(audio[r["onset"]:r["attack_end"]])
    tail = smooth_edges(audio[r["tail_start"]:r["active_end"]])
    partial = smooth_edges(join_partial(attack, tail), 4)
    if min(len(attack), len(tail)) < 200 or len(partial) >= len(audio):
        raise ValueError("Partial material is invalid")
    return attack, tail, partial


def write_wav(path: Path, samples: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    a = np.asarray(samples, dtype=np.float64)
    if not len(a) or not np.all(np.isfinite(a)):
        raise ValueError(f"Invalid audio output: {path}")
    peak = float(np.max(np.abs(a)))
    if peak > .94:
        a = a * (.94 / peak)
    pcm = np.rint(np.clip(a, -.99, .99) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes(pcm.tobytes())


def measure(name: str, src: Path, result_dir: Path) -> dict:
    audio = decode(src)
    r = regions(audio)
    attack, tail, partial = split(audio, r)
    parts = result_dir / "isolated_auditions"
    write_wav(parts / f"{name}_attack_only.wav", attack)
    write_wav(parts / f"{name}_tail_only.wav", tail)
    write_wav(parts / f"{name}_attack_tail_no_core.wav", partial)
    # The WHOLE original (including any encoding padding) is the only
    # candidate for the actual fusion. No synthesised replacement.
    write_wav(result_dir / "full_fusions" / f"{name}_complete.wav", audio)
    metrics = {
        "source_sha256": hashlib.sha256(src.read_bytes()).hexdigest(),
        "file_duration_s": round(len(audio) / RATE, 5),
        "rms_full": round(float(np.sqrt(np.mean(audio ** 2))), 6),
        "peak_full": round(float(np.max(np.abs(audio))), 6),
        "regions_samples": {k: v for k, v in r.items() if k in
                            ("onset", "attack_end", "tail_start", "active_end", "peak_sample")},
        "regions_seconds": {k: round(v / RATE, 5) for k, v in r.items() if k in
                            ("onset", "attack_end", "tail_start", "active_end", "peak_sample")},
        "active_rms_threshold": r["active_rms_threshold"],
        "tail_decay_not_found": r["tail_decay_not_found"],
        "review_required": True,
        "full_fusion": f"full_fusions/{name}_complete.wav",
        "attack": f"isolated_auditions/{name}_attack_only.wav",
        "tail": f"isolated_auditions/{name}_tail_only.wav",
        "partial": f"isolated_auditions/{name}_attack_tail_no_core.wav",
    }
    return metrics


def report_plot(sources: dict, out: Path) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    figure, axes = plt.subplots(4, 1, figsize=(11, 9), layout="constrained")
    for ax, (name, item) in zip(axes, sources.items()):
        audio = item["audio"]
        r = item["region"]
        ax.plot(np.arange(len(audio)) / RATE, audio, lw=.55)
        for tag, color in (("onset", "green"), ("attack_end", "orange"),
                           ("tail_start", "blue"), ("active_end", "gray")):
            ax.axvline(r[tag] / RATE, label=tag, color=color, ls="--", lw=1)
        ax.set_xlim(0, len(audio) / RATE)
        ax.set_title(name + " — boundary candidates (review by ear)")
        ax.set_ylabel("Amplitude")
    axes[-1].set_xlabel("Seconds")
    axes[0].legend(ncol=4, loc="upper right", fontsize=8)
    figure.savefig(out / "bloop_regions_review.png", dpi=140)
    plt.close(figure)


def run(source_dir: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    items = {}
    pictures = {}
    for name in BLOOPS:
        src = source_dir / f"{name}.mp3"
        items[name] = measure(name, src, output_dir)
        pictures[name] = {"audio": decode(src), "region": regions(decode(src))}
    report_plot(pictures, output_dir)
    document = {
        "schema": "lava-blobs-bloop-bank-audition-v1",
        "status": "MEASURED_CANDIDATES_NOT_ARTISTICALLY_APPROVED",
        "source": "four original MP3s; 44.1kHz mono PCM for previews only",
        "no_core_in_nonfusion": True,
        "boundaries_are_heuristics": True,
        "note": "Listen to all candidate regions: acoustic energy alone cannot certify the fusion's perceptual core.",
        "bloops": items,
    }
    (output_dir / "material_bank_candidates.json").write_text(
        json.dumps(document, indent=2), encoding="utf-8")
    (output_dir / "LISTEN_FIRST.md").write_text(
        "# V2 material auditions\n\n"
        "These are NOT final sound effects. Each source has a full original fusion, "
        "an isolated attack, an isolated tail and a candidate attack+tail without "
        "the measured core. Review the waveform chart and listen before approving "
        "any boundary. Never call partials a fusion or distribute an isolated sound pack.\n",
        encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    run(args.sources, args.output)
