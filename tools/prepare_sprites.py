#!/usr/bin/env python3
"""
Sprite preparation tool for WaifuVerse.

Problem it solves
-----------------
The raw mouth frames (mouth_0..mouth_3) were generated as separate images and
are not perfectly aligned with each other: the whole body is shifted by a few
pixels between frames and small details differ everywhere. Swapping full
frames therefore makes the character "jitter".

What it does
------------
For every character folder in tools/raw_sprites/characters/<id>/:
  1. Takes mouth_0 as the fixed BODY image (never changes at runtime).
  2. Aligns every other frame to the body by searching the integer (dx, dy)
     shift with the smallest difference (the body dominates the image, so the
     best shift is the body alignment).
  3. Finds the mouth rectangle = largest SOLID region that still differs after
     alignment (thin edge lines caused by sub-pixel differences are eroded
     away), with padding.
  4. Cuts that rectangle out of each aligned frame, feathers its edges so it
     blends seamlessly into the body, and saves it as a small patch.
  5. Writes app/src/main/assets/characters/<id>/body.webp, mouth_0..3.webp
     and sprite.json (mouth rectangle in body pixel coordinates).

At runtime the app draws body.webp once and only swaps the small mouth patch,
so nothing except the mouth can ever move.

Usage
-----
    pip install pillow numpy
    python3 tools/prepare_sprites.py

Add a new character: create tools/raw_sprites/characters/<id>/mouth_0..3.webp
(transparent background, same size), run the tool, then add an entry to
app/src/main/java/com/vislupus/waifuverse/config/CharactersConfig.kt.
"""
import json
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw_sprites"
OUT = ROOT / "app" / "src" / "main" / "assets"

MAX_SHIFT = 14          # pixels searched in every direction when aligning
DIFF_THRESHOLD = 60     # per-pixel RGBA abs-diff sum considered "changed"
ERODE_RADIUS = 3        # thin edge lines (sub-pixel misalignment) are thinner than this
ERODE_KEEP = 0.92       # fraction of the eroding window that must be "changed"
PADDING = 14            # pixels added around the detected mouth rectangle
FEATHER = 10            # pixels of soft edge blended into the body


def load_rgba(path: Path) -> np.ndarray:
    return np.array(Image.open(path).convert("RGBA")).astype(np.int32)


def shifted(img: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """Return img translated by (dx, dy); uncovered area is transparent."""
    h, w = img.shape[:2]
    out = np.zeros_like(img)
    xs = slice(max(0, dx), min(w, w + dx))
    ys = slice(max(0, dy), min(h, h + dy))
    xs_src = slice(max(0, -dx), min(w, w - dx))
    ys_src = slice(max(0, -dy), min(h, h - dy))
    out[ys, xs] = img[ys_src, xs_src]
    return out


def sad(a: np.ndarray, b: np.ndarray) -> float:
    m = MAX_SHIFT + 1  # ignore the border which may be uncovered after a shift
    return float(np.abs(a[m:-m, m:-m] - b[m:-m, m:-m]).sum())


def best_shift(base: np.ndarray, frame: np.ndarray) -> tuple[int, int]:
    """Coarse-to-fine integer search for the shift aligning frame to base."""
    best = (0, 0)
    best_score = sad(base, frame)
    # Coarse pass on a 4x downscaled copy, then refine around the result.
    small_base = base[::4, ::4]
    small_frame = frame[::4, ::4]
    coarse = (0, 0)
    coarse_score = None
    r = MAX_SHIFT // 4 + 1
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            s = float(np.abs(small_base[3:-3, 3:-3] - shifted(small_frame, dx, dy)[3:-3, 3:-3]).sum())
            if coarse_score is None or s < coarse_score:
                coarse_score, coarse = s, (dx, dy)
    cx, cy = coarse[0] * 4, coarse[1] * 4
    for dy in range(cy - 5, cy + 6):
        for dx in range(cx - 5, cx + 6):
            if abs(dx) > MAX_SHIFT or abs(dy) > MAX_SHIFT:
                continue
            s = sad(base, shifted(frame, dx, dy))
            if s < best_score:
                best_score, best = s, (dx, dy)
    return best


def box_blur(mask: np.ndarray, radius: int) -> np.ndarray:
    """Mean filter using an integral image (fast, no scipy needed)."""
    h, w = mask.shape
    pad = np.pad(mask.astype(np.float64), ((1, 0), (1, 0)))
    integral = pad.cumsum(0).cumsum(1)
    ys = np.arange(h)
    xs = np.arange(w)
    y0 = np.clip(ys - radius, 0, h)[:, None]
    y1 = np.clip(ys + radius + 1, 0, h)[:, None]
    x0 = np.clip(xs - radius, 0, w)[None, :]
    x1 = np.clip(xs + radius + 1, 0, w)[None, :]
    total = integral[y1, x1] - integral[y0, x1] - integral[y1, x0] + integral[y0, x0]
    area = (y1 - y0) * (x1 - x0)
    return total / area


def largest_component_bbox(mask: np.ndarray):
    """Bounding box of the largest 4-connected component of a boolean mask."""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    current = 0
    best = None
    best_size = 0
    ys, xs = np.nonzero(mask)
    for sy, sx in zip(ys, xs):
        if labels[sy, sx]:
            continue
        current += 1
        stack = [(sy, sx)]
        labels[sy, sx] = current
        size = 0
        y0 = y1 = sy
        x0 = x1 = sx
        while stack:
            y, x = stack.pop()
            size += 1
            y0, y1 = min(y0, y), max(y1, y)
            x0, x1 = min(x0, x), max(x1, x)
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not labels[ny, nx]:
                    labels[ny, nx] = current
                    stack.append((ny, nx))
        if size > best_size:
            best_size, best = size, (x0, y0, x1, y1)
    return best


def feather_window(h: int, w: int, feather: int) -> np.ndarray:
    ramp_y = np.minimum(np.arange(h) + 1, np.arange(h)[::-1] + 1) / float(feather)
    ramp_x = np.minimum(np.arange(w) + 1, np.arange(w)[::-1] + 1) / float(feather)
    return np.clip(np.minimum(ramp_y[:, None], ramp_x[None, :]), 0.0, 1.0)


def save_webp(arr: np.ndarray, path: Path) -> None:
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, "WEBP", lossless=True, quality=100)


def process_character(char_dir: Path) -> None:
    cid = char_dir.name
    frames_paths = sorted(char_dir.glob("mouth_*.webp"), key=lambda p: int(p.stem.split("_")[1]))
    if len(frames_paths) < 2:
        print(f"[{cid}] skipped: needs at least mouth_0 and mouth_1")
        return
    frames = [load_rgba(p) for p in frames_paths]
    base = frames[0]
    h, w = base.shape[:2]
    for i, f in enumerate(frames):
        if f.shape != base.shape:
            sys.exit(f"[{cid}] mouth_{i} has size {f.shape[1]}x{f.shape[0]} but mouth_0 is {w}x{h}")

    aligned = [base]
    union_mask = np.zeros((h, w), dtype=bool)
    for i, f in enumerate(frames[1:], start=1):
        dx, dy = best_shift(base, f)
        a = shifted(f, dx, dy)
        aligned.append(a)
        diff = np.abs(a - base).sum(axis=2) > DIFF_THRESHOLD
        # Erosion: thin edge lines caused by tiny residual misalignment vanish,
        # only the solid mouth blob survives.
        eroded = box_blur(diff, ERODE_RADIUS) > ERODE_KEEP
        union_mask |= eroded
        print(f"[{cid}] mouth_{i}: shift=({dx:+d},{dy:+d}) changed_px={int(diff.sum())}")

    bbox = largest_component_bbox(union_mask)
    if bbox is None:
        sys.exit(f"[{cid}] could not detect a mouth region")
    x0, y0, x1, y1 = (int(v) for v in bbox)
    x0 = max(0, x0 - PADDING)
    y0 = max(0, y0 - PADDING)
    x1 = min(w - 1, x1 + PADDING)
    y1 = min(h - 1, y1 + PADDING)
    pw, ph = x1 - x0 + 1, y1 - y0 + 1
    print(f"[{cid}] mouth rect: x={x0} y={y0} w={pw} h={ph}")

    out_dir = OUT / "characters" / cid
    out_dir.mkdir(parents=True, exist_ok=True)
    save_webp(base, out_dir / "body.webp")

    base_crop = base[y0:y1 + 1, x0:x1 + 1].astype(np.float64)
    window = feather_window(ph, pw, FEATHER)[:, :, None]
    for i, a in enumerate(aligned):
        crop = a[y0:y1 + 1, x0:x1 + 1].astype(np.float64)
        patch = base_crop * (1.0 - window) + crop * window
        save_webp(patch, out_dir / f"mouth_{i}.webp")

    meta = {
        "width": w,
        "height": h,
        "frames": len(aligned),
        "mouth": {"x": x0, "y": y0, "width": pw, "height": ph},
    }
    (out_dir / "sprite.json").write_text(json.dumps(meta, indent=2) + "\n")


def copy_backgrounds() -> None:
    src = RAW / "backgrounds"
    dst = OUT / "backgrounds"
    dst.mkdir(parents=True, exist_ok=True)
    for p in sorted(src.glob("*.webp")):
        Image.open(p).convert("RGB").save(dst / p.name, "WEBP", quality=90, method=6)
        print(f"[bg] {p.name}")


def main() -> None:
    chars = RAW / "characters"
    if not chars.is_dir():
        sys.exit(f"missing {chars}")
    for d in sorted(p for p in chars.iterdir() if p.is_dir()):
        process_character(d)
    copy_backgrounds()
    print("done ->", OUT)


if __name__ == "__main__":
    main()
