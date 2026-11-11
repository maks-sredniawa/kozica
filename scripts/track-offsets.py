#!/usr/bin/env python3
"""Is 40 m for 30 s a wrong turn, or just the GPS? Phase 6b's gate (docs/phase-6b.md § 1).

Reads Tracks walked on marked Trails, from the debug gate recorder's `gate.db` or from a Hike's GPX
export, against the Trail network, and prints per file the hours, the fixes, the percentiles of each
fix's distance from the nearest edge, and every firing of § 4's leaving rule as the banner applies
it: only fixes that count (accuracy at most 50 m and stored at most 30 s after their own time; a
`gate.db` fix without accuracy does not count, as in the app, while a GPX keeps neither, so all of
its fixes count), each measured against 40 m or its own accuracy where larger, 30 s across the fixes
received, a gap of more than 30 s restarting the count. A firing still shown when its stretch ends
ends there. Each firing prints the median and worst accuracy of its counting fixes and the nearest
edge's OSM way at its start and at its peak, for § 1's verdict 3. For a GPX it also lists every gap
over 30 s inside a stretch, since the recorder dropped the fixes worse than 30 m and the worst drift
shows as a gap instead.

§ 1's bias is reported too: the minutes walked within 40 m of a second edge not joined to the
nearest one, where another Trail can hide a real wrong turn from the distance.

Usage:

    python3 scripts/track-offsets.py --network trails.network walk.gpx gate.db
    python3 scripts/track-offsets.py -s mnbm7hw8ivdqzx9l walk.gpx      # the debug build's network
    python3 scripts/track-offsets.py -s 132bc856 --pull-gate             # and its gate.db
"""

from __future__ import annotations

import argparse
import math
import sqlite3
import statistics
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import project  # noqa: E402

LEAVING_METRES = 40.0
FIX_WORST_ACCURACY_METRES = 50.0
FIX_OLDEST_MILLIS = 30_000
LEAVING_MILLIS = 30_000
SECOND_TRAIL_METRES = 40.0
CELL_DEGREES = 0.002
METRES_PER_DEGREE = 6_371_008.8 * math.pi / 180


@dataclass
class Fix:
    at_ms: int
    stretch: int
    latitude: float
    longitude: float
    accuracy: float | None
    age_ms: int = 0

    @property
    def counts(self) -> bool:
        accurate = self.accuracy is None or self.accuracy <= FIX_WORST_ACCURACY_METRES
        return accurate and self.age_ms <= FIX_OLDEST_MILLIS

    @property
    def leaving_metres(self) -> float:
        return max(LEAVING_METRES, self.accuracy or 0.0)


@dataclass
class Firing:
    since_ms: int
    last_ms: int
    latitude: float
    longitude: float
    start_edge: int
    peak_metres: float
    peak_edge: int
    shown_ms: int | None = None
    end_ms: int | None = None
    to_stretch_end: bool = False
    accuracies: list[float] = field(default_factory=list)


class Network:
    """`trails.network`'s edges as polylines (docs/phase-5.md § 2), with a grid over their segments."""

    def __init__(self, data: bytes):
        if data[:4] != b"KZTN":
            raise ValueError("not a trails.network")
        version, _, vertices, edges, points = struct.unpack_from("<HHIII", data, 4)
        if version != 3:
            raise ValueError(f"trails.network format {version}, this script reads 3")
        self.ways = struct.unpack_from(f"<{edges}q", data, 20)
        pos = 20 + 8 * edges + 4 * vertices * 2
        starts = struct.unpack_from(f"<{edges}I", data, pos)
        ends = struct.unpack_from(f"<{edges}I", data, pos + 4 * edges)
        firsts = struct.unpack_from(f"<{edges}I", data, pos + 8 * edges)
        counts = struct.unpack_from(f"<{edges}I", data, pos + 12 * edges)
        pos += 16 * edges + 7 * 4 * edges
        lats = struct.unpack_from(f"<{points}i", data, pos)
        lons = struct.unpack_from(f"<{points}i", data, pos + 4 * points)
        self.ends = list(zip(starts, ends))
        self.lines = [
            [(lats[i] / 1e7, lons[i] / 1e7) for i in range(first, first + count)]
            for first, count in zip(firsts, counts)
        ]
        self.grid: dict[tuple[int, int], set[int]] = {}
        for edge, line in enumerate(self.lines):
            for a, b in zip(line, line[1:]):
                for cell in self._cells(a, b):
                    self.grid.setdefault(cell, set()).add(edge)

    def _cells(self, a, b):
        la0, la1 = sorted((a[0], b[0]))
        lo0, lo1 = sorted((a[1], b[1]))
        for i in range(math.floor(la0 / CELL_DEGREES), math.floor(la1 / CELL_DEGREES) + 1):
            for j in range(math.floor(lo0 / CELL_DEGREES), math.floor(lo1 / CELL_DEGREES) + 1):
                yield i, j

    def nearby(self, latitude: float, longitude: float, reach: int) -> set[int]:
        i, j = math.floor(latitude / CELL_DEGREES), math.floor(longitude / CELL_DEGREES)
        found: set[int] = set()
        for di in range(-reach, reach + 1):
            for dj in range(-reach, reach + 1):
                found |= self.grid.get((i + di, j + dj), set())
        return found

    def distances(self, latitude: float, longitude: float) -> list[tuple[float, int]]:
        """(metres, edge) for the edges near the point, nearest first; widened until one is found."""
        reaches = (1, 3, 10, 40)
        for reach in reaches:
            near = self.nearby(latitude, longitude, reach)
            if not near:
                continue
            found = sorted((to_line(latitude, longitude, self.lines[e]), e) for e in near)
            # Only an edge within the cells' inner radius is surely the nearest of all.
            if found[0][0] <= reach * CELL_DEGREES * METRES_PER_DEGREE * 0.6 or reach == reaches[-1]:
                return found
        return [(math.inf, -1)]

    def joined(self, a: int, b: int) -> bool:
        return bool(set(self.ends[a]) & set(self.ends[b]))


def to_line(latitude: float, longitude: float, line: list[tuple[float, float]]) -> float:
    east = math.cos(math.radians(latitude)) * METRES_PER_DEGREE
    best = math.inf
    for a, b in zip(line, line[1:]):
        ax, ay = (a[1] - longitude) * east, (a[0] - latitude) * METRES_PER_DEGREE
        bx, by = (b[1] - longitude) * east, (b[0] - latitude) * METRES_PER_DEGREE
        dx, dy = bx - ax, by - ay
        length = dx * dx + dy * dy
        t = 0.0 if length == 0 else min(1.0, max(0.0, -(ax * dx + ay * dy) / length))
        best = min(best, math.hypot(ax + dx * t, ay + dy * t))
    return best


def firings(fixes: list[Fix], nearest: list[tuple[float, int]]) -> list[Firing]:
    """§ 4's rule over the fixes in order, as `Leaving.after` applies it, each with its (metres, edge)."""
    found: list[Firing] = []
    run: Firing | None = None
    previous: Fix | None = None

    def close_at_stretch_end():
        if run is not None and run.shown_ms is not None and run.end_ms is None:
            run.end_ms = previous.at_ms
            run.to_stretch_end = True

    for fix, (distance, edge) in zip(fixes, nearest):
        if previous is not None and fix.stretch != previous.stretch:
            close_at_stretch_end()
            run = None
        previous = fix
        if not fix.counts:
            continue
        if distance <= fix.leaving_metres:
            if run is not None and run.shown_ms is not None:
                run.end_ms = fix.at_ms
            run = None
            continue
        if run is None or (run.shown_ms is None and fix.at_ms - run.last_ms > LEAVING_MILLIS):
            run = Firing(fix.at_ms, fix.at_ms, fix.latitude, fix.longitude, edge, distance, edge)
        run.last_ms = fix.at_ms
        if fix.accuracy is not None:
            run.accuracies.append(fix.accuracy)
        if distance > run.peak_metres:
            run.peak_metres, run.peak_edge = distance, edge
        if run.shown_ms is None and fix.at_ms - run.since_ms >= LEAVING_MILLIS:
            run.shown_ms = fix.at_ms
            found.append(run)
    close_at_stretch_end()
    return found


def read_gpx(path: Path) -> list[Fix]:
    fixes = []
    stretch = 0
    for element in ET.parse(path).iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if tag == "trkseg":
            stretch += 1
            for point in element:
                stamp = next((c.text for c in point if c.tag.endswith("time")), None)
                if stamp is None:
                    continue
                at = datetime.fromisoformat(stamp.replace("Z", "+00:00")).timestamp()
                fixes.append(Fix(int(at * 1000), stretch, float(point.get("lat")), float(point.get("lon")), None))
    return fixes


def read_gate(path: Path) -> list[Fix]:
    """A fix without accuracy is `Double.MAX_VALUE` to the app (`toFollowFix`), so it never counts."""
    db = sqlite3.connect(path)
    return [
        Fix(at, segment, latitude, longitude, math.inf if accuracy is None else accuracy, stored - at)
        for at, stored, segment, latitude, longitude, accuracy in db.execute(
            "SELECT fix_time, stored_at, segment, latitude, longitude, accuracy FROM gate_points ORDER BY fix_time"
        )
    ]


def pull(serial: str, path: str, name: str, folder: Path | None = None) -> Path:
    out = (folder or Path(tempfile.mkdtemp(prefix="offsets-"))) / name
    shell = ["adb", "-s", serial, "exec-out", "run-as", project.DEBUG_APPLICATION_ID]
    listed = subprocess.run(shell + ["ls", path], capture_output=True, text=True)
    if listed.returncode != 0 or listed.stdout.strip() != path:
        sys.exit(f"no {path} in the debug build on {serial}")
    with out.open("wb") as f:
        subprocess.run(shell + ["cat", path], stdout=f, check=True)
    return out


def pull_gate(serial: str) -> Path:
    """gate.db with its -wal, whose rows are the newest; a copy without it reads stale."""
    database = pull(serial, "databases/gate.db", "gate.db")
    shell = ["adb", "-s", serial, "exec-out", "run-as", project.DEBUG_APPLICATION_ID, "ls", "databases/gate.db-wal"]
    if subprocess.run(shell, capture_output=True).returncode == 0:
        pull(serial, "databases/gate.db-wal", "gate.db-wal", database.parent)
    return database


def stamp(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S")


def percentile(sorted_values: list[float], p: float) -> float:
    return sorted_values[min(len(sorted_values) - 1, int(p / 100 * len(sorted_values)))]


def report(name: str, fixes: list[Fix], network: Network, is_gpx: bool) -> None:
    print(f"== {name}")
    if not fixes:
        print("  no fixes")
        return
    stretches: dict[int, list[Fix]] = {}
    for fix in fixes:
        stretches.setdefault(fix.stretch, []).append(fix)
    hours = sum((s[-1].at_ms - s[0].at_ms) / 3_600_000 for s in stretches.values())
    near = [network.distances(f.latitude, f.longitude) for f in fixes]
    metres = [n[0][0] for n in near]
    ordered = sorted(metres)
    print(f"  {hours:.2f} h walked, {len(fixes)} fixes in {len(stretches)} stretches, "
          f"{sum(f.counts for f in fixes)} that count")
    print("  distance from the nearest edge: " + ", ".join(
        f"p{p} {percentile(ordered, p):.1f} m" for p in (50, 90, 95, 99)) + f", max {ordered[-1]:.1f} m")

    second = 0.0
    for i, (fix, found) in enumerate(zip(fixes, near)):
        nearest = found[0][1]
        others = [e for d, e in found[1:] if d <= SECOND_TRAIL_METRES and e != nearest and not network.joined(e, nearest)]
        following = fixes[i + 1] if i + 1 < len(fixes) and fixes[i + 1].stretch == fix.stretch else None
        if others and following is not None:
            second += (following.at_ms - fix.at_ms) / 60_000
    print(f"  minutes within {SECOND_TRAIL_METRES:.0f} m of a second edge not joined to the nearest: {second:.1f}")

    found = firings(fixes, [n[0] for n in near])
    rate = len(found) / hours if hours > 0 else 0.0
    print(f"  firings: {len(found)}, {rate:.2f} per hour")

    def way(edge: int) -> str:
        return str(network.ways[edge]) if edge >= 0 else "none"

    for f in found:
        length = f"{(f.end_ms - f.since_ms) / 1000:.0f} s off" + (", to the end of its stretch" if f.to_stretch_end else "")
        accuracy = (f", accuracy median {statistics.median(f.accuracies):.0f} m, worst {max(f.accuracies):.0f} m"
                    if f.accuracies else "")
        print(f"    {stamp(f.since_ms)}  {length}, peak {f.peak_metres:.0f} m, "
              f"at {f.latitude:.6f},{f.longitude:.6f}{accuracy}, "
              f"way {way(f.start_edge)} at its start, {way(f.peak_edge)} at its peak")

    if is_gpx:
        gaps = [
            (a, b)
            for s in stretches.values()
            for a, b in zip(s, s[1:])
            if b.at_ms - a.at_ms > LEAVING_MILLIS
        ]
        print(f"  gaps over {LEAVING_MILLIS // 1000} s inside a stretch: {len(gaps)}")
        for a, b in gaps:
            print(f"    {stamp(a.at_ms)}  {(b.at_ms - a.at_ms) / 1000:.0f} s, "
                  f"from {a.latitude:.6f},{a.longitude:.6f} ({metres[fixes.index(a)]:.0f} m off)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tracks", nargs="*", type=Path, help="GPX files and gate.db copies")
    parser.add_argument("--network", type=Path, help="a trails.network; the debug build's with -s")
    parser.add_argument("--pull-gate", action="store_true", help="also read the debug build's gate.db")
    parser.add_argument("-s", "--serial", help="adb serial of the phone to pull from")
    args = parser.parse_args()
    if args.network is None and args.serial is None:
        parser.error("give --network, or -s to pull the debug build's")
    if args.pull_gate and args.serial is None:
        parser.error("--pull-gate needs -s")
    tracks = list(args.tracks)
    if args.pull_gate:
        tracks.append(pull_gate(args.serial))
    if not tracks:
        parser.error("nothing to read")

    network_path = args.network or pull(args.serial, "files/network/trails.network", "trails.network")
    network = Network(network_path.read_bytes())
    for path in tracks:
        is_gpx = path.suffix.lower() == ".gpx"
        report(str(path), read_gpx(path) if is_gpx else read_gate(path), network, is_gpx)
    return 0


if __name__ == "__main__":
    sys.exit(main())
