#!/usr/bin/env python3
"""Lava Blobs offline C4: brighter impact, organic drag, silent/near-silent compression.

Uses only C1/C3 Bloop-derived material and real BlobWorld physics traces.
No changes to shipping Android effects or the four original Bloop recordings.
"""
from __future__ import annotations

import argparse
import csv
import json
import wave
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.signal import butter, resample_poly, sosfiltfilt

SAMPLE_RATE = 44100
GAP_SECONDS = .5


def wav_read(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as f:
        if (f.getframerate(),f.getnchannels(),f.getsampwidth()) != (SAMPLE_RATE,1,2):
            raise ValueError(f"Expected 44.1kHz mono PCM16: {path}")
        a = np.frombuffer(f.readframes(f.getnframes()),dtype="<i2").astype(float)/32768.
    if not len(a) or not np.all(np.isfinite(a)):
        raise ValueError("Invalid sound source")
    return a


def wav_write(path:Path,data:np.ndarray) -> None:
    if not np.all(np.isfinite(data)) or np.max(np.abs(data))>=.85:
        raise ValueError("Invalid/clipped output")
    path.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(path),"wb") as f:
        f.setparams((1,2,SAMPLE_RATE,0,"NONE","not compressed"))
        f.writeframes(np.round(np.clip(data,-1,1)*32767).astype("<i2").tobytes())


def peak(x:np.ndarray)->float:return float(np.max(np.abs(x)))
def rms(x:np.ndarray)->float:return float(np.sqrt(np.mean(x*x)))


def attenuate(x:np.ndarray,limit:float)->np.ndarray:
    # Never raise the level of a weak sound to reach a target.
    return x*min(1.,limit/max(1e-12,peak(x)))


def smooth(x:np.ndarray,millis:float)->np.ndarray:
    return gaussian_filter1d(x,max(1.,millis*SAMPLE_RATE/1000.),mode="nearest")


def pitch_impact(x:np.ndarray,factor:float,n:int,cap:float)->np.ndarray:
    """Mild pitch-up of the C3 impact, preserving its actual contact onset."""
    where=np.flatnonzero(np.abs(x)>max(.00008,peak(x)*.008))
    y=np.zeros(n,dtype=float)
    if not len(where):return y
    start=max(0,int(where[0])-int(.005*SAMPLE_RATE))
    stop=min(len(x),int(where[-1])+int(.015*SAMPLE_RATE))
    fraction=Fraction(1./factor).limit_denominator(250)
    resampled=resample_poly(x[start:stop],fraction.numerator,fraction.denominator)
    fade=min(len(resampled)//5,int(.005*SAMPLE_RATE))
    if fade:
        resampled[:fade]*=np.linspace(0,1,fade)
        resampled[-fade:]*=np.linspace(1,0,fade)
    end=min(n,start+len(resampled))
    y[start:end]=resampled[:end-start]
    return attenuate(y,cap)


def read_trace(path:Path)->dict[str,dict[str,np.ndarray]]:
    rows={}
    with path.open(newline="",encoding="utf8") as f:
        for row in csv.DictReader(f):
            rows.setdefault(row["scenario"],[]).append(row)
    result={}
    for name,items in rows.items():
        result[name]={k:np.array([float(row[k]) for row in items])
                      for k in items[0] if k!="scenario"}
    if not {"impact","compression","sliding","fusion"}.issubset(result):
        raise ValueError("Missing real two-blob physics scenarios")
    return result


def control(trace:dict[str,np.ndarray],n:int,key:str)->np.ndarray:
    t=np.arange(n,dtype=float)/SAMPLE_RATE
    return np.interp(t,trace["time"],trace[key],left=trace[key][0],right=0.)


def lowpass(source:np.ndarray,hz:float)->np.ndarray:
    return sosfiltfilt(butter(2,hz,fs=SAMPLE_RATE,output="sos"),source)


def sticky_friction(carrier:np.ndarray,trace:dict[str,np.ndarray],
                    strong_stickiness:bool,cap:float)->np.ndarray:
    """No new oscillator; the phase-coherent C3 tail follows actual slip.

    Subtle sticky/grippy motion comes from changes in measured tangential
    speed, not random pitch hopping or a new sound on every collision frame.
    """
    n=len(carrier)
    tangential=np.maximum(0,control(trace,n,"tangent_speed"))
    contact=np.clip(control(trace,n,"contact"),0,1)
    velocity=smooth(tangential,20)
    slip=smooth(contact*np.clip((velocity-13)/135,0,1),24)
    speed_changes=smooth(np.abs(np.gradient(velocity,1/SAMPLE_RATE)),35)
    grip=np.clip(speed_changes/1100.,0,1)
    adhesive=1.-(.33 if strong_stickiness else .14)*grip
    bed=lowpass(carrier,850 if strong_stickiness else 1450)
    bed*=smooth(np.clip(slip,0,1)*adhesive,18)
    if strong_stickiness:
        delay=int(.014*SAMPLE_RATE)
        shifted=np.pad(bed[:-delay],(delay,0))
        bed=.88*bed+.12*shifted
    return attenuate(bed,cap)


def compression_whisper(source:np.ndarray,trace:dict[str,np.ndarray],
                        n:int,cap:float)->np.ndarray:
    """Optional barely audible, non-laser material rub while the membrane MOVES.

    Constant static deformation creates no sound, even when in contact.
    The rejected C2 resonant laser-like component is never reused.
    """
    times=trace["time"]
    deformation_rate=np.abs(np.gradient(trace["compression"],times))
    interpolated=np.interp(np.arange(n)/SAMPLE_RATE,times,deformation_rate,
                            left=deformation_rate[0],right=0.)
    contact=np.clip(control(trace,n,"contact"),0,1)
    envelope=smooth(contact*np.clip((interpolated-.06)/2.3,0,1),32)
    # C3 ghost tail is lowpassed again to remove conspicuous resonances.
    response=lowpass(source,900)*envelope
    return attenuate(response,cap)


def montage(waves:list[np.ndarray])->np.ndarray:
    gap=np.zeros(round(GAP_SECONDS*SAMPLE_RATE))
    return np.concatenate([p for x in waves for p in (x,gap)])


def run(c3_dir:Path,old_dir:Path,trace_path:Path,output:Path)->None:
    output.mkdir(parents=True,exist_ok=True)
    traces=read_trace(trace_path)
    fusion=wav_read(c3_dir/"fusion_original_prototype_reference.wav")
    ia=wav_read(c3_dir/"impact_a.wav")
    ib=wav_read(c3_dir/"impact_b.wav")
    fghost=wav_read(c3_dir/"friction_ghost.wav")
    fwhisper=wav_read(c3_dir/"friction_whisper.wav")
    n=len(fusion)
    if any(len(x)!=n for x in (ia,ib,fghost,fwhisper)):
        raise ValueError("Mismatched source WAV duration")
    reference_peak=peak(fusion)
    variants={
        "01_impact_A_pitch_plus_9pct":pitch_impact(ia,1.09,n,reference_peak*.20),
        "02_impact_B_pitch_plus_16pct":pitch_impact(ib,1.16,n,reference_peak*.20),
        "03_friction_wet_drag":sticky_friction(fwhisper,traces["sliding"],False,reference_peak*.043),
        "04_friction_adhesive_ghost":sticky_friction(fghost,traces["sliding"],True,reference_peak*.028),
        "05_compression_A_silence":np.zeros(n),
        "06_compression_B_whisper":compression_whisper(fghost,traces["compression"],n,reference_peak*.015),
    }
    for name,sound in variants.items():
        if len(sound)!=n or not np.all(np.isfinite(sound)):
            raise AssertionError("Invalid output: "+name)
        wav_write(output/(name+".wav"),sound)
    wav_write(output/"A_impact_C3_vs_C4_pitch.wav",montage([
        ia,variants["01_impact_A_pitch_plus_9pct"],
        variants["02_impact_B_pitch_plus_16pct"],fusion]))
    wav_write(output/"B_friction_C3_vs_C4_wet.wav",montage([
        fwhisper,variants["03_friction_wet_drag"],
        variants["04_friction_adhesive_ghost"],fusion]))
    wav_write(output/"C_compression_silent_vs_whisper.wav",montage([
        variants["05_compression_A_silence"],
        variants["06_compression_B_whisper"],fusion]))
    wav_write(output/"D_mix_new_impact_friction_compression_fusion.wav",montage([
        variants["01_impact_A_pitch_plus_9pct"],
        variants["03_friction_wet_drag"],
        variants["06_compression_B_whisper"],fusion]))
    wav_write(output/"E_mix_impact_friction_no_compression_fusion.wav",montage([
        variants["01_impact_A_pitch_plus_9pct"],
        variants["04_friction_adhesive_ghost"],
        variants["05_compression_A_silence"],fusion]))
    wav_write(output/"fusion_anchor_UNCHANGED.wav",fusion)
    measures={name:{"rms":round(rms(x),7),"peak":round(peak(x),6),
                    "peak_over_fusion":round(peak(x)/reference_peak,5)}
              for name,x in variants.items()}
    (output/"C4_METRICS.json").write_text(json.dumps({
        "status":"OFFLINE_ONLY_NOT_APPROVED",
        "original_fusion_unchanged":True,
        "artist_feedback":{
            "impact":"near desired behavior but too low-pitched",
            "friction":"should evoke two sticky blobs rubbing, subdued",
            "compression":"resembles distant laser shot; rejected tonal component"},
        "decision":"Compression A is intentionally SILENT; B only 1.5% of fusion peak",
        "source":"C3 WAVs and real Java BlobWorld physics CSV",
        "variations":measures,
        "caveat":"Peak ratios are not loudness; test listening on real devices",
    },ensure_ascii=False,indent=2),encoding="utf8")
    (output/"LISTENING_GUIDE.txt").write_text(
        "C4 offline sound design, no Android integration.\n"
        "A: old C3 impact / brighter +9% / brighter +16% / original fusion.\n"
        "B: old C3 friction / damp wet drag / adhesive ghost / original fusion.\n"
        "C: intentional compression SILENCE / compression whisper / original fusion.\n"
        "D: impact +9 / damp wet drag / compression whisper / fusion.\n"
        "E: impact +9 / adhesive ghost / SILENCE / fusion.\n"
        "Silent samples are intentional, not failed exports.\n",
        encoding="utf8")
    print(json.dumps(measures,ensure_ascii=False),flush=True)
    print("SUCCESS: organic C4 prototype rendered, unchanged fusion reference",flush=True)


if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--c3",type=Path,default=Path("build/audio_lab/two_blob_subtle"))
    p.add_argument("--old",type=Path,default=Path("build/audio_lab/two_blob"))
    p.add_argument("--traces",type=Path,default=Path("app/build/audio_lab/two_blob_physics.csv"))
    p.add_argument("--output",type=Path,default=Path("build/audio_lab/two_blob_c4"))
    a=p.parse_args()
    run(a.c3,a.old,a.traces,a.output)
