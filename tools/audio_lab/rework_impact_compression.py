#!/usr/bin/env python3
"""Lava Blobs phase C2: physics-driven contact sound, not continuous sine tones.

A controlled offline follow-up to rejected C1 impact/compression. Uses the
existing real Java BlobWorld CSV. Sound source: Bloop 1/3 from the four
licensed original Bloop files. Fusion/sliding C1 are kept unchanged as anchors.
No Android gameplay or shipping SFX are changed.
"""
from __future__ import annotations

import argparse
import json
import math
import wave
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.signal import fftconvolve, resample_poly

from analyze_and_render import SAMPLE_RATE, active_excerpt, decode_mp3, protect, save_wav
from render_physics_prototype import load_trace, render_one, loop_material_excerpt

SEPARATOR_SECONDS = .65


def smooth(x: np.ndarray, seconds: float) -> np.ndarray:
    return gaussian_filter1d(np.asarray(x, dtype=np.float64),
                             sigma=max(1.0, seconds * SAMPLE_RATE),
                             mode="constant", cval=0.0)


def resample_controls(trace: dict) -> tuple[np.ndarray, dict]:
    duration = float(trace["time"][-1]) + .45
    samples = int(math.ceil(duration*SAMPLE_RATE))
    timeline = np.arange(samples, dtype=float)/SAMPLE_RATE
    values = {}
    for key in ("contact", "normal_speed", "tangent_speed", "compression", "radius", "merge"):
        values[key] = np.interp(timeline, trace["time"], trace[key],
                                left=trace[key][0], right=0.0)
    values["contact"] = np.clip(values["contact"], 0, 1)
    values["normal_speed"] = np.maximum(0, values["normal_speed"])
    values["compression"] = np.clip(values["compression"],0,1)
    return timeline, values


def bloop_resonance(source: np.ndarray, radius: float,
                    length_ms: float = 112.) -> np.ndarray:
    """Short, tapered ORIGINAL material resonance as contact impulse response.

    Unlike C1, this is neither a fixed 185Hz/260Hz/325Hz oscillator nor a
    full merger playback at every contact. Its loudness comes from the
    physics-derived excitation signal, not from an event's fixed gain.
    """
    n = min(len(source), max(640, int(length_ms * SAMPLE_RATE / 1000)))
    clip = np.copy(source[:n])
    # 3ms entry ramp, 22ms release window: respect natural Bloop transient.
    attack = min(n//6, int(.003*SAMPLE_RATE))
    release = min(n//3,int(.022*SAMPLE_RATE))
    if attack: clip[:attack] *= np.linspace(0,1,attack)
    if release: clip[-release:] *= np.linspace(1,0,release)
    clip -= np.mean(clip)
    rate = float(np.clip((29.0/max(12.,radius))**.33,.77,1.3))
    ratio = Fraction(1.0/rate).limit_denominator(150)
    return resample_poly(clip,ratio.numerator,ratio.denominator)


def contact_excitation(controls: dict) -> np.ndarray:
    """A pressure-wave excitation when surfaces genuinely begin approaching.

    No scheduled SFX on game event. Integrate only the rising contact and
    rapid compression while the relative normal velocity is high.
    """
    contact = smooth(controls["contact"],.006)
    normal = smooth(controls["normal_speed"],.004)
    compression = smooth(controls["compression"],.005)
    # Quiet for slow/resting contact; smooth in speed, no arbitrary timer.
    intensity = np.clip((normal-110.)/180.,0,1)**1.2
    arriving = np.maximum(0,np.diff(contact,prepend=0))
    changing = np.maximum(0,np.diff(compression,prepend=0))
    return (arriving + .12*changing) * intensity


def elastic_impact(trace: dict, material: np.ndarray,
                   peak_gain: float=.42) -> tuple[np.ndarray,dict]:
    timeline,c=resample_controls(trace)
    excitation = contact_excitation(c)
    audible=float(np.sum(excitation))
    radius=float(trace["radius"][0])
    response=bloop_resonance(material,radius)
    output=fftconvolve(excitation,response,mode="full")[:len(timeline)]
    output=protect(output*peak_gain,peak_limit=.70)
    return output, {"driver_integral":round(audible,4),
                    "rms":round(float(np.sqrt(np.mean(output**2))),5),
                    "peak":round(float(np.max(np.abs(output))),4)}


def deformation_driver(controls: dict) -> np.ndarray:
    """Deformation *velocity*, not absolute deformation or constant pressure.

    A squashed but stationary bubble should NOT keep singing.
    """
    deformed=smooth(controls["compression"],.009)
    changing=np.abs(np.diff(deformed,prepend=0))
    contact=smooth(controls["contact"],.009)
    return changing * np.clip(contact,0,1)


def wet_flex(trace: dict, material: np.ndarray) -> tuple[np.ndarray,dict]:
    """Compression candidate 1: many tiny physical changes excite Bloop 3."""
    t,c=resample_controls(trace)
    driver=deformation_driver(c)
    radius=float(trace["radius"][0])
    impulse=bloop_resonance(material,radius,length_ms=126)
    response=fftconvolve(driver,impulse,mode="full")[:len(t)]
    # Responds to sustained *motion*, releases as deformation stops.
    response=protect(response*.48,peak_limit=.70)
    return response, {"deformation_motion":round(float(np.sum(driver)),4),
                      "peak":round(float(np.max(np.abs(response))),4),
                      "rms":round(float(np.sqrt(np.mean(response**2))),5)}


def phase_coherent_loop(source: np.ndarray, start=.035, length=.145,
                        overlap=.026) -> np.ndarray:
    """Source-phase-continuous cyclic reading with a long edge crossfade."""
    a=min(len(source)-500,int(start*SAMPLE_RATE))
    b=min(len(source),a+int(length*SAMPLE_RATE))
    segment=np.copy(source[a:b])
    if len(segment)<1000: raise ValueError("No sustained source for compression")
    overlap_n=min(len(segment)//4,int(overlap*SAMPLE_RATE))
    period=segment[:-overlap_n].copy()
    ramp=(np.arange(overlap_n)+.5)/overlap_n
    period[:overlap_n]=(1-ramp)*segment[-overlap_n:]+ramp*segment[:overlap_n]
    period-=np.mean(period)
    return period


def wet_texture(trace: dict, source: np.ndarray) -> tuple[np.ndarray,dict]:
    """Compression candidate 2: quiet continuous Bloop-body flex texture.

    Its gain follows only change of shape (abs strain rate). No sine drone,
    and no persistent noise when a contact is held at constant pressure.
    """
    t,c=resample_controls(trace)
    driver=deformation_driver(c)
    deformation_rate = driver*SAMPLE_RATE
    controlled=np.clip(deformation_rate/1.35,0,1)
    envelope=smooth(controlled,.022)
    loop=phase_coherent_loop(source)
    radius=np.maximum(12,c["radius"])
    ratio=np.clip((29/radius)**.23,.72,1.3)
    phase=np.cumsum(ratio)
    pointer=np.mod(phase,len(loop))
    i=np.floor(pointer).astype(int)
    fraction=pointer-i
    texture=loop[i]*(1-fraction)+loop[(i+1)%len(loop)]*fraction
    response=protect(texture*envelope*.28,peak_limit=.70)
    return response,{"deformation_motion":round(float(np.sum(driver)),4),
                     "peak":round(float(np.max(np.abs(response))),4),
                     "rms":round(float(np.sqrt(np.mean(response**2))),5)}


def join(parts: list[np.ndarray]) -> np.ndarray:
    gap=np.zeros(round(SEPARATOR_SECONDS*SAMPLE_RATE))
    return np.concatenate([piece for part in parts for piece in (part,gap)])


def verify_near_silence_when_still() -> None:
    """Regression: a static deformed contact cannot create a tonal drone."""
    frames=80
    t=np.arange(frames)/60
    trace={"time":t,"contact":np.ones(frames),
           "compression":np.ones(frames)*.23,
           "normal_speed":np.zeros(frames),
           "tangent_speed":np.zeros(frames),
           "radius":np.ones(frames)*28.,
           "merge":np.zeros(frames)}
    _,c=resample_controls(trace)
    driver=deformation_driver(c)
    # Discard first 200ms (initialisation) and final 200ms (release).
    s=round(.25*SAMPLE_RATE)
    e=round((t[-1]-.2)*SAMPLE_RATE)
    assert np.max(np.abs(driver[s:e])) < 1e-8, "Static pressure emitted stimulation"
    assert np.sum(contact_excitation(c))==0, "Stationary contact excited impact"


def run(csv_path:Path, sources:Path, old_dir:Path,output:Path) -> None:
    verify_near_silence_when_still()
    trace=load_trace(csv_path)
    output.mkdir(parents=True,exist_ok=True)
    bl1,_=active_excerpt(decode_mp3(sources/"bloop_1.mp3"))
    bl3,_=active_excerpt(decode_mp3(sources/"bloop_3.mp3"))
    # Old audio remains available *only as an AB reference*.
    old_audio={}
    for name in ("impact","compression","sliding","fusion"):
        p=old_dir/(name+"_physics.wav")
        if not p.exists():raise FileNotFoundError(f"Missing baseline: {p}")
        with wave.open(str(p)) as f:
            assert f.getframerate()==SAMPLE_RATE and f.getnchannels()==1
            old_audio[name]=np.frombuffer(f.readframes(f.getnframes()),
                                           dtype="<i2").astype(float)/32768.
    impact_1,m_i1=elastic_impact(trace["impact"],bl1)
    impact_2,m_i2=elastic_impact(trace["impact"],bl3,peak_gain=.37)
    compression_1,m_c1=wet_flex(trace["compression"],bl3)
    compression_2,m_c2=wet_texture(trace["compression"],bl3)

    variants={"impact_A_bloop1":impact_1,
              "impact_B_bloop3":impact_2,
              "compression_A_flex":compression_1,
              "compression_B_texture":compression_2}
    for name,wav in variants.items():
        if not np.all(np.isfinite(wav)):raise AssertionError("Nonfinite audio: "+name)
        if np.max(np.abs(wav))>.72:raise AssertionError("Too loud: "+name)
        save_wav(output/(name+".wav"),wav)

    save_wav(output/"listen_impact_old_newA_newB.wav",
             join([old_audio["impact"],impact_1,impact_2]))
    save_wav(output/"listen_compression_old_newA_newB.wav",
             join([old_audio["compression"],compression_1,compression_2]))
    save_wav(output/"listen_all_new_impact_compression_sliding_fusion.wav",
             join([impact_1,compression_1,old_audio["sliding"],old_audio["fusion"]]))
    save_wav(output/"old_sliding_reference.wav",old_audio["sliding"])
    save_wav(output/"old_fusion_reference.wav",old_audio["fusion"])

    report={"version":"C2 — rejected impact/compression redesign",
            "input":"real 24-node Java BlobWorld trace",
            "artist_feedback":{"impact":"rejected 100%",
                               "compression":"rejected 100%",
                               "sliding":"closest but not approved",
                               "fusion":"acceptable",
                               "hybrid_bloop_distortion":"slight residual artifact remains"},
            "design":{"old_issue":"fixed continuous 3-sine drone + fixed short Bloop overlay",
                      "new_impact":"physics-driven contact/compression derivative convolution with Bloop 1/3 damped material response",
                      "new_compression":"change of deformation, not amount of deformation, excites Bloop 3 material; two candidate renderers",
                      "old_sliding_and_fusion":"retained as exact listening anchors"},
            "metrics":{"impact_A":m_i1,"impact_B":m_i2,
                       "compression_A":m_c1,"compression_B":m_c2},
            "test":"Static contact with constant compression and zero velocity is silent after initialisation"}
    (output/"C2_METRICS_AND_FEEDBACK.json").write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
    print(json.dumps(report["metrics"],indent=2),flush=True)
    print("SUCCESS: redesigned impact/compression with Bloop 1 and 3; old sliding/fusion retained",flush=True)


if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--traces",type=Path,default=Path("app/build/audio_lab/two_blob_physics.csv"))
    p.add_argument("--sources",type=Path,default=Path("app/src/main/res/raw"))
    p.add_argument("--old",type=Path,default=Path("build/audio_lab/two_blob"))
    p.add_argument("--output",type=Path,default=Path("build/audio_lab/two_blob_rework"))
    args=p.parse_args()
    run(args.traces,args.sources,args.old,args.output)
