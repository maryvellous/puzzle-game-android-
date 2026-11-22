#!/usr/bin/env python3
"""Faithful 24-node soft-body visualization synchronized to offline V2 audio.

Each frame comes directly from BlobWorld Java simulation snapshots written
by ContactProbeTraceTest. This is NOT the game's actual Android renderer,
vessel geometry, or a substitute for on-device latency testing.
"""
from __future__ import annotations

import argparse
import csv
import math
import shutil
import subprocess
import wave
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FPS = 30
WIDTH, HEIGHT = 540, 960
PHYSICS_SECONDS = 96 / 60
SCENARIO_SECONDS = 2.3  # Each audio clip is 2.0 s + 300 ms gap.
THEMES = (
    "#DAF301", "#E9AE08", "#EB8403", "#F0420D", "#D23876",
    "#B435A8", "#9937C8", "#5A29DA", "#2D2383",
)
SCENARIOS = ("impact", "slide", "pressure", "release", "fusion")
TITLES = {
    "impact": ("URTO", "Attacco derivato da un Bloop"),
    "slide": ("SFREGAMENTO", "Coda modulata dal contatto"),
    "pressure": ("COMPRESSIONE", "Risposta alla deformazione"),
    "release": ("SEPARAZIONE", "Rilascio della materia"),
    "fusion": ("FUSIONE", "Bloop originale completo"),
    "stress_real": ("12 BLOB REALI", "Contatti simultanei + fusioni"),
}


def load_mesh(path: Path) -> dict[str, dict[int, dict]]:
    """scenario -> frame_index -> {time, blobs: {id: {tier, nodes}}}"""
    frames: dict[str, dict[int, dict]] = defaultdict(dict)
    with path.open("r", encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            scenario = row["scenario"]
            frame_index = int(row["frame"])
            blob_id = int(row["blob_id"])
            node = int(row["node"])
            if not 0 <= node < 24 or blob_id <= 0:
                raise ValueError("Invalid mesh node or blob identity")
            frame = frames[scenario].setdefault(
                frame_index, {"time": float(row["time"]), "blobs": {}})
            if abs(frame["time"] - float(row["time"])) > 1e-6:
                raise ValueError("Inconsistent simulation timestamp")
            blob = frame["blobs"].setdefault(blob_id, {
                "tier": int(row["tier"]), "nodes": [None] * 24})
            if blob["tier"] != int(row["tier"]) or blob["nodes"][node] is not None:
                raise ValueError("Duplicate node or mismatched tier")
            blob["nodes"][node] = (float(row["x"]), float(row["y"]))
    for scenario, sequence in frames.items():
        if set(sequence) != set(range(97)):
            raise ValueError(f"Mesh frames missing in {scenario}: {len(sequence)} of 97")
        if abs(sequence[0]["time"]) > 1e-6 or \
           abs(sequence[96]["time"] - PHYSICS_SECONDS) > .0002:
            raise ValueError(f"Unexpected physics time axis in {scenario}")
        for frame in sequence.values():
            for blob in frame["blobs"].values():
                if any(node is None for node in blob["nodes"]):
                    raise ValueError("Incomplete 24-node physical membrane")
    return dict(frames)


def load_contact_marks(path: Path) -> dict[str, list[dict]]:
    marks = defaultdict(list)
    with path.open("r", encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            if row["type"] == "contact":
                marks[row["scenario"]].append({
                    "time": float(row["time"]),
                    "x": float(row["x"]), "y": float(row["y"]),
                    "normal": float(row["normal_speed"]),
                    "slide": float(row["tangent_speed"]),
                })
    return marks


def font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    paths = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    )
    for candidate in paths:
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


TITLE = font(28, True)
BODY = font(16)
SMALL = font(13)
TIMER = font(18, True)
ANNOTATION = font(14)
BG_TOP = (50, 46, 50)
BG_BOTTOM = (61, 61, 61)


def canvas_background() -> Image.Image:
    background = Image.new("RGB", (WIDTH, HEIGHT))
    painter = ImageDraw.Draw(background)
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        rgb = tuple(round(a * (1 - t) + b * t)
                    for a, b in zip(BG_TOP, BG_BOTTOM))
        painter.line((0, y, WIDTH, y), fill=rgb)
    # This outline only illustrates boundaries: actual Vessel constraints may
    # be curved. It is NOT presented as the game's real vessel geometry.
    painter.rounded_rectangle((45, 150, WIDTH - 45, 846), radius=26,
                              outline=(118, 111, 121), width=2)
    painter.line((60, 860, WIDTH - 60, 860),
                 fill=(112, 106, 114), width=1)
    return background


def to_screen(x: float, y: float) -> tuple[float, float]:
    # World x in [29,391] and y in [155,850], centered in schematic glass.
    px = 45 + (x - 29) / (391 - 29) * (WIDTH - 90)
    py = 150 + (y - 155) / (850 - 155) * (846 - 150)
    return px, py


def closest_frame(sequence: dict[int, dict], time: float) -> dict:
    # Frame snapshots occur after real 60 Hz ticks. The nearest *earlier*
    # snapshot avoids showing a contact ahead of its sound.
    n = int(math.floor(min(PHYSICS_SECONDS, max(0, time)) * 60 + 1e-6))
    return sequence[min(n, 96)]


def audio_envelope(wav_path: Path, num_buckets: int = 135) -> list[float]:
    with wave.open(str(wav_path)) as source:
        if source.getsampwidth() != 2:
            raise ValueError("Expected PCM16 for preview")
        samples = np.frombuffer(source.readframes(source.getnframes()), dtype="<i2")
        channels = source.getnchannels()
    if channels > 1:
        samples = samples.reshape(-1, channels).mean(axis=1)
    bounds = np.linspace(0, len(samples), num_buckets + 1).astype(int)
    peaks = [float(np.max(np.abs(samples[bounds[i]:bounds[i + 1]])) / 32768)
             if bounds[i + 1] > bounds[i] else 0.
             for i in range(num_buckets)]
    max_level = max(peaks) or 1.
    return [p / max_level for p in peaks]


def draw_frame(background: Image.Image, scenario: str, state: dict, local_time: float,
               progress: float, marks: list[dict], waveform: list[float]) -> bytes:
    image = background.copy()
    draw = ImageDraw.Draw(image)
    title, subtitle = TITLES[scenario]
    draw.text((45, 44), title, font=TITLE, fill=(239, 230, 235))
    draw.text((45, 87), subtitle, font=BODY, fill=(191, 182, 194))
    draw.text((45, 117), "Mesh fisica reale | replay debug", font=SMALL,
              fill=(160, 153, 167))

    # Render exactly the 24 vertices from Java. No circles, interpolation or
    # synthesized deformation: even merging and contour recovery are real.
    for blob_id, body in sorted(state["blobs"].items()):
        points = [to_screen(x, y) for x, y in body["nodes"]]
        draw.polygon(points, fill=THEMES[body["tier"] % len(THEMES)])
        draw.line(points + points[:1], fill=(238, 227, 233), width=2, joint="curve")
        center_x = sum(p[0] for p in points) / 24
        center_y = sum(p[1] for p in points) / 24
        draw.text((center_x - 10, center_y - 9), f"{blob_id}",
                  font=SMALL, fill=(30, 25, 35))
    # Tiny dots identify actually detected contact points (not fabricated).
    for mark in marks:
        if abs(mark["time"] - local_time) > .018:
            continue
        x, y = to_screen(mark["x"], mark["y"])
        rad = 3.3
        draw.ellipse((x-rad, y-rad, x+rad, y+rad),
                     fill=(255, 247, 246), outline=(72, 54, 65))

    draw.text((45, 872),
              "Contorni: 24 nodi Java · Punti chiari: contatti rilevati",
              font=SMALL, fill=(196, 187, 199))
    # A subtle mini-waveform helps assess sound/motion timing.
    draw.text((45, 895), "AUDIO", font=SMALL, fill=(177, 168, 185))
    bx0, bx1 = 121, WIDTH-45
    by = 903
    for i, level in enumerate(waveform):
        px = bx0 + i / (len(waveform) - 1) * (bx1 - bx0)
        amp = max(1, 12 * level)
        fill = (218, 201, 215) if i / len(waveform) <= progress else (105, 96, 109)
        draw.line((px, by-amp, px, by+amp), fill=fill, width=2)
    draw.line((45, 943, WIDTH-45, 943), fill=(94, 85, 101), width=4)
    end_x = 45 + (WIDTH - 90) * max(0., min(1., progress))
    draw.line((45, 943, end_x, 943), fill=(235, 230, 235), width=4)
    elapsed = f"{local_time:.2f}s" if local_time <= PHYSICS_SECONDS else "coda audio"
    draw.text((WIDTH-45, 116), elapsed, anchor="ra",
              font=TIMER, fill=(231, 222, 231))
    return image.tobytes()


def ffmpeg_bin() -> str:
    cmd = shutil.which("ffmpeg")
    if cmd:
        return cmd
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def encode(path: Path, audio: Path, frames, duration_seconds: float) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    n = round(FPS * duration_seconds)
    process = subprocess.Popen(
        [ffmpeg_bin(), "-hide_banner", "-loglevel", "error", "-y",
         "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", f"{WIDTH}x{HEIGHT}",
         "-framerate", str(FPS), "-i", "pipe:0", "-i", str(audio),
         "-map", "0:v:0", "-map", "1:a:0", "-c:v", "libx264", "-preset", "veryfast",
         "-crf", "25", "-pix_fmt", "yuv420p", "-c:a", "aac",
         "-b:a", "160k", "-t", str(duration_seconds),
         "-movflags", "+faststart", str(path)],
        stdin=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    assert process.stdin is not None
    try:
        for i in range(n):
            process.stdin.write(next(frames))
        process.stdin.close()
    except Exception:
        process.kill()
        raise
    stderr = process.stderr.read().decode("utf-8", "replace")
    code = process.wait()
    if code != 0 or not path.is_file() or path.stat().st_size < 5000:
        raise RuntimeError(f"ffmpeg failed ({code}) creating {path}: {stderr[-1000:]}")


def montage_frames(mesh: dict, marks: dict, waveform: list[float]):
    base = canvas_background()
    segments = round(SCENARIO_SECONDS * FPS)
    for n in range(segments * len(SCENARIOS)):
        scenario_index = min(len(SCENARIOS)-1, n // segments)
        scenario = SCENARIOS[scenario_index]
        local = (n % segments) / FPS
        frame = closest_frame(mesh[scenario], local)
        progress = n / (segments * len(SCENARIOS))
        yield draw_frame(base, scenario, frame, local,
                         progress, marks.get(scenario, []), waveform)


def stress_frames(mesh: dict, marks: dict, waveform: list[float], total: float):
    base = canvas_background()
    count = round(total * FPS)
    for n in range(count):
        seconds = n / FPS
        frame = closest_frame(mesh["stress_real"], seconds)
        yield draw_frame(base, "stress_real", frame, seconds,
                         n / count, marks.get("stress_real", []), waveform)


def run(mesh_two: Path, mesh_stress: Path, contacts_two: Path,
        contacts_stress: Path, listen_dir: Path, output_dir: Path) -> None:
    first = load_mesh(mesh_two)
    second = load_mesh(mesh_stress)
    if not all(s in first for s in SCENARIOS):
        raise ValueError("The five scenarios must be real Java frame traces")
    if "stress_real" not in second:
        raise ValueError("Missing real stress scene frames")
    marks = load_contact_marks(contacts_two)
    stress_marks = load_contact_marks(contacts_stress)
    audio = listen_dir / "listen_real_physics_v2.wav"
    stress_audio = listen_dir / "real_12_blob_scene_stress.wav"
    out1 = output_dir / "lava_v2_five_physical_behaviors_synced.mp4"
    out2 = output_dir / "lava_v2_12_real_blobs_synced.mp4"
    montage_duration = SCENARIO_SECONDS * len(SCENARIOS)
    encode(out1, audio, iter(montage_frames(first, marks, audio_envelope(audio))),
           montage_duration)
    # The stress audition is exactly 3 seconds long.
    encode(out2, stress_audio,
           iter(stress_frames(second, stress_marks, audio_envelope(stress_audio), 3.0)),
           3.0)
    (output_dir / "READ_ME_FIRST.txt").write_text(
        "Video sincronizzati alla traccia Java delle membrane 24 nodi: "
        "le sagome e i punti di contatto sono reali; colori tema 1. "
        "Il contenitore e la UI sono schematici, NON sono il renderer Android. "
        "L'audio è ancora una prova DSP offline e la visualizzazione NON misura "
        "la latenza percepita sul dispositivo. Dopo 1.6s di simulazione, "
        "le membrane restano ferme mentre terminano le code audio.\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mesh-two", type=Path, required=True)
    parser.add_argument("--mesh-stress", type=Path, required=True)
    parser.add_argument("--contacts-two", type=Path, required=True)
    parser.add_argument("--contacts-stress", type=Path, required=True)
    parser.add_argument("--listen-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run(args.mesh_two, args.mesh_stress, args.contacts_two, args.contacts_stress,
        args.listen_dir, args.output)
