#!/usr/bin/env python3
"""Offline audio driven by traces recorded from the *real* Java BlobWorld.

This is a non-shipping proof of concept. The game still uses SoundPool.
No granular random source jumps or synthesis in the physics loop.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import wave
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.signal import resample_poly

from analyze_and_render import (
    SAMPLE_RATE, active_excerpt, decode_mp3, spectral_features,
    modal_synthesis, hybrid_synthesis, save_wav, protect,
)

SCENARIOS = ("impact", "compression", "sliding", "fusion")
FPS = 60


def load_trace(csv_path: Path) -> dict[str, dict[str, np.ndarray]]:
    rows = {name: [] for name in SCENARIOS}
    with csv_path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        expected = {"scenario", "time", "radius", "contact", "normal_speed",
                    "tangent_speed", "compression", "contact_nodes", "merge"}
        if set(reader.fieldnames or []) != expected:
            raise ValueError("Unexpected physics trace columns")
        for row in reader:
            if row["scenario"] not in rows:
                raise ValueError("Unknown scenario in trace")
            rows[row["scenario"]].append(row)
    result = {}
    for name, values in rows.items():
        if len(values) < 30:
            raise ValueError(f"Too few real physics frames: {name}")
        fields = {k: np.array([float(row[k]) for row in values], dtype=np.float64)
                  for k in expected if k != "scenario"}
        if not np.all(np.isfinite(fields["time"])) or np.any(np.diff(fields["time"]) <= 0):
            raise ValueError(f"Invalid timestamps: {name}")
        result[name] = fields
    return result


def soft_limit(x: np.ndarray, max_peak: float = .75) -> np.ndarray:
    # Slow material passages should not be made artificially loud.
    return protect(np.tanh(x * 1.15) / 1.15, peak_limit=max_peak)


def loop_material_excerpt(source: np.ndarray) -> np.ndarray:
    """Create a short seamless loop from the tonal body of a Bloop.

    No random re-ordering: an end-to-start crossfade preserves short-time
    continuity. The full waveform remains the source of the timbre.
    """
    start = min(len(source)-2, int(.022 * SAMPLE_RATE))
    stop = min(len(source), start + int(.090 * SAMPLE_RATE))
    segment = source[start:stop].copy()
    if len(segment) < 512:
        raise ValueError("Bloop source too short")
    overlap = min(int(.019 * SAMPLE_RATE), len(segment)//4)
    loop = segment[:-overlap].copy()
    fade = (np.arange(overlap) + .5) / overlap
    # The first overlap samples crossfade from the end to the beginning.
    loop[:overlap] = (1.0-fade)*segment[-overlap:] + fade*segment[:overlap]
    # A light smoothing of the very lowest-amplitude seam is not a lowpass
    # filter on the main material and does not remove the recognizable attack.
    loop -= np.mean(loop)
    return loop


def event_sample(reference: np.ndarray, radius: float, energy: float,
                 kind: str) -> np.ndarray:
    # Radius changes the "mass" / resonance; physical energy primarily
    # determines gain, rather than increasing gain automatically by tier.
    reference_radius = 29.0
    exponent = .35
    rate = max(.70, min(1.4, (reference_radius/max(10.,radius))**exponent))
    from fractions import Fraction
    ratio = Fraction(1.0/rate).limit_denominator(120)
    sample = resample_poly(reference, ratio.numerator, ratio.denominator)
    if kind == "impact":
        # Just a brief excitation, not the entire fusion tone.
        sample = sample[:int(.125 * SAMPLE_RATE)]
        gain = .09 * min(1., math.log1p(max(0.,energy)/17.)/math.log1p(170./17.))
    else:
        gain = .40 + .30 * min(1.,math.log1p(max(0.,energy)/40.) / math.log1p(250./40.))
    fade = min(int(.009 * SAMPLE_RATE), max(1,len(sample)//6))
    sample = np.array(sample, copy=True)
    sample[:fade] *= np.linspace(0.,1.,fade,endpoint=True)
    sample[-fade:] *= np.linspace(1.,0.,fade,endpoint=True)
    return sample*gain


def render_one(name: str, trace: dict[str,np.ndarray],
               material: np.ndarray, texture: np.ndarray) -> tuple[np.ndarray,dict]:
    duration = float(trace["time"][-1]) + .45
    count = int(math.ceil(duration*SAMPLE_RATE))
    sample_t = np.arange(count) / SAMPLE_RATE
    def curve(key: str) -> np.ndarray:
        return np.interp(sample_t,trace["time"],trace[key],left=trace[key][0],right=0.)
    contact = np.clip(curve("contact"),0,1)
    normal = np.maximum(0,curve("normal_speed"))
    tangential = np.maximum(0,curve("tangent_speed"))
    compression = np.clip(curve("compression"),0,1)
    radius = np.maximum(10,curve("radius"))
    # Smooth gains to avoid audio clicks from the 60Hz physics sampling.
    contact_smooth = gaussian_filter1d(contact, .011*SAMPLE_RATE)
    squeeze_energy = contact_smooth*np.sqrt(np.maximum(0,compression))
    scrape_energy = contact_smooth*np.log1p(tangential/20.)/np.log1p(220/20.)
    squeeze_energy = gaussian_filter1d(squeeze_energy, .010*SAMPLE_RATE)
    scrape_energy = gaussian_filter1d(scrape_energy, .016*SAMPLE_RATE)

    # Simple resonant body: frequencies measured from the original Bloop,
    # tuned by blob size and fed by the continuously measured deformation.
    frequencies = np.array([185., 260., 325.])
    pitch_scale = np.clip((29.0/radius)**.32,.65,1.55)
    phase = np.cumsum(pitch_scale)/SAMPLE_RATE
    body = (np.sin(2*np.pi*frequencies[0]*phase)
            + .28*np.sin(2*np.pi*frequencies[1]*phase+.4)
            + .12*np.sin(2*np.pi*frequencies[2]*phase+.1))
    body *= squeeze_energy*.19

    # Continuous, source-coherent contact texture; intensity comes from
    # tangential motion, not a new effect on every simulation frame.
    texture_rate = np.clip(.45+.55*tangential/150., .4, 1.4)
    offset = np.cumsum(texture_rate) / 1.0
    positions = np.mod(offset, len(texture))
    left = positions.astype(np.int64)
    frac = positions-left
    right = (left+1)%len(texture)
    scrape = ((1-frac)*texture[left]+frac*texture[right])
    scrape *= scrape_energy*.095

    mix = body+scrape
    events = []
    # A contact onset can excite the same sound material; no 350ms global
    # sound timer. Sustained pressure and slide are continuous components.
    was_contact = False
    merge_frames = np.flatnonzero(trace["merge"]>0)
    for i in range(len(trace["time"])):
        t = float(trace["time"][i])
        touching=bool(trace["contact"][i]>.5)
        onset=touching and not was_contact
        was_contact=touching
        speed=float(trace["normal_speed"][i])
        r=float(trace["radius"][i])
        if onset and speed>15 and name!="fusion":
            events.append((t,"impact",speed))
            sound=event_sample(material,r,speed,"impact")
            start=int(t*SAMPLE_RATE)
            end=min(count,start+len(sound))
            mix[start:end]+=sound[:end-start]
    if name=="fusion":
        for i in merge_frames:
            t=float(trace["time"][i])
            energy=float(trace["normal_speed"][i])
            r=float(trace["radius"][i])*1.15
            events.append((t,"merge",energy))
            sound=event_sample(material,r,max(90.,energy),"merge")
            start=int(t*SAMPLE_RATE)
            end=min(count,start+len(sound))
            mix[start:end]+=sound[:end-start]
    out=soft_limit(mix)
    metadata={
        "scenario":name,
        "trace_frames":len(trace["time"]),
        "contact_frames":int(np.sum(trace["contact"]>.5)),
        "max_normal_speed":round(float(np.max(trace["normal_speed"])),2),
        "max_tangential_speed":round(float(np.max(trace["tangent_speed"])),2),
        "max_compression":round(float(np.max(trace["compression"])),4),
        "merge_count":int(np.sum(trace["merge"])),
        "events":events,
        "render_peak":round(float(np.max(np.abs(out))),4),
        "render_rms":round(float(np.sqrt(np.mean(out*out))),5),
    }
    return out,metadata


def make_graph(traces: dict[str,dict[str,np.ndarray]],path: Path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(4,1,figsize=(12,10),sharex=True,layout="constrained")
    for ax,(name, trace) in zip(axes,traces.items()):
        t=trace["time"]
        ax.plot(t,trace["contact"]*130,label="Contact (×130)",alpha=.7)
        ax.plot(t,trace["normal_speed"],label="Approach speed",alpha=.85)
        ax.plot(t,trace["tangent_speed"],label="Sliding speed",alpha=.8)
        ax.plot(t,trace["compression"]*200,label="Compression (×200)",alpha=.8)
        for tm in t[trace["merge"]>0]:ax.axvline(tm,color="k",ls=":",lw=1.4)
        ax.set_title(name.title()+" — real 24-node soft-body simulation")
        ax.set_ylabel("a.u.")
        ax.legend(loc="upper right",ncol=4,fontsize=8)
    axes[-1].set_xlabel("Seconds")
    fig.savefig(path,dpi=135)
    plt.close(fig)


def run(csv_path: Path, mp3_dir: Path, output: Path) -> None:
    traces=load_trace(csv_path)
    output.mkdir(parents=True,exist_ok=True)
    original,_ = active_excerpt(decode_mp3(mp3_dir/"bloop_2.mp3"))
    spectral=spectral_features(original)
    modal=modal_synthesis(original,spectral["candidate_modes_hz"])
    material=hybrid_synthesis(original,modal,seed=1235,grain_share=.78,onset_end=.105)
    texture=loop_material_excerpt(original)
    results={}
    audio={}
    for name, trace in traces.items():
        sample,meta=render_one(name,trace,material,texture)
        results[name]=meta
        audio[name]=sample
        save_wav(output/(name+"_physics.wav"),sample)
        print(name, json.dumps(meta,ensure_ascii=False),flush=True)
        if name=="fusion" and meta["merge_count"]<1:
            raise AssertionError("Real physics fusion missing")
        if name!="fusion" and meta["contact_frames"]<1:
            raise AssertionError(f"No real contacts in {name}")
        if name=="sliding" and meta["max_tangential_speed"]<5:
            raise AssertionError("Sliding scenario did not slide")
        if meta["render_peak"]>.8:
            raise AssertionError("Output level too high")
    separator=np.zeros(int(.7*SAMPLE_RATE))
    montage=np.concatenate([piece for name in SCENARIOS
                             for piece in (audio[name],separator)])
    save_wav(output/"listen_real_physics_four_behaviors.wav",montage)
    make_graph(traces,output/"real_physics_trace.png")
    (output/"real_physics.csv").write_bytes(csv_path.read_bytes())
    (output/"physics_audio_metrics.json").write_text(json.dumps({
        "status":"ACTUAL_JAVA_BLOBWORLD_PHYSICS_OFFLINE_RENDER",
        "limitations":"First perceptual model; no real-time Android synthesis, no stereo, no mix with music",
        "audio_material":"Original Bloop 2; texture-preserving prototype D",
        "controls":"Real blob mesh contact, relative local normal and tangent speed, compression and merges",
        "scenarios":results,
    },indent=2,ensure_ascii=False),encoding="utf-8")
    print("SUCCESS: four real BlobWorld scenarios rendered with the Bloop-derived sound material",flush=True)


if __name__=="__main__":
    arg=argparse.ArgumentParser()
    arg.add_argument("--traces",type=Path,default=Path("app/build/audio_lab/two_blob_physics.csv"))
    arg.add_argument("--sources",type=Path,default=Path("app/src/main/res/raw"))
    arg.add_argument("--output",type=Path,default=Path("build/audio_lab/two_blob"))
    args=arg.parse_args()
    run(args.traces,args.sources,args.output)
