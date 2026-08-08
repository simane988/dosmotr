#!/usr/bin/env python3
"""Captions the raw frames and draws the feature graphic.

Called by scripts/screenshots.sh; runnable on its own when only the wording changes:

    python3 scripts/store-graphics.py --raw /tmp/frames --out store/screenshots \\
        --icon store/icon-512.png --feature store/feature-graphic.png

A bare screenshot is a picture of a screen; a store listing is read in a scrolling strip
at thumbnail size, where the caption is the only thing legible. So every frame is placed
on a plain dark card with one verbal caption above it — verbal because "Отмечай серии в
два тапа" says what you get and "Экран библиотеки" does not.

Pillow rather than ImageMagick because Pillow is already on this machine and the layout
is arithmetic either way. The palette is the app's own: the icon background and the
primary from ui/theme/Theme.kt, so the strip and the icon belong to the same thing.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# ui/theme/Theme.kt and res/values/colors.xml.
CARD_TOP = (0x33, 0x33, 0x3C)
CARD_BOTTOM = (0x14, 0x14, 0x1A)
PRIMARY = (0x4B, 0x5B, 0xD1)
INK = (0xFF, 0xFF, 0xFF)
MUTED = (0xC4, 0xC4, 0xDD)

BOLD = "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"
REGULAR = "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"

# Pixel 6, which is what the AVD is and what both stores accept without resizing.
CANVAS = (1080, 2400)
SHOT_WIDTH = 940
SHOT_BOTTOM_MARGIN = 24
CORNER = 36

FEATURE = (1024, 500)

# The order is the order the store shows them in, and the first two are the only ones
# visible in a search result — hence the library and the detail screen at the top.
CAPTIONS = {
    "01-library": "Отмечай серии в два тапа",
    "02-detail": "Сезоны и серии — всё на месте",
    "03-trending": "Начни с того, что смотрят сейчас",
    "04-stats": "Считает время и прогресс за тебя",
    "05-search": "Ищи и добавляй без регистрации",
    "06-dark": "Тёмная тема с первого запуска",
}


def gradient(size: tuple[int, int], top: tuple[int, int, int],
             bottom: tuple[int, int, int], horizontal: bool = False) -> Image.Image:
    """A one-pixel-wide ramp stretched to size — cheaper than painting every row."""
    length = size[0] if horizontal else size[1]
    ramp = Image.new("RGB", (length, 1) if horizontal else (1, length))
    pixels = ramp.load()
    for i in range(length):
        t = i / max(length - 1, 1)
        colour = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        pixels[(i, 0) if horizontal else (0, i)] = colour
    return ramp.resize(size, Image.Resampling.BILINEAR)


def rounded(image: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1),
                                           radius=radius, fill=255)
    out = image.convert("RGBA")
    out.putalpha(mask)
    return out


def wrap(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont,
         width: int) -> list[str]:
    lines: list[str] = []
    words = text.split()
    line = ""
    for word in words:
        candidate = f"{line} {word}".strip()
        if draw.textlength(candidate, font=font) <= width or not line:
            line = candidate
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def caption_frame(source: Path, caption: str, target: Path) -> None:
    shot = Image.open(source).convert("RGB")
    canvas = gradient(CANVAS, CARD_TOP, CARD_BOTTOM)
    draw = ImageDraw.Draw(canvas)

    scaled_height = round(shot.height * SHOT_WIDTH / shot.width)
    shot = shot.resize((SHOT_WIDTH, scaled_height), Image.Resampling.LANCZOS)
    top = CANVAS[1] - scaled_height - SHOT_BOTTOM_MARGIN
    card = rounded(shot, CORNER)
    canvas.paste(card, ((CANVAS[0] - SHOT_WIDTH) // 2, top), card)

    # The caption is sized to the band the screenshot leaves, not the other way round:
    # a frame from a taller device leaves less room, and text that does not fit is worse
    # than text one step smaller.
    size = 62
    while size > 34:
        font = ImageFont.truetype(BOLD, size)
        lines = wrap(draw, caption, font, CANVAS[0] - 160)
        block = len(lines) * round(size * 1.28)
        if len(lines) <= 2 and block + 80 <= top:
            break
        size -= 4
    else:
        font = ImageFont.truetype(BOLD, 34)
        lines = wrap(draw, caption, font, CANVAS[0] - 160)
        block = len(lines) * round(size * 1.28)

    y = (top - block) // 2
    for line in lines:
        draw.text((CANVAS[0] // 2, y), line, font=font, fill=INK, anchor="ma")
        y += round(size * 1.28)

    canvas.save(target, "PNG", optimize=True)


def feature_graphic(icon_path: Path, target: Path) -> None:
    """1024x500, the one asset Google Play will not let a listing go out without."""
    canvas = gradient(FEATURE, PRIMARY, CARD_BOTTOM, horizontal=True)
    draw = ImageDraw.Draw(canvas)

    icon = Image.open(icon_path).convert("RGBA").resize((240, 240), Image.Resampling.LANCZOS)
    icon = rounded(icon, 56)
    canvas.paste(icon, (88, (FEATURE[1] - 240) // 2), icon)

    x = 88 + 240 + 56
    room = FEATURE[0] - x - 56

    def line(text: str, baseline: int, path: str, size: int,
             colour: tuple[int, int, int]) -> None:
        """Shrinks rather than overflows — the right edge crops silently otherwise."""
        font = ImageFont.truetype(path, size)
        while size > 20 and draw.textlength(text, font=font) > room:
            size -= 2
            font = ImageFont.truetype(path, size)
        draw.text((x, baseline), text, font=font, fill=colour, anchor="ls")

    line("Досмотр", 168, BOLD, 88, INK)
    line("Трекер сериалов и фильмов", 232, REGULAR, 40, INK)
    line("Без регистрации и без VPN", 300, REGULAR, 30, MUTED)

    canvas.convert("RGB").save(target, "PNG", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw", type=Path, required=True, help="where the bare frames are")
    parser.add_argument("--out", type=Path, required=True, help="where the captioned ones go")
    parser.add_argument("--icon", type=Path, required=True)
    parser.add_argument("--feature", type=Path, required=True)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    missing = [name for name in CAPTIONS if not (args.raw / f"{name}.png").exists()]
    if missing:
        print(f"missing frames: {', '.join(missing)}", file=sys.stderr)
        return 1

    for name, caption in CAPTIONS.items():
        caption_frame(args.raw / f"{name}.png", caption, args.out / f"{name}.png")
        print(f"  {args.out / f'{name}.png'}")

    feature_graphic(args.icon, args.feature)
    print(f"  {args.feature}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
