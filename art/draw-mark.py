"""The mark's drawing tool: anchor points -> cubic path data, written over every path literal in
`mark.py` beside this file. Move an anchor, run this, then the two generators.
"""
import math
import re
from pathlib import Path

HERE = Path(__file__).parent


def stroke(center, widths):
    """A thick stroke along a centreline -> (left side base->tip, right side tip->base)."""
    left, right = [], []
    n = len(center)
    for i, (x, y) in enumerate(center):
        x0, y0 = center[max(i - 1, 0)]
        x1, y1 = center[min(i + 1, n - 1)]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L
        w = widths[i] / 2
        left.append((x - nx * w, y - ny * w))
        right.append((x + nx * w, y + ny * w))
    return left, right[::-1]


def horn_centre(base, shaft, tilt, r, sweep, n=12):
    """A horn's centreline: a straight shaft rising from `base`, then an arc hooking backward.

    `tilt` leans the whole horn forward in degrees; `sweep` is how far round the hook goes.
    """
    bx, by = base
    arc = math.radians(sweep) * r
    total = shaft + arc
    pts = []
    for i in range(n + 1):
        s = total * i / n
        if s <= shaft:
            x, y = 0, -s
        else:
            a = (s - shaft) / r
            x, y = -r + r * math.cos(a), -shaft - r * math.sin(a)
        t = math.radians(tilt)
        pts.append((bx + x * math.cos(t) - y * math.sin(t), by + x * math.sin(t) + y * math.cos(t)))
    return pts


def taper(n, w0, w1):
    return [w0 + (w1 - w0) * (i / n) ** 0.75 for i in range(n + 1)]


NEAR_C = horn_centre((52, -98), 37, 8, 11.5, 185)
NEAR_W = taper(12, 16, 1.2)
FAR_C = horn_centre((39, -103), 18, -12, 9.5, 190)
FAR_W = taper(12, 13, 1.2)

NL, NR = stroke(NEAR_C, NEAR_W)
FL, FR = stroke(FAR_C, FAR_W)


def crossing(a, b):
    """Where polyline `a` first crosses polyline `b`: (point, index into a, index into b)."""
    for i in range(len(a) - 1):
        (x1, y1), (x2, y2) = a[i], a[i + 1]
        for j in range(len(b) - 1):
            (x3, y3), (x4, y4) = b[j], b[j + 1]
            den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            if abs(den) < 1e-9:
                continue
            s = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
            u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / den
            if 0 <= s <= 1 and 0 <= u <= 1:
                return (x1 + s * (x2 - x1), y1 + s * (y2 - y1)), i, j
    raise ValueError("the horns do not cross")


def extended(edge, at_end):
    """`edge` continued 12 units past its base, so two shafts' edges meet below their bases."""
    (x1, y1), (x2, y2) = (edge[-2], edge[-1]) if at_end else (edge[1], edge[0])
    L = math.hypot(x2 - x1, y2 - y1)
    beyond = (x2 + (x2 - x1) / L * 12, y2 + (y2 - y1) / L * 12)
    return edge + [beyond] if at_end else [beyond] + edge


FRX, NLX = extended(FR, at_end=True), extended(NL, at_end=False)
NOTCH, FR_END, NL_START = crossing(FRX, NLX)


FAR = (
    [(x, y, 's') for x, y in FL[1:-1]]
    + [(FL[-1][0], FL[-1][1], 'c')]
    + [(x, y, 's') for x, y in FRX[:FR_END + 1]]
)
NEAR = (
    [(x, y, 's') for x, y in NLX[NL_START + 1:-1]]
    + [(NL[-1][0], NL[-1][1], 'c')]
    + [(x, y, 's') for x, y in NR[:-1]]
)


OUTER = [
    (-66, -30, 's'),
    (-69, -40, 's'),
    (-80, -42, 'c'),   # tail tip
    (-68, -49, 'c'),   # tail root
    (-56, -52, 's'),   # rump top
    (-20, -59, 's'),   # back
    (14, -66, 's'),    # withers
    (28, -84, 's'),    # neck
    (33, -96, 's'),    # poll
    (19, -108, 'c'),   # ear tip
    (32, -106, 's'),   # ear front
] + FAR + [
    (NOTCH[0], NOTCH[1], 'c'),
] + NEAR + [
    (60, -96, 's'),    # forehead
    (68, -85, 's'),    # nose bridge
    (72, -78, 'c'),    # nose tip
    (66, -73, 's'),    # chin
    (51, -65, 's'),    # throat
    (45, -50, 's'),
    (44, -34, 's'),    # chest
    (41, -18, 's'),    # front leg front
    (38, 2, 's'),
    (37, 18, 'c'),     # front hoof front
    (47, 7, 'c'),      # summit tip
    (57, 36, 'c'),
    (51, 46, 'c'),
    (60, 70, 'c'),
    (57, 88, 'c'),     # bottom right
    (-70, 88, 'c'),    # bottom left
    (-66, 64, 'c'),
    (-74, 54, 'c'),
    (-60, 44, 'c'),    # ledge
    (-48, 40, 'c'),    # hind hoof back
    (-55, 22, 's'),    # cannon
    (-62, 8, 's'),     # hock
    (-66, -12, 's'),   # thigh
]

WHEEL_RADIUS = 16
WHEELS = ((-40, 76), (26, 76))

# The slab's underside, right to left: a tyre round each wheel, the gap between them cut up to y=70.
TYRES = [
    (50.6, 71.7, 's'), (49.2, 85.2, 's'), (40.9, 96.1, 's'), (28.2, 100.9, 's'),
    (14.8, 98.3, 's'), (4.7, 89.1, 's'),
    (1, 70, 'c'), (-15, 70, 'c'),
    (-18.7, 89.1, 's'), (-28.8, 98.3, 's'), (-42.2, 100.9, 's'), (-54.9, 96.1, 's'),
    (-63.2, 85.2, 's'), (-64.6, 71.7, 's'),
]
_BOTTOM = OUTER.index((57, 88, 'c'))
assert OUTER[_BOTTOM + 1] == (-70, 88, 'c')
CYCLING_OUTER = OUTER[:_BOTTOM] + TYRES + OUTER[_BOTTOM + 2:]

HOLE = [
    (-32, -16, 's'),   # belly rear
    (-8, -20, 's'),    # belly
    (16, -31, 's'),    # belly front
    (23, -10, 's'),    # knee
    (25, 18, 'c'),     # front hoof back
    (12, 22, 'c'),     # rock top
    (2, 29, 'c'),
    (-14, 27, 'c'),
    (-26, 35, 'c'),
    (-37, 40, 'c'),    # hind hoof front
    (-41, 14, 's'),    # hind leg front edge
    (-34, -4, 's'),    # stifle
]


def circle(centre, r):
    """Clockwise from the top, as four cubics: the standard quarter-arc approximation."""
    cx, cy = centre
    k = r * 4 * (math.sqrt(2) - 1) / 3
    quarters = (
        ((cx + k, cy - r), (cx + r, cy - k), (cx + r, cy)),
        ((cx + r, cy + k), (cx + k, cy + r), (cx, cy + r)),
        ((cx - k, cy + r), (cx - r, cy + k), (cx - r, cy)),
        ((cx - r, cy - k), (cx - k, cy - r), (cx, cy - r)),
    )
    return f"M{fmt((cx, cy - r))}" + "".join(
        f"C{fmt(c1)} {fmt(c2)} {fmt(end)}" for c1, c2, end in quarters
    ) + "Z"


def cubic(points, k=1.0):
    n = len(points)
    out = []
    for i in range(n):
        x1, y1, k1 = points[i]
        x2, y2, k2 = points[(i + 1) % n]
        x0, y0, _ = points[(i - 1) % n]
        x3, y3, _ = points[(i + 2) % n]
        if k1 == 'c':
            c1 = (x1, y1)
        else:
            c1 = (x1 + (x2 - x0) / 6 * k, y1 + (y2 - y0) / 6 * k)
        if k2 == 'c':
            c2 = (x2, y2)
        else:
            c2 = (x2 - (x3 - x1) / 6 * k, y2 - (y3 - y1) / 6 * k)
        out.append((c1, c2, (x2, y2)))
    return out


def fmt(p):
    return f"{round(p[0], 2):g},{round(p[1], 2):g}"


def path_data(points):
    segs = cubic(points)
    d = f"M{fmt(points[0][:2])}"
    for c1, c2, p in segs:
        d += f"C{fmt(c1)} {fmt(c2)} {fmt(p)}"
    return d + "Z"


def area(points):
    s = 0
    for i in range(len(points)):
        x1, y1 = points[i][:2]
        x2, y2 = points[(i + 1) % len(points)][:2]
        s += x1 * y2 - x2 * y1
    return s / 2


def source_literal(d, indent="    "):
    """Path data as the wrapped parenthesised string literal mark.py keeps it in."""
    head, rest = d[:d.index("C")], d[d.index("C"):-1]
    segs = re.findall(r"C[^C]*", rest)
    lines = [f'"{head}"']
    for i in range(0, len(segs), 2):
        lines.append(f'{indent}"{"".join(segs[i:i + 2])}"')
    lines.append(f'{indent}"Z"')
    return "\n".join(lines)


def write_mark(path):
    text = path.read_text()
    literals = (
        ("MARK", path_data(OUTER)),
        ("HOLE", path_data(HOLE)),
        ("CYCLING_MARK", path_data(CYCLING_OUTER)),
        ("REAR_WHEEL", circle(WHEELS[0], WHEEL_RADIUS)),
        ("FRONT_WHEEL", circle(WHEELS[1], WHEEL_RADIUS)),
    )
    for name, d in literals:
        text, n = re.subn(
            rf"^{name} = \(\n.*?\n\)", f"{name} = (\n    {source_literal(d)}\n)",
            text, count=1, flags=re.S | re.M,
        )
        assert n == 1, name
    path.write_text(text)


if __name__ == "__main__":
    print("outer area", area(OUTER), "hole area", area(HOLE))
    write_mark(HERE / "mark.py")
    print("written mark.py")
