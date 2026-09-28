#!/usr/bin/env python3
"""Builds the app icons for iOS and Android from icons/source/KeepSpace_icon_source_1254.png.

Why this exists: the source artwork is a rounded square on an opaque BLACK background. Exporting it as-is
leaves black corners and a thin black frame, which show up under iOS's own corner mask and behind Android
launcher shapes. This script fills the black background with the artwork's own colours (so it is a
full-bleed square for iOS / Google Play) and gives the Android launcher icons real transparency.

    python3 icons/build_icons.py

Needs Pillow only. Writes into the app projects:
  ios/SmartStorage/Resources/Assets.xcassets/AppIcon.appiconset   (sizes + Contents.json from icons/ios)
  android/app/src/main/res/mipmap-*/ic_launcher{,_round}.png
  icons/build/ic_launcher-playstore.png                            (upload to Google Play Console)
"""
import json
import re
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "icons/source/KeepSpace_icon_source_1254.png"
IOS_SPEC = ROOT / "icons/ios/AppIcon.appiconset/Contents.json"
IOS_OUT = ROOT / "ios/SmartStorage/Resources/Assets.xcassets/AppIcon.appiconset"
ANDROID_RES = ROOT / "android/app/src/main/res"
PLAY_OUT = ROOT / "icons/build"

# Legacy launcher icon sizes in px per density (48 dp).
ANDROID_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
PLAY_STORE = 512


def flood_background(art_mask: Image.Image) -> Image.Image:
    """The black background connected to the image border (255 there), leaving dark artwork alone."""
    marked = art_mask.copy()
    w, h = marked.size
    for seed in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        if marked.getpixel(seed) == 0:
            ImageDraw.floodfill(marked, seed, 128)
    return marked.point(lambda v: 255 if v == 128 else 0)


def extend_colours(image: Image.Image, unknown: Image.Image) -> Image.Image:
    """Fills the pixels marked 255 in `unknown` with colours pushed outward from the rest of the image."""
    known = ImageChops.invert(unknown)
    premult = ImageChops.multiply(image, Image.merge("RGB", (known, known, known)))
    filled = image.copy()
    fp = filled.load()
    w, h = image.size
    flags = unknown.tobytes()
    remaining = [(i % w, i // w) for i, v in enumerate(flags) if v]
    # Widen the blur until every background pixel has a colour.
    for radius in (3, 6, 12, 24, 48, 96, 192):
        if not remaining:
            break
        blurred = premult.filter(ImageFilter.GaussianBlur(radius))
        weight = known.filter(ImageFilter.GaussianBlur(radius))
        bp, wp = blurred.load(), weight.load()
        kp, pp = known.load(), premult.load()
        still, coloured = [], []
        for x, y in remaining:
            wt = wp[x, y]
            if wt >= 6:  # enough known neighbours (out of 255)
                pr, pg, pb = bp[x, y]
                fp[x, y] = (min(255, pr * 255 // wt), min(255, pg * 255 // wt), min(255, pb * 255 // wt))
                coloured.append((x, y))
            else:
                still.append((x, y))
        # Newly coloured pixels count as known for the next, wider pass.
        for x, y in coloured:
            kp[x, y] = 255
            pp[x, y] = fp[x, y]
        remaining = still
    return filled


def load_full_bleed():
    """Returns (full-bleed RGB artwork, shape mask 0/255 of the real rounded-square artwork)."""
    src = Image.open(SOURCE).convert("RGB")
    r, g, b = src.split()
    # "Artwork" = anything that isn't near-black in at least one channel.
    art = ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda v: 255 if v > 40 else 0)
    background = flood_background(art)
    # Also drop the anti-aliased dark fringe along the edge (a few px).
    unknown = background.filter(ImageFilter.MaxFilter(7))
    return extend_colours(src, unknown), ImageChops.invert(unknown)


def resized(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.LANCZOS)


def shape_bbox(shape: Image.Image):
    return shape.getbbox()


def build_ios(full: Image.Image) -> list[str]:
    spec = json.loads(IOS_SPEC.read_text())
    if IOS_OUT.exists():
        shutil.rmtree(IOS_OUT)
    IOS_OUT.mkdir(parents=True)
    written = []
    for entry in spec["images"]:
        name = entry["filename"]
        base = float(entry["size"].split("x")[0])
        scale = int(entry["scale"].rstrip("x"))
        px = round(base * scale)
        # App icons must be opaque (no alpha), and iOS rounds the corners itself.
        resized(full, px).convert("RGB").save(IOS_OUT / name, optimize=True)
        written.append(f"{name} {px}px")
    shutil.copy(IOS_SPEC, IOS_OUT / "Contents.json")
    return written


def build_android(full: Image.Image, shape: Image.Image) -> list[str]:
    box = shape_bbox(shape)
    art = full.crop(box)
    alpha = shape.crop(box).filter(ImageFilter.GaussianBlur(1.2))
    square = art.convert("RGBA")
    square.putalpha(alpha)
    side = art.size[0]
    written = []
    for density, px in ANDROID_SIZES.items():
        folder = ANDROID_RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        # Standard icon: the artwork's own rounded-square shape, transparent outside it.
        resized(square, px).save(folder / "ic_launcher.png", optimize=True)
        # Round icon: the artwork cropped to a circle (nothing important lives in the corners).
        ss = 4
        circle = Image.new("L", (px * ss, px * ss), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, px * ss - 1, px * ss - 1), fill=255)
        round_icon = resized(art, px).convert("RGBA")
        round_icon.putalpha(circle.resize((px, px), Image.LANCZOS))
        round_icon.save(folder / "ic_launcher_round.png", optimize=True)
        written.append(f"mipmap-{density} {px}px")
    PLAY_OUT.mkdir(parents=True, exist_ok=True)
    # Google Play masks the store icon itself: a full-bleed square with no transparency.
    resized(full, PLAY_STORE).convert("RGB").save(PLAY_OUT / "ic_launcher-playstore.png", optimize=True)
    written.append(f"play store {PLAY_STORE}px")
    return written


# Adaptive icon layers: a 108 dp canvas whose safe zone is the central 66 dp circle. The artwork is placed at
# 72 dp (everything important sits well inside its own corners), and the background is the artwork's edge
# colours extended outward, so any launcher shape (circle, squircle, …) fills without a visible seam.
ADAPTIVE_DP = 108
ADAPTIVE_ART_DP = 72
ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by icons/build_icons.py -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def build_adaptive(full: Image.Image, shape: Image.Image) -> list[str]:
    work = 540  # px for the 108 dp canvas (5 px/dp); every density is scaled down from here
    art_px = work * ADAPTIVE_ART_DP // ADAPTIVE_DP
    box = shape.getbbox()
    art = full.crop(box).resize((art_px, art_px), Image.LANCZOS)
    art_shape = shape.crop(box).resize((art_px, art_px), Image.LANCZOS)
    offset = (work - art_px) // 2
    canvas = Image.new("RGB", (work, work), (0, 0, 0))
    canvas.paste(art, (offset, offset))
    known = Image.new("L", (work, work), 0)
    known.paste(art_shape.point(lambda v: 255 if v > 127 else 0), (offset, offset))
    background = extend_colours(canvas, ImageChops.invert(known))
    foreground = Image.new("RGBA", (work, work), (0, 0, 0, 0))
    art_rgba = art.convert("RGBA")
    art_rgba.putalpha(art_shape.filter(ImageFilter.GaussianBlur(0.8)))
    foreground.paste(art_rgba, (offset, offset))
    written = []
    for density, px in ADAPTIVE_SIZES.items():
        folder = ANDROID_RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        background.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_background.png", optimize=True)
        foreground.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_foreground.png", optimize=True)
        written.append(f"mipmap-{density} {px}px")
    xml_dir = ANDROID_RES / "mipmap-anydpi-v26"
    xml_dir.mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (xml_dir / name).write_text(ADAPTIVE_XML)
    return written


def main():
    full, shape = load_full_bleed()
    print("iOS:", ", ".join(build_ios(full)))
    print("Android:", ", ".join(build_android(full, shape)))
    print("Android adaptive:", ", ".join(build_adaptive(full, shape)))


if __name__ == "__main__":
    main()
