#!/usr/bin/env python3
"""Lava Blobs C4.1: fix misleading audition montages, make impacts audible.

C4 mistakenly appended the merge Bloop to EVERY comparison montage. This
script deliberately NEVER uses the merger in a non-fusion preview. It
preserves C4 compression B and adhesive friction byte-for-byte, and creates
three listening levels of the same physical-impact material.

No changes to the game or original source recordings.
"""
from __future__ import annotations

import argparse
import json
import shutil
import wave
from pathlib import Path

import numpy as np

SR = 44100
GAP = .42


def read_pcm16(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as wav:
        if (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) != (SR, 1, 2):
            raise ValueError(f"Not mono 44.1kHz PCM16: {path}")
        signal = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2")
    if not signal.size:
        raise ValueError(f"Empty recording: {path}")
    return signal.astype(np.float64) / 32768.0


def peak(signal: np.ndarray) -> float:
    return float(np.max(np.abs(signal)))


def rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def write_pcm16(path: Path, samples: np.ndarray) -> None:
    if len(samples) < 1 or not np.all(np.isfinite(samples)) or peak(samples) >= .90:
        raise ValueError(f"Invalid/clipping preview: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.rint(samples * 32767).clip(-32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def impact_for_audition(original: np.ndarray,
                        fusion_peak: float,
                        fraction: float) -> tuple[np.ndarray, dict]:
    """Calibrate a fixed physical test event, NOT a per-event gameplay normalizer.

    Old C4 was too quiet: it used a 20% peak CAP with attenuation only.
    Now rescale the measured fixed scenario to two explicit audition levels,
    both still far below the fusion's peak. Don't touch approved sounds.
    """
    original_peak = peak(original)
    if original_peak < 0.002:
        raise ValueError("Impact source is genuinely inaudible or missing")
    if not .2 < fraction <= .40:
        raise ValueError("Audition impact cannot exceed 40% of fusion peak")
    target = fusion_peak * fraction
    if target >= .85:
        raise ValueError("Unsafe preview gain")
    result = original * (target / original_peak)
    if peak(result) >= .85:
        raise ValueError("Impact exceeded headroom")
    info = {
        "relative_peak_to_fusion": round(peak(result) / fusion_peak, 3),
        "original_peak": round(original_peak, 6),
        "new_peak": round(peak(result), 6),
        "gain_db": round(20 * np.log10(peak(result) / original_peak), 2),
        "rms": round(rms(result), 6),
    }
    return result, info


def audible_excerpt(original: np.ndarray) -> np.ndarray:
    """Shorten silent space around an impact ONLY for an audition montage.

    This does not create new events or alter the impact waveform and is
    *never* used by game/runtime code.
    """
    p = peak(original)
    nonzero = np.flatnonzero(np.abs(original) >= max(0.00012, p * .012))
    if not nonzero.size:
        raise ValueError("Impact preview has no audible transient")
    left = max(0, int(nonzero[0]) - int(.045 * SR))
    right = min(len(original), max(int(nonzero[-1]) + int(.080 * SR),
                                  int(nonzero[0]) + int(.28 * SR)))
    return original[left:right].copy()


def sequence(parts: list[np.ndarray]) -> np.ndarray:
    """Concatenate EXACTLY named tracks; never silently append a merge."""
    if not parts:
        raise ValueError("No sound parts")
    gap = np.zeros(int(SR * GAP))
    return np.concatenate([p for sound in parts for p in (sound, gap)])


def run(c4_dir: Path, output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    files = {
        "impact_a": c4_dir / "01_impact_A_pitch_plus_9pct.wav",
        "impact_b": c4_dir / "02_impact_B_pitch_plus_16pct.wav",
        "adhesive": c4_dir / "04_friction_adhesive_ghost.wav",
        "compression_b": c4_dir / "06_compression_B_whisper.wav",
        "fusion": c4_dir / "fusion_anchor_UNCHANGED.wav",
    }
    signals = {k: read_pcm16(v) for k, v in files.items()}
    fusion_peak = peak(signals["fusion"])
    if not .1 < fusion_peak < .9:
        raise ValueError("Unexpected reference Bloop level")

    # User liked these two C4 timbres: preserve original PCM16 files, verbatim.
    shutil.copyfile(files["adhesive"], output / "04_friction_adhesive_UNCHANGED.wav")
    shutil.copyfile(files["compression_b"], output / "05_compression_B_UNCHANGED.wav")
    shutil.copyfile(files["fusion"], output / "06_fusion_ONLY_EXPLICIT_REFERENCE.wav")
    assert (output / "04_friction_adhesive_UNCHANGED.wav").read_bytes() == files["adhesive"].read_bytes()
    assert (output / "05_compression_B_UNCHANGED.wav").read_bytes() == files["compression_b"].read_bytes()

    a32, a32_metrics = impact_for_audition(signals["impact_a"], fusion_peak, .32)
    a40, a40_metrics = impact_for_audition(signals["impact_a"], fusion_peak, .40)
    b32, b32_metrics = impact_for_audition(signals["impact_b"], fusion_peak, .32)
    write_pcm16(output / "01_impact_A_plus9_audible_32percent.wav", a32)
    write_pcm16(output / "02_impact_A_plus9_audible_40percent.wav", a40)
    write_pcm16(output / "03_impact_B_plus16_audible_32percent.wav", b32)

    # No merge / Bloop is ever appended by either montage.
    impact_excerpt = [audible_excerpt(x) for x in (a32, a40, b32)]
    impact_montage = sequence(impact_excerpt)
    write_pcm16(output / "07_COMPARE_ONLY_IMPACTS_NO_MERGE.wav", impact_montage)

    # Each source appears exactly once, in declared order; no implicit fusion.
    non_fusion_montage = sequence([
        audible_excerpt(a40), signals["adhesive"], signals["compression_b"]
    ])
    write_pcm16(output / "08_COMPARE_IMPACT_FRICTION_COMPRESSION_NO_MERGE.wav",
                non_fusion_montage)

    # Integral checks against accidental appended fusion. Each montage length
    # must be EXACTLY the intended parts plus one silence gap per part.
    gap_samples = int(SR * GAP)
    assert len(impact_montage) == sum(map(len, impact_excerpt)) + 3 * gap_samples
    assert len(non_fusion_montage) == (
        len(audible_excerpt(a40)) + len(signals["adhesive"])
        + len(signals["compression_b"]) + 3 * gap_samples
    )
    for item in [a32, a40, b32]:
        assert peak(item) < fusion_peak*.41
        assert rms(item) > rms(signals["impact_a"])
    assert rms(signals["adhesive"]) > 0 and rms(signals["compression_b"]) > 0

    meta = {
        "status": "C4.1 FIXED PREVIEWS — listening required, no game integration",
        "root_cause": "C4 montage appended merger to EVERY audition comparison",
        "isolated_previews_contain_fusion": False,
        "explicit_reference_only": "06_fusion_ONLY_EXPLICIT_REFERENCE.wav",
        "preserved_byte_identical": [
            "04_friction_adhesive_UNCHANGED.wav",
            "05_compression_B_UNCHANGED.wav",
        ],
        "audition_impact_levels": {"A32": a32_metrics, "A40": a40_metrics,
                                  "B32": b32_metrics},
        "note": ("These levels are for a fixed contact test. They are NOT a "
                 "runtime loudness normalization rule; impacts must later "
                 "respond to physical collision energy."),
        "montage_segments": {
            "07_COMPARE_ONLY_IMPACTS_NO_MERGE.wav": [
                "Impact A +9%, 32% of merger peak",
                "Impact A +9%, 40% of merger peak",
                "Impact B +16%, 32% of merger peak",
            ],
            "08_COMPARE_IMPACT_FRICTION_COMPRESSION_NO_MERGE.wav": [
                "Impact A +9%, 40% of merger peak",
                "Friction adhesive C4, unmodified",
                "Compression B C4, unmodified",
            ],
        },
    }
    (output / "C41_AUDIT.json").write_text(
        json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    (output / "READ_ME_FIRST.txt").write_text(
        "LAVA BLOBS C4.1 — REAL SOURCE OF CONFUSION FIXED\n\n"
        "Older C4 comparison WAVs secretly placed a full fusion Bloop at "
        "the end of every test. THESE comparisons DO NOT.\n\n"
        "07_COMPARE_ONLY_IMPACTS_NO_MERGE.wav: THREE real-solver contact "
        "sound variations, with short pauses, NO FUSION.\n"
        "  1. Pitch +9%, 32% of fusion peak\n"
        "  2. Pitch +9%, 40% of fusion peak (louder, still subdued)\n"
        "  3. Pitch +16%, 32% of fusion peak\n\n"
        "08_COMPARE_IMPACT_FRICTION_COMPRESSION_NO_MERGE.wav: "
        "impact +9% louder / friction adhesive unchanged / compression B "
        "unchanged. No fusion.\n\n"
        "04_friction_adhesive_UNCHANGED.wav and "
        "05_compression_B_UNCHANGED.wav are EXACT BYTE COPIES of C4.\n"
        "06_fusion_ONLY_EXPLICIT_REFERENCE.wav: only here is a full fusion "
        "included, clearly named as reference.\n\n"
        "No Android code or game sound assets modified.\n",
        encoding="utf-8",
    )
    print(json.dumps(meta, ensure_ascii=False, indent=2), flush=True)
    print("SUCCESS: no surprise merge, C4 accepted sounds preserved, impacts audible", flush=True)
    return meta


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--c4", type=Path, default=Path("build/audio_lab/two_blob_c4"))
    p.add_argument("--output", type=Path, default=Path("build/audio_lab/two_blob_c41"))
    a = p.parse_args()
    run(a.c4, a.output)
