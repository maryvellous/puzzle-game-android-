#!/usr/bin/env python3
"""C3 listening trial: fusion is the foreground; all other sounds are incidental.

This script consumes offline traces and C1/C2 WAVs. It does not alter Android
gameplay, SFX, or the source Bloop recordings. Friction uses ONLY the late,
soft portion of the prior friction sound (not its original loud beginning).
"""
from __future__ import annotations

import argparse
import json
import wave
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.signal import butter, sosfiltfilt

from analyze_and_render import SAMPLE_RATE, save_wav
from render_physics_prototype import load_trace

GAP_SECONDS = .65


def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as f:
        if f.getframerate() != SAMPLE_RATE or f.getnchannels() != 1 or f.getsampwidth() != 2:
            raise ValueError(f"Expected 44.1kHz mono PCM 16 WAV: {path}")
        samples = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2")
    return samples.astype(np.float64) / 32768.0


def peak(x: np.ndarray) -> float:
    return float(np.max(np.abs(x)))


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x*x)))


def reduced(x: np.ndarray, upper_peak: float) -> np.ndarray:
    """Only attenuate; never raise a quiet input or normalise to loudness."""
    p = peak(x)
    return x * min(1.0, upper_peak/max(1.e-12,p))


def smoother(x: np.ndarray, ms: float) -> np.ndarray:
    return gaussian_filter1d(x, sigma=max(1, ms*SAMPLE_RATE/1000), mode="constant", cval=0.0)


def active_end(x: np.ndarray, ms: int=15) -> int:
    frame=max(1,round(ms*SAMPLE_RATE/1000))
    n=len(x)//frame
    level=np.sqrt(np.mean(x[:n*frame].reshape(n,frame)**2,axis=1))
    indices=np.flatnonzero(level >= max(.004, float(np.max(level))*.18))
    if not len(indices):raise ValueError("Friction source has no audible tail")
    return min(len(x),int((indices[-1]+1)*frame))


def friction_tail_from_previous(x: np.ndarray) -> tuple[np.ndarray, dict]:
    """Select only the *end* of the old sliding sound, excluding its onset."""
    end=active_end(x)
    start=max(0,end-round(.235*SAMPLE_RATE))
    part=x[start:end].copy()
    if len(part)<int(.10*SAMPLE_RATE):
        raise ValueError("Too little friction tail")
    # This softness adjustment affects friction alone, not Bloop fusions.
    part=sosfiltfilt(butter(2,2100,fs=SAMPLE_RATE,output="sos"),part)
    part-=np.mean(part)
    overlap=min(int(.044*SAMPLE_RATE),len(part)//4)
    loop=part[:-overlap].copy()
    fade=np.sin((np.arange(overlap)+.5)/overlap*np.pi/2)**2
    loop[:overlap]=part[-overlap:]*(1-fade)+part[:overlap]*fade
    # loop[-1] is immediately followed by the tail sample represented
    # by loop[0], so the circular seam is source-phase continuous.
    return loop,{"tail_start_sec":round(start/SAMPLE_RATE,3),
                 "tail_end_sec":round(end/SAMPLE_RATE,3),
                 "loop_sec":round(len(loop)/SAMPLE_RATE,3)}


def friction_from_physics(old_sliding: np.ndarray, trace: dict[str,np.ndarray],
                          max_peak: float, brightness: float) -> tuple[np.ndarray,dict]:
    base, info=friction_tail_from_previous(old_sliding)
    n=len(old_sliding)
    time=np.arange(n)/SAMPLE_RATE
    c=np.interp(time,trace["time"],trace["contact"],left=0,right=0)
    v=np.interp(time,trace["time"],trace["tangent_speed"],left=0,right=0)
    # Surface contact AND relative sliding, not a note on touch.
    slider=np.clip((v-15)/150.,0.,1.)**1.2
    envelope=smoother(np.clip(c,0,1)*slider,35)
    # Slow the texture a little to remove the frantic / overly lively feel.
    sample_rate_ratio=.70 if brightness<.6 else .87
    indexes=np.mod(np.arange(n,dtype=np.float64)*sample_rate_ratio,len(base))
    i=np.floor(indexes).astype(np.int64)
    u=indexes-i
    raw=base[i]*(1-u)+base[(i+1)%len(base)]*u
    raw-=np.mean(raw)
    if brightness < .6:
        raw=sosfiltfilt(butter(2,1200,fs=SAMPLE_RATE,output="sos"),raw)
    out=raw*envelope
    out=reduced(out,max_peak)
    info.update({"source":"last ~235ms of rejected C1 sliding texture only",
                 "peak_cap":max_peak,
                 "physics":"local surface slip under contact",
                 "brightness":brightness,
                 "peak":round(peak(out),5),
                 "rms":round(rms(out),6)})
    return out,info


def montage(waves: list[np.ndarray]) -> np.ndarray:
    pause=np.zeros(int(round(GAP_SECONDS*SAMPLE_RATE)))
    return np.concatenate([part for sound in waves for part in (sound,pause)])


def run(old_dir:Path,c2_dir:Path,trace_path:Path,output:Path)->None:
    output.mkdir(parents=True,exist_ok=True)
    fusion=read_wav(old_dir/"fusion_physics.wav")
    old_friction=read_wav(old_dir/"sliding_physics.wav")
    if peak(fusion)<.20:raise ValueError("Fusion anchor too quiet/unexpected")
    data=load_trace(trace_path)
    fusion_peak=peak(fusion)
    # A deliberately strict amplitude hierarchy. These are *candidate mix*
    # caps, not psychoacoustic guarantees and not yet a shipping sound policy.
    caps={
        "impact_a":fusion_peak*.21,
        "impact_b":fusion_peak*.21,
        "compression_a":fusion_peak*.105,
        "compression_b":fusion_peak*.105,
        "friction_whisper":fusion_peak*.055,
        "friction_ghost":fusion_peak*.027,
    }
    variants={
        "impact_a":reduced(read_wav(c2_dir/"impact_A_bloop1.wav"),caps["impact_a"]),
        "impact_b":reduced(read_wav(c2_dir/"impact_B_bloop3.wav"),caps["impact_b"]),
        "compression_a":reduced(read_wav(c2_dir/"compression_A_flex.wav"),caps["compression_a"]),
        "compression_b":reduced(read_wav(c2_dir/"compression_B_texture.wav"),caps["compression_b"]),
    }
    variants["friction_whisper"], info1=friction_from_physics(
        old_friction,data["sliding"],caps["friction_whisper"],brightness=.75)
    variants["friction_ghost"], info2=friction_from_physics(
        old_friction,data["sliding"],caps["friction_ghost"],brightness=.35)
    for name, sound in variants.items():
        assert np.all(np.isfinite(sound)),name
        assert peak(sound)<=caps[name]+.00001,(name,peak(sound),caps[name])
        assert len(sound)==len(fusion), (name,len(sound),len(fusion))
        save_wav(output/(name+".wav"),sound)

    save_wav(output/"01_impact_newA_soft_newB_soft.wav",montage([
        variants["impact_a"],variants["impact_b"]]))
    save_wav(output/"02_compression_newA_soft_newB_soft.wav",montage([
        variants["compression_a"],variants["compression_b"]]))
    save_wav(output/"03_friction_old_tail_whisper_tail_ghost.wav",montage([
        old_friction,variants["friction_whisper"],variants["friction_ghost"]]))
    save_wav(output/"04_combined_soft_impact_compression_friction_fusion.wav",montage([
        variants["impact_a"],variants["compression_b"],
        variants["friction_whisper"],fusion]))
    save_wav(output/"fusion_original_prototype_reference.wav",fusion)
    report={
        "version":"C3 / perceptual hierarchy and near-silent friction",
        "state":"OFFLINE LISTENING EXPERIMENT, not an approved mix or Android code",
        "feedback":{
            "impact":"C2 may be approaching right direction; needs to be much quieter than fusion",
            "compression":"C1 entirely rejected; C2 not approved; only subdued trials",
            "friction":"Use only the late part, significantly less pronounced and less lively",
            "fusion":"KEEP as dominant, acceptable anchor",
            "remaining_artifact":"Slight frying/distortion in hybrid sound family remains open"
        },
        "original_fusion_peak":round(fusion_peak,5),
        "hard_peak_caps_as_fraction_of_fusion":{
            k:round(v/fusion_peak,4) for k,v in caps.items()},
        "sound_metrics":{
            name:{"peak":round(peak(w),5),"rms":round(rms(w),6),
                  "peak_vs_fusion":round(peak(w)/fusion_peak,4)}
            for name,w in variants.items()},
        "friction_tail_whisper":info1,
        "friction_tail_ghost":info2,
        "important":"Peak ratios alone do not prove perceived loudness; validate with music, multi-blob stress and subjective listening"
    }
    (output/"C3_MIX_REPORT.json").write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf8")
    (output/"LISTENING_GUIDE.txt").write_text(
        "Lava Blobs C3 — all non-fusion sounds are deliberately quieter.\n"
        "01: Impact A soft / Impact B soft\n"
        "02: Compression A soft / Compression B soft (unapproved timbres)\n"
        "03: OLD sliding / tail-only whisper / even quieter tail-only ghost\n"
        "04: Impact A / Compression B / tail-only whisper / unchanged fusion\n"
        "The friction timbre comes ONLY from the end of the previous sliding WAV.\n"
        "The test uses the SAME real Java BlobWorld solver trace as previous trials.\n"
        "Not integrated into Android. User evaluation is the acceptance gate.\n",encoding="utf8")
    print(json.dumps(report,indent=2,ensure_ascii=False),flush=True)
    print("SUCCESS: subdued impact/compression; tail-only almost-silent friction; original fusion unchanged",flush=True)


if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--old",type=Path,default=Path("build/audio_lab/two_blob"))
    p.add_argument("--c2",type=Path,default=Path("build/audio_lab/two_blob_rework"))
    p.add_argument("--traces",type=Path,default=Path("app/build/audio_lab/two_blob_physics.csv"))
    p.add_argument("--output",type=Path,default=Path("build/audio_lab/two_blob_subtle"))
    args=p.parse_args()
    run(args.old,args.c2,args.traces,args.output)
