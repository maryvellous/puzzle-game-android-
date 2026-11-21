#!/usr/bin/env python3
"""Offline Lava Blobs sound material analysis and first A/B/C fusion prototypes.

Not an Android/gameplay engine. Output is private evaluation artifacts: never
commit isolated derivative samples to the repository.
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import wave
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.signal import find_peaks, resample_poly, stft

SAMPLE_RATE = 44100
SOURCE_NAMES = [f"bloop_{i}" for i in range(1, 5)]


def decode_mp3(path: Path) -> np.ndarray:
    binary = shutil.which("ffmpeg")
    if not binary:
        try:
            import imageio_ffmpeg
            binary = imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError as exc:
            raise RuntimeError("ffmpeg decoder unavailable") from exc
    p = subprocess.run([
        binary, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", str(path),
        "-f", "f32le", "-acodec", "pcm_f32le", "-ac", "1", "-ar", str(SAMPLE_RATE), "pipe:1"
    ], capture_output=True, check=True)
    arr = np.frombuffer(p.stdout, dtype="<f4").astype(np.float64)
    if len(arr) < SAMPLE_RATE // 10 or not np.all(np.isfinite(arr)):
        raise ValueError(f"Invalid or empty decoded sound: {path}")
    return np.clip(arr, -1.0, 1.0)


def rms_envelope(x: np.ndarray, window_ms: int = 10) -> tuple[np.ndarray, np.ndarray]:
    hop = max(1, int(SAMPLE_RATE * window_ms / 1000))
    n = int(math.ceil(len(x) / hop))
    pad = np.pad(x, (0, n * hop - len(x)))
    levels = np.sqrt(np.mean(pad.reshape(n, hop) ** 2, axis=1) + 1e-16)
    times = (np.arange(n) + 0.5) * hop / SAMPLE_RATE
    return times, levels


def active_excerpt(x: np.ndarray) -> tuple[np.ndarray, dict]:
    times, rms = rms_envelope(x)
    threshold = max(0.00316, float(rms.max()) * 0.025)
    active = np.flatnonzero(rms >= threshold)
    if len(active) == 0:
        raise ValueError("Clip contains no audible signal")
    start = max(0, int((times[active[0]] - 0.015) * SAMPLE_RATE))
    end = min(len(x), int((times[active[-1]] + 0.025) * SAMPLE_RATE))
    data = x[start:end].copy()
    if len(data) < SAMPLE_RATE * 0.08:
        raise ValueError("Audio excerpt implausibly short")
    return data, {
        "original_decoded_seconds": round(len(x) / SAMPLE_RATE, 4),
        "active_seconds": round(len(data) / SAMPLE_RATE, 4),
        "active_start_seconds": round(start / SAMPLE_RATE, 4),
        "active_end_seconds": round(end / SAMPLE_RATE, 4),
        "silence_threshold_rms": round(threshold, 6),
    }


def normalized_stft(x: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    f, t, s = stft(x, fs=SAMPLE_RATE, nperseg=2048, noverlap=1792,
                   window="hann", boundary="zeros", padded=True)
    return f, t, s


def spectral_features(x: np.ndarray) -> dict:
    freq, times, spectrum = normalized_stft(x)
    mag = np.abs(spectrum)
    mask = (freq >= 75) & (freq <= 6000)
    active_mag = mag[mask]
    freq_part = freq[mask]
    energy = active_mag ** 2
    weights = energy.sum(axis=0) + 1e-15
    centroid = (freq_part[:, None] * energy).sum(axis=0) / weights
    db_levels = 20 * np.log10(np.maximum(1e-8, np.sqrt(weights)))
    active_frames = db_levels > (db_levels.max() - 34)
    if not active_frames.any():
        active_frames = np.ones(len(times), dtype=bool)
    mean_mag = np.mean(active_mag[:, active_frames], axis=1)
    smoothed = gaussian_filter1d(mean_mag, 1.3)
    peak_bins, _ = find_peaks(smoothed, distance=2, prominence=max(smoothed.max() * .018, 1e-8))
    candidate_bins = sorted(peak_bins, key=lambda i: smoothed[i], reverse=True)
    chosen: list[int] = []
    for idx in candidate_bins:
        hz = freq_part[idx]
        if all(abs(hz - freq_part[j]) >= max(37, hz * 0.025) for j in chosen):
            chosen.append(int(idx))
        if len(chosen) == 10:
            break
    if len(chosen) < 4:
        for idx in np.argsort(smoothed)[::-1]:
            hz = freq_part[idx]
            if all(abs(hz - freq_part[j]) >= max(37, hz * 0.025) for j in chosen):
                chosen.append(int(idx))
            if len(chosen) == 6:
                break
    modes = sorted([float(freq_part[k]) for k in chosen])
    flat = np.exp(np.mean(np.log(np.maximum(active_mag[:, active_frames], 1e-9)), axis=0)) \
         / np.maximum(np.mean(active_mag[:, active_frames], axis=0), 1e-9)
    _, rms = rms_envelope(x)
    return {
        "peak": round(float(np.max(np.abs(x))), 5),
        "rms": round(float(np.sqrt(np.mean(x*x))), 5),
        "crest_factor_db": round(float(20*np.log10(np.max(np.abs(x))/max(1e-9, np.sqrt(np.mean(x*x))))), 2),
        "median_spectral_centroid_hz": round(float(np.median(centroid[active_frames])), 1),
        "mean_spectral_flatness": round(float(np.mean(flat)), 3),
        "rms_peak_at_seconds": round(float(np.argmax(rms)*.01), 3),
        "candidate_modes_hz": [round(hz, 1) for hz in modes],
    }


def protect(x: np.ndarray, peak_limit: float = .87) -> np.ndarray:
    x = np.nan_to_num(x, copy=True)
    peak = np.max(np.abs(x)) if len(x) else 0
    if peak > peak_limit:
        x *= peak_limit / peak
    return x


def modal_synthesis(src: np.ndarray, modes_hz: list[float]) -> np.ndarray:
    """First additive, time-varying approximation from measured modal envelopes."""
    freqs, times, complex_spec = normalized_stft(src)
    mag = np.abs(complex_spec)
    dest_t = np.arange(len(src), dtype=np.float64)/SAMPLE_RATE
    result = np.zeros(len(src), dtype=np.float64)
    for mode in modes_hz:
        neighborhood = np.where((freqs >= max(60., mode-90)) &
                                (freqs <= min(7000., mode+90)))[0]
        if len(neighborhood) == 0:
            continue
        local = mag[neighborhood]
        arg = np.argmax(local, axis=0)
        local_bin = neighborhood[arg]
        nominal_pitch = freqs[local_bin]
        smooth_pitch = gaussian_filter1d(nominal_pitch, sigma=2.0, mode='nearest')
        local_power = np.max(local, axis=0)
        smooth_power = gaussian_filter1d(local_power, sigma=1.2, mode='nearest')
        amp = np.interp(dest_t, times, smooth_power, left=0, right=0)
        hz = np.interp(dest_t, times, smooth_pitch, left=mode, right=mode)
        phase = 2*np.pi*np.cumsum(hz)/SAMPLE_RATE
        result += amp * np.sin(phase)
    if np.max(np.abs(result)) < 1e-9:
        raise ValueError("Unable to identify any modes")
    _, src_env_10ms = rms_envelope(src)
    energy = gaussian_filter1d(result**2, sigma=SAMPLE_RATE*.012, mode='nearest')
    dest_env = np.sqrt(np.maximum(energy, 1e-12))
    src_t = (np.arange(len(src_env_10ms))+.5)*.01
    target = np.interp(dest_t, src_t, src_env_10ms, left=src_env_10ms[0],right=0)
    scale = np.minimum(6.0, target / np.maximum(dest_env, 1e-5))
    result *= gaussian_filter1d(scale, sigma=SAMPLE_RATE*.0015)
    fade = min(int(SAMPLE_RATE*.008), len(result)//8)
    if fade:
        result[:fade] *= np.linspace(0, 1, fade)
        result[-fade:] *= np.linspace(1,0,fade)
    return protect(result)


def granular_texture_legacy(src: np.ndarray, seed: int) -> np.ndarray:
    """Original jittered-grain baseline (retained for controlled A/B comparison)."""
    random = np.random.default_rng(seed)
    n = len(src)
    out = np.zeros(n, dtype=np.float64)
    normalizer = np.zeros(n, dtype=np.float64)
    grain_length = max(1024, int(.055*SAMPLE_RATE))
    stride = int(.022*SAMPLE_RATE)
    max_start = max(1, n-grain_length)
    for dest_start in range(0,n,stride):
        center = np.clip(dest_start+random.integers(-int(.08*SAMPLE_RATE),int(.08*SAMPLE_RATE)+1),0,max_start)
        source_start = int(center)
        remaining = min(grain_length,n-dest_start,n-source_start)
        if remaining < 8:
            continue
        win = np.hanning(grain_length)[:remaining]
        out[dest_start:dest_start+remaining] += src[source_start:source_start+remaining]*win
        normalizer[dest_start:dest_start+remaining] += win
    nonzero = normalizer > 1e-4
    out[nonzero] /= normalizer[nonzero]
    _, env = rms_envelope(src)
    t = np.arange(n)/SAMPLE_RATE
    env_t = (np.arange(len(env))+.5)*.01
    target_env = np.interp(t,env_t,env,left=env[0],right=0)
    curr_env = np.sqrt(gaussian_filter1d(out*out,sigma=SAMPLE_RATE*.01)+1e-10)
    out *= np.minimum(3,target_env/np.maximum(curr_env,1e-5))
    return protect(out)


def granular_texture(src: np.ndarray, seed: int) -> np.ndarray:
    """Phase-coherent source-aligned overlap-add. No stochastic read jumps.

    The earlier grain reshuffle selected a new arbitrary source offset on every
    ~22ms grain, discontinuously changing phase/pitch and creating audible
    crackle in the C/D mixes. Preserve source-time continuity in this reference
    reconstruction and defer pitch/time-grain movement to a separate future
    phase-locked resampler/WSOLA implementation.
    """
    _ = seed  # Interface retained for deterministic future variants.
    n = len(src)
    grain_length = max(1024, int(.070 * SAMPLE_RATE))
    hop = max(64, grain_length // 4)  # 75% overlap.
    window = np.hanning(grain_length)
    output = np.zeros(n, dtype=np.float64)
    weights = np.zeros(n, dtype=np.float64)
    for dest_start in range(-grain_length + hop, n, hop):
        left = max(0, dest_start)
        right = min(n, dest_start + grain_length)
        if right <= left:
            continue
        slice_start = left - dest_start
        slice_end = slice_start + (right - left)
        w = window[slice_start:slice_end]
        output[left:right] += src[left:right] * w
        weights[left:right] += w
    if np.any(weights < 1e-6):
        raise ValueError("Overlap-add left an uncovered audio sample")
    return output / weights


def hybrid_synthesis(src: np.ndarray, modal: np.ndarray, seed: int,
                     grain_share: float = .36, onset_end: float = .07,
                     use_legacy_grains: bool = False) -> np.ndarray:
    granular = (granular_texture_legacy(src,seed) if use_legacy_grains
                else granular_texture(src,seed))
    result = (1.-grain_share)*modal + grain_share*granular
    n = len(result)
    t = np.arange(n)/SAMPLE_RATE
    original_amount = np.clip((onset_end-t)/max(.01,onset_end-.015), 0, 1)**2
    result = original_amount*src+(1-original_amount)*result
    fade=int(.02*SAMPLE_RATE)
    if fade<n:
        result[-fade:]*=np.linspace(1,0,fade)
    return protect(result)


def level_match(x: np.ndarray, ref_rms: float) -> np.ndarray:
    rms = float(np.sqrt(np.mean(x*x)))
    if rms < 1e-7:
        return np.zeros_like(x)
    return protect(x * min(12., ref_rms / rms))


def variation(src: np.ndarray, size: str, energy: float) -> np.ndarray:
    speed = {'small':1.14,'medium':1.00,'large':.84}[size]
    from fractions import Fraction
    ratio = Fraction(1/speed).limit_denominator(300)
    altered = resample_poly(src,ratio.numerator,ratio.denominator)
    gain = .5 + .45*min(1.0, max(0,energy))
    return protect(altered*gain)


def save_wav(path: Path, samples: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(np.rint(protect(samples)*32767), -32768,32767).astype('<i2')
    with wave.open(str(path),'wb') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(pcm.tobytes())


def visual_report(data: dict[str, dict], out: Path) -> None:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(4,2,figsize=(13,12),layout='constrained')
    for row,(name, info) in enumerate(data.items()):
        x=info['decoded']
        ax=axes[row,0]
        ax.plot(np.arange(len(x))/SAMPLE_RATE,x,lw=.5)
        ax.set(title=name+' • decoded waveform',xlabel='Time (s)',ylabel='Amplitude')
        freqs,t,spec=normalized_stft(x)
        keep=freqs<6000
        pwr=20*np.log10(np.maximum(1e-7,np.abs(spec[keep])))
        ax=axes[row,1]
        ax.pcolormesh(t,freqs[keep],pwr,shading='auto',vmin=-85,vmax=-15,rasterized=True)
        ax.set(title=name+' • spectrogram',xlabel='Time (s)',ylabel='Hz')
    fig.suptitle('Lava Blobs — original Bloop materials (decoded PCM)',fontsize=15)
    fig.savefig(out/'bloop_analysis.png',dpi=130)
    plt.close(fig)


def run(input_dir: Path, output: Path) -> None:
    output.mkdir(parents=True,exist_ok=True)
    info_by_name={}
    feature_report={}
    for idx,name in enumerate(SOURCE_NAMES):
        source_path = input_dir/f'{name}.mp3'
        x=decode_mp3(source_path)
        active,meta=active_excerpt(x)
        features=spectral_features(active)
        modes=features['candidate_modes_hz']
        b=modal_synthesis(active,modes)
        c=hybrid_synthesis(active,b,seed=idx+1234)
        d=hybrid_synthesis(active,b,seed=idx+1234,grain_share=.78,onset_end=.105)
        c_old=hybrid_synthesis(active,b,seed=idx+1234,use_legacy_grains=True)
        d_old=hybrid_synthesis(active,b,seed=idx+1234,grain_share=.78,
                               onset_end=.105,use_legacy_grains=True)
        info_by_name[name]={'decoded':x, 'active':active, 'modal':b,
                            'hybrid':c, 'natural':d, 'old_c':c_old, 'old_d':d_old}
        feature_report[name]={**meta,**features, 'source_sha256':__import__('hashlib').sha256(source_path.read_bytes()).hexdigest()}
        save_wav(output/'comparisons'/f'{name}_B_modal.wav',b)
        save_wav(output/'comparisons'/f'{name}_C_hybrid.wav',c)
        save_wav(output/'comparisons'/f'{name}_D_texture_preserved.wav',d)
        save_wav(output/'legacy_comparison'/f'{name}_C_old_grain_jitter.wav',c_old)
        save_wav(output/'legacy_comparison'/f'{name}_D_old_grain_jitter.wav',d_old)
        print(f'{name}: {meta["active_seconds"]}s active, centroid {features["median_spectral_centroid_hz"]}Hz, modes={modes}',flush=True)
    master=info_by_name['bloop_2']['hybrid']
    for size in ['small','medium','large']:
        for energy_label,energy in [('soft',.2),('strong',1.)]:
            save_wav(output/'variations'/f'fusion_{size}_{energy_label}.wav',variation(master,size,energy))
    spacing=np.zeros(int(SAMPLE_RATE*.4))
    ref=info_by_name['bloop_2']
    source_rms = float(np.sqrt(np.mean(ref['active']**2)))
    montage=np.concatenate([
        level_match(ref['active'],source_rms),spacing,
        level_match(ref['modal'],source_rms),spacing,
        level_match(ref['hybrid'],source_rms),spacing,
        level_match(ref['natural'],source_rms)])
    save_wav(output/'listen_A_original_B_modal_C_hybrid_D_natural.wav',montage)
    before_after=np.concatenate([
        level_match(ref['active'],source_rms),spacing,
        level_match(ref['old_c'],source_rms),spacing,
        level_match(ref['hybrid'],source_rms),spacing,
        level_match(ref['old_d'],source_rms),spacing,
        level_match(ref['natural'],source_rms)])
    save_wav(output/'listen_A_C_old_C_clean_D_old_D_clean.wav',before_after)
    for path in output.rglob('*.wav'):
        with wave.open(str(path)) as w:
            assert w.getframerate()==SAMPLE_RATE and w.getnchannels()==1 and w.getsampwidth()==2
            pcm=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2').astype(float)/32768
        assert len(pcm)>SAMPLE_RATE*.08 and np.all(np.isfinite(pcm)) and np.max(np.abs(pcm))<.95
    visual_report(info_by_name,output)
    (output/'features.json').write_text(json.dumps({
        'status':'MEASURED_FROM_DECODED_PCM',
        'method':'ffmpeg float PCM mono 44.1kHz; STFT nfft2048 hop256; active RMS -50dBFS or -32dB relative',
        'note':'Candidate modes are broad spectral peaks, not verified physical resonances or music notes',
        'samples':feature_report,
        'synthetic_prototypes':{
            'B':'modal additive baseline',
            'C':'hybrid: original attack + 64% modal / 36% phase-coherent source-aligned grains',
            'D':'sample-faithful: original attack + 22% modal / 78% phase-coherent source-aligned grains',
            'legacy_C_D':'prior arbitrary grain jitter at every 22ms hop; suspected source of crackling',
            'montage':'A/B/C/D loudness-matched with 0.4s pauses',
            'before_after':'A / C legacy / C clean / D legacy / D clean'
        },
    },ensure_ascii=False,indent=2),encoding='utf8')
    lines=[
        '# Lava Blobs — A/B/C audio lab results', '',
        '**Offline evaluation only. No changes to the Android game or its SFX.**', '',
        '## Analysis of the four sources', '',
        '| Sample | Decoded sec | Active sec | Peak | RMS | Centroid Hz (median) | Candidate spectral peaks Hz |',
        '|---|---:|---:|---:|---:|---:|---|'
    ]
    for name,metrics in feature_report.items():
        freqs=', '.join(str(int(round(f))) for f in metrics['candidate_modes_hz'][:8])
        lines.append(f'| {name} | {metrics["original_decoded_seconds"]:.3f} | {metrics["active_seconds"]:.3f} | {metrics["peak"]:.3f} | {metrics["rms"]:.3f} | {metrics["median_spectral_centroid_hz"]:.0f} | {freqs} |')
    lines += ['', 'Values extracted from decoded mono PCM; the peak frequencies are **candidate modes**, not validated fundamentals.', '',
        '## Listening instructions', '',
        '1. Start with listen_A_original_B_modal_C_hybrid_D_natural.wav: A is original, B is fully modal, C is hybrid and D preserves more original texture. The versions are RMS-balanced before safe peak limiting; each is separated by 0.4 seconds of silence.',
        '2. Use listen_A_C_old_C_clean_D_old_D_clean.wav to hear A original, old C, clean C, old D and clean D in that order (0.4s pauses; levels balanced).',
        '3. Compare comparisons/bloop_N_B_modal.wav, bloop_N_C_hybrid.wav and bloop_N_D_texture_preserved.wav for all four references.',
        '4. Listen to variations/fusion_small_soft.wav through fusion_large_strong.wav to assess size and energy. These initially derive from clean C; this is a parameter test, not final acoustic physics.',
        '5. Compare on headphones and the actual Android phone. Specifically report if the light sizzling/frying heard in old C/D has disappeared in the clean C/D versions, and whether they still sound like the original material.',
        '', '## Decision gate', '',
        'A synthesis is **not approved** by a passing test or the measured spectra. It must sound at least as convincing as the existing fusion Bloop to the user. If not, iterate without changing GameAudio.',
        '', '## Licensing and provenance', '',
        'Input tracks are by floraphonic, from Pixabay, as credited in the main README. This ZIP is an internal evaluation artifact, not a sound library for independent redistribution. Do not publish the derivatives as stand-alone assets.',
    ]
    (output/'LISTENING_NOTES.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    print('SUCCESS: all four MP3 files analyzed; B/C/D prototypes, size/energy variations, plots and measured report generated.',flush=True)


if __name__=='__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--inputs',type=Path,default=Path('app/src/main/res/raw'))
    ap.add_argument('--output',type=Path,default=Path('build/audio_lab'))
    args=ap.parse_args()
    run(args.inputs,args.output)
