#!/usr/bin/env python3
"""Draws the map's sprite sheets: the Trail symbols, the Bike route plates and chevrons and the POI icons, light
and dark, at 1× and 2×.

    python3 scripts/map/sprites.py

Run after gen_style.py, whose palette this reads. Needs Pillow.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gen_style import ASSETS, BIKE_KINDS, BIKE_ROUTE, CHEVRONS, POIS, ROOT, SHAPES, TRAIL, palette  # noqa: E402

SUPERSAMPLE = 4
SYMBOL = (16, 12)
POI = (17, 17)
PLATE = (20, 12)
CHEVRON = (8, 8)
BICYCLE_INK = "#000000"


def symbol(shape: str, colour: str, c: dict[str, str]):
    def draw(d: ImageDraw.ImageDraw, w: float, h: float) -> None:
        edge = max(1.0, w / 16)
        d.rectangle((0, 0, w - 1, h - 1), fill=c["symbol_white"], outline=c["symbol_edge"], width=round(edge))
        inner = (edge, edge, w - 1 - edge, h - 1 - edge)
        x0, y0, x1, y1 = inner
        cx, cy = w / 2, h / 2
        if shape == "bar":
            d.rectangle((x0, h / 3, x1, h * 2 / 3), fill=colour)
        elif shape == "dot":
            r = h * 0.28
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour)
        elif shape == "cross":
            arm = h * 0.11
            d.rectangle((cx - arm, y0 + h * 0.12, cx + arm, y1 - h * 0.12), fill=colour)
            d.rectangle((cx - h * 0.33, cy - arm, cx + h * 0.33, cy + arm), fill=colour)
        elif shape == "triangle":
            d.polygon(((cx, y0 + h * 0.12), (cx + h * 0.36, y1 - h * 0.12), (cx - h * 0.36, y1 - h * 0.12)), fill=colour)
        elif shape == "corner":
            d.polygon(((x0, y0), (x1, y0), (x0, y1)), fill=colour)

    return SYMBOL, draw


def plate(kind: str, colour: str, c: dict[str, str]):
    """After the Polish R-1 sign (docs/phase-7a.md § 3): Touring thin wheels and a drop bar; MTB heavy wheels, a flat
    bar and rough ground under them."""
    mtb = kind == "mtb"

    def draw(d: ImageDraw.ImageDraw, w: float, h: float) -> None:
        unit = w / PLATE[0]
        edge = max(1.0, unit)
        d.rectangle((0, 0, w - 1, h - 1), fill=c["symbol_white"], outline=c["symbol_edge"], width=round(edge))
        d.rectangle((edge, h - 3.2 * unit, w - 1 - edge, h - 1 - edge), fill=colour)

        def at(x: float, y: float) -> tuple[float, float]:
            return x * unit, y * unit

        def line(*points: tuple[float, float], width: float = 0.9) -> None:
            d.line([at(*point) for point in points], fill=BICYCLE_INK, width=round(width * unit), joint="curve")

        radius = 2.3
        back, front, axle = (6.2, 6.4), (13.8, 6.4), 6.4
        tyre = 1.3 if mtb else 0.7
        for x, y in (back, front):
            d.ellipse((*at(x - radius, y - radius), *at(x + radius, y + radius)), outline=BICYCLE_INK, width=round(tyre * unit))
        seat, head, crank = (8.0, 3.4), (12.4, 3.4), (9.6, axle)
        line(back, crank, head, front)
        line(back, seat, crank)
        line(seat, head)
        line((7.2, 2.8), (8.8, 2.8))
        if mtb:
            line((11.2, 2.4), (13.6, 2.4), width=1.1)
            line((12.4, 3.4), (12.4, 2.4))
            ground = [(1.6 + 1.7 * step, 9.6 if step % 2 else 8.9) for step in range(11)]
            line(*ground, width=0.8)
        else:
            line((12.4, 3.4), (13.6, 2.6), (14.4, 3.2), (13.6, 3.9), width=0.7)

    return PLATE, draw


def chevron(colour: str):
    def draw(d: ImageDraw.ImageDraw, w: float, h: float) -> None:
        d.line(((w * 0.25, h * 0.12), (w * 0.72, h * 0.5), (w * 0.25, h * 0.88)), fill=colour,
               width=round(w * 0.2), joint="curve")

    return CHEVRON, draw


def poi(kind: str, c: dict[str, str]):
    ink = {"hut": c["hut_ink"], "shelter": c["hut_ink"], "spring": c["spring_ink"]}.get(kind, c["ink"])

    def draw(d: ImageDraw.ImageDraw, w: float, h: float) -> None:
        edge = max(1.0, w / 17)
        d.ellipse((0, 0, w - 1, h - 1), fill=c["badge"], outline=c["badge_edge"], width=round(edge))

        def at(*points: tuple[float, float]) -> list[tuple[float, float]]:
            return [(x * w, y * h) for x, y in points]

        if kind == "hut":
            d.polygon(at((0.5, 0.2), (0.78, 0.45), (0.72, 0.45), (0.72, 0.76), (0.28, 0.76), (0.28, 0.45), (0.22, 0.45)),
                      fill=ink)
        elif kind == "shelter":
            d.line(at((0.2, 0.62), (0.5, 0.28), (0.8, 0.62)), fill=ink, width=round(w * 0.1), joint="curve")
            d.line(at((0.3, 0.55), (0.3, 0.76)), fill=ink, width=round(w * 0.07))
            d.line(at((0.7, 0.55), (0.7, 0.76)), fill=ink, width=round(w * 0.07))
        elif kind == "peak":
            d.polygon(at((0.5, 0.24), (0.8, 0.74), (0.2, 0.74)), fill=ink)
        elif kind == "pass":
            d.polygon(at((0.18, 0.72), (0.34, 0.3), (0.5, 0.54), (0.66, 0.3), (0.82, 0.72)), fill=ink)
        elif kind == "spring":
            d.polygon(at((0.5, 0.2), (0.7, 0.55), (0.3, 0.55)), fill=ink)
            d.ellipse(at((0.3, 0.4), (0.7, 0.8)), fill=ink)
        elif kind == "viewpoint":
            d.pieslice(at((0.18, 0.3), (0.82, 0.94)), 210, 330, fill=ink)
        elif kind == "parking":
            d.rectangle(at((0.34, 0.24), (0.46, 0.78)), fill=ink)
            d.ellipse(at((0.3, 0.24), (0.72, 0.56)), fill=ink)
            d.ellipse(at((0.44, 0.34), (0.6, 0.46)), fill=c["badge"])
        elif kind == "stop":
            d.rectangle(at((0.28, 0.26), (0.72, 0.7)), fill=ink)
            d.rectangle(at((0.34, 0.32), (0.66, 0.46)), fill=c["badge"])
            d.rectangle(at((0.3, 0.7), (0.4, 0.78)), fill=ink)
            d.rectangle(at((0.6, 0.7), (0.7, 0.78)), fill=ink)
        elif kind == "station":
            for y in (0.36, 0.64):
                d.rectangle(at((0.2, y - 0.04), (0.8, y + 0.04)), fill=ink)
            for x in (0.3, 0.5, 0.7):
                d.rectangle(at((x - 0.04, 0.26), (x + 0.04, 0.74)), fill=ink)

    return POI, draw


def icons(c: dict[str, str]) -> dict[str, tuple]:
    result = {f"{colour}_{shape}": symbol(shape, value, c) for colour, value in TRAIL.items() for shape in SHAPES}
    result["other_bar"] = symbol("bar", c["trail_other"], c)
    result |= {f"bike_route_{kind}_{colour}": plate(kind, value, c) for kind in BIKE_KINDS for colour, value in BIKE_ROUTE.items()}
    result |= {f"chevron_{tone_name}": chevron(c[tone_name]) for tone_name in CHEVRONS}
    result |= {f"poi_{kind}": poi(kind, c) for kind in POIS}
    return dict(sorted(result.items()))


def render(size: tuple[int, int], draw, ratio: int) -> Image.Image:
    w, h = size[0] * ratio, size[1] * ratio
    big = Image.new("RGBA", (w * SUPERSAMPLE, h * SUPERSAMPLE), (0, 0, 0, 0))
    draw(ImageDraw.Draw(big), w * SUPERSAMPLE, h * SUPERSAMPLE)
    return big.resize((w, h), Image.Resampling.LANCZOS)


def sheet(name: str, c: dict[str, str], ratio: int) -> None:
    images = {key: render(size, draw, ratio) for key, (size, draw) in icons(c).items()}
    width = 256 * ratio
    index, x, y, row = {}, 0, 0, 0
    for key, image in images.items():
        if x + image.width > width:
            x, y, row = 0, y + row, 0
        index[key] = {"x": x, "y": y, "width": image.width, "height": image.height, "pixelRatio": ratio}
        x, row = x + image.width, max(row, image.height)
    atlas = Image.new("RGBA", (width, y + row), (0, 0, 0, 0))
    for key, image in images.items():
        atlas.paste(image, (index[key]["x"], index[key]["y"]))
    suffix = "" if ratio == 1 else f"@{ratio}x"
    atlas.save(ASSETS / f"{name}{suffix}.png")
    (ASSETS / f"{name}{suffix}.json").write_text(json.dumps(index, indent=1) + "\n")
    print((ASSETS / f"{name}{suffix}.png").relative_to(ROOT))


def main() -> None:
    for mode, dark in (("light", False), ("dark", True)):
        for ratio in (1, 2):
            sheet(f"sprite-{mode}", palette(dark), ratio)


if __name__ == "__main__":
    main()
