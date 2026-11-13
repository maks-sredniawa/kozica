#!/usr/bin/env python3
"""Builds the Trail network, the name index and the walks of docs/phase-5.md § 2, docs/phase-6c.md § 2,
docs/phase-7a.md § 2 and docs/phase-7b.md §§ 2 and 6 from the Regions' merged cuts.

    python3 scripts/build-network.py --relief map-data/relief/relief.pmtiles --out map-data/base-assets \\
        --region tatry map-data/merged/pin/tatry.osm.pbf map-data/archives/tatry.trails ...

The Trails, the Bike routes and their ways are the ones KozicaProfile.java listed for each Region; the
ways' nodes and tags, the relations' own tags and the named places are read from the merged cuts. Needs pyosmium 4.2+ and Pillow with WebP, imported only
where they are used, so `scripts/test_build_network.py` runs on a plain Python.
"""

from __future__ import annotations

import argparse
import functools
import gzip
import heapq
import io
import math
import mmap
import re
import sqlite3
import struct
import sys
import unicodedata
from collections import Counter
from dataclasses import dataclass, field, replace
from pathlib import Path

FORMAT_VERSION = 3
MAGIC = b"KZTN"
WALK_FORMAT_VERSION = 2
WALK_MAGIC = b"KZTE"
DENSIFY_METRES = 30.0
DEAD_BAND_METRES = 5.0
FLAT_METRES_PER_SECOND = 4000 / 3600
SECONDS_PER_METRE_UP = 600 / 100
SECONDS_PER_METRE_STEEP_DOWN = 300 / 100
STEEP_GRADE = 0.10
RELIEF_ZOOM = 12
RELIEF_TILE = 512
EARTH_RADIUS = 6_371_008.8

ONEWAY_HIGHWAYS = {"path", "footway", "steps", "via_ferrata"}
SAC_GRADES = {
    "hiking": 1,
    "mountain_hiking": 2,
    "demanding_mountain_hiking": 3,
    "alpine_hiking": 4,
    "demanding_alpine_hiking": 5,
    "difficult_alpine_hiking": 6,
}
FORWARD_ONLY = 1
BACKWARD_ONLY = 2
TURNED = {FORWARD_ONLY: BACKWARD_ONLY, BACKWARD_ONLY: FORWARD_ONLY}
ONEWAY_VALUES = {"yes": FORWARD_ONLY, "true": FORWARD_ONLY, "1": FORWARD_ONLY, "-1": BACKWARD_ONLY}
RINGS = {"roundabout", "circular"}
ONE_WAY_SHARE = 0.75
SET_TRAIL = 1
SET_TOURING = 2
SET_MTB = 4
KIND_SETS = {"touring": SET_TOURING, "mtb": SET_MTB}
PAVED, UNPAVED, ROUGH = 0, 1, 2
SURFACES = {
    **dict.fromkeys(("asphalt", "paving_stones", "sett", "chipseal", "paved", "metal", "wood"), PAVED),
    **dict.fromkeys(("compacted", "fine_gravel", "gravel", "unpaved", "dirt", "earth", "ground", "grass_paver"), UNPAVED),
    **dict.fromkeys(("rock", "stone", "pebblestone", "roots", "mud", "sand", "grass", "woodchips"), ROUGH),
}
PAVED_HIGHWAYS = {"motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
                  "living_street", "pedestrian", "road", "busway", "service", "cycleway"}
TRACK_GRADES = {"grade1": PAVED, "grade2": UNPAVED, "grade3": UNPAVED, "grade4": ROUGH, "grade5": ROUGH}
NO_RIDING = math.nan
NAME_KEYS = ("name", "name:pl", "name:sk", "name:cs", "alt_name", "old_name")
ENDS_KEYS = ("description", "osmc:name", "note:cs", "note")
TRAIL_KEYS = ("name", "name:pl", "ref", "from", "to", *ENDS_KEYS)
BIKE_ROUTE_KEYS = ("network", "mtb:scale", "mtb:scale:uphill", "signed_direction", "oneway")
WAY_KEYS = ("highway", "oneway", "oneway:foot", "foot:backward", "foot:forward", "sac_scale", "oneway:bicycle",
            "mtb:scale", "mtb:scale:uphill", "surface", "tracktype", "junction")
COLOUR_CODE = re.compile(r"^\[[^\]]{1,3}\]\s*")
DASH = re.compile(r"\s+[-–—]\s+")
LONGEST_END = 40
TIED_GAPS = 1.0
SEARCHED_RUNS = 60
SEARCH_STEPS = 200_000
ENDLESS_LONGER = 2
BESIDE_METRES = 150.0
WIDEN_METRES = 250.0
OFF_WALK = {"alternative", "excursion", "approach", "branch"}
UNDECOMPOSED = str.maketrans({"ł": "l", "đ": "d", "ð": "d", "ø": "o", "ß": "ss", "æ": "ae", "œ": "oe", "ı": "i"})


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def fold(text: str) -> str:
    """The search key: lower case, accents off, letters no decomposition reaches mapped by hand."""
    lowered = text.lower().translate(UNDECOMPOSED)
    bare = "".join(c for c in unicodedata.normalize("NFKD", lowered) if not unicodedata.combining(c))
    return " ".join("".join(c if c.isascii() and c.isalnum() else " " for c in bare).split())


def distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS * math.asin(math.sqrt(a))


@dataclass(frozen=True)
class Climb:
    ascent: float
    descent: float
    steep_ascent: float
    steep_descent: float


def runs(lengths: list[float], heights: list[float]) -> list[tuple[float, float]]:
    """Each stretch from the point the height was last counted at to the next point a dead band or more above
    or below it, as (length, change); the last ends at the last point whatever its height differs by there.

    `lengths[i]` is the horizontal distance from point i to point i + 1."""
    found = []
    reference = heights[0]
    run = 0.0
    for i, (length, height) in enumerate(zip(lengths, heights[1:])):
        run += length
        change = height - reference
        if abs(change) < DEAD_BAND_METRES and i < len(lengths) - 1:
            continue
        found.append((run, change))
        reference, run = height, 0.0
    return found


def is_steep(length: float, change: float) -> bool:
    return length > 0 and abs(change) / length > STEEP_GRADE


def climb(lengths: list[float], heights: list[float]) -> Climb:
    """Ascent and descent over `runs()`, and the parts of each steeper than STEEP_GRADE. The last run counts
    too, so the edges of a Route climb exactly what its ends differ by."""
    ascent = descent = steep_ascent = steep_descent = 0.0
    for run, change in runs(lengths, heights):
        steep = is_steep(run, change)
        if change > 0:
            ascent += change
            steep_ascent += change if steep else 0.0
        else:
            descent -= change
            steep_descent -= change if steep else 0.0
    return Climb(ascent, descent, steep_ascent, steep_descent)


def walking_seconds(length: float, up: float, steep_down: float) -> float:
    """The PTTK marker's rule, docs/phase-5.md § 3, unrounded."""
    return length / FLAT_METRES_PER_SECOND + up * SECONDS_PER_METRE_UP + steep_down * SECONDS_PER_METRE_STEEP_DOWN


@dataclass(frozen=True)
class RidingConstants:
    """docs/phase-7b.md § 2's starting constants, speeds in km/h; `flat` and `descent_cap` are indexed by surface
    class, `mtb_caps` hold S2's, S3's, and S4's and over. RIDING is what R3's gate fitted."""
    flat: tuple[float, float, float] = (20.0, 15.0, 10.0)
    descent_cap: tuple[float, float, float] = (40.0, 25.0, 15.0)
    seconds_per_metre_up: float = 10.0
    descent_factor: float = 1.5
    pushing_factor: float = 1.0
    mtb_caps: tuple[float, float, float] = (10.0, 6.0, 4.0)

    def mtb_cap(self, mtb: int) -> float:
        """The speed an `mtb` byte caps a stretch at."""
        scale = mtb - 1
        return math.inf if scale < 2 else self.mtb_caps[min(scale, 4) - 2]


RIDING = RidingConstants(flat=(17.53, 15.26, 11.6), descent_cap=(19.28, 16.79, 18.2), seconds_per_metre_up=3.22,
                         descent_factor=1.92, pushing_factor=0.92)


def at_kmh(length: float, kmh: float) -> float:
    return length * 3.6 / kmh


def riding_stretch(length: float, change: float, surface: int, mtb: int, mtb_up: int, constants: RidingConstants) -> float:
    flat = constants.flat[surface]
    if change > 0:
        seconds = at_kmh(length, flat) + change * constants.seconds_per_metre_up
        cap = constants.mtb_cap(mtb_up or mtb)
    else:
        speed = min(flat * constants.descent_factor, constants.descent_cap[surface]) if change < 0 else flat
        seconds = at_kmh(length, speed)
        cap = constants.mtb_cap(mtb)
    pushing = walking_seconds(length, max(change, 0.0), -change if change < 0 and is_steep(length, change) else 0.0)
    return min(max(seconds, at_kmh(length, cap)), pushing * constants.pushing_factor)


def riding_seconds(lengths: list[float], heights: list[float], surface: int, mtb: int, mtb_up: int,
                   constants: RidingConstants = RIDING) -> tuple[float, float]:
    """docs/phase-7b.md § 2: riding an edge forward and backward over `climb()`'s runs, each timed by its length
    and its change, so the metres each way climbs are the edge's ascent and descent."""
    stretches = runs(lengths, heights)
    forward = sum(riding_stretch(length, change, surface, mtb, mtb_up, constants) for length, change in stretches)
    backward = sum(riding_stretch(length, -change, surface, mtb, mtb_up, constants) for length, change in stretches[::-1])
    return forward, backward


def oneway(tags: dict[str, str]) -> int:
    """docs/phase-5.md § 3: the walker's direction, never a vehicle's."""
    foot = tags.get("oneway:foot")
    if foot is not None:
        return ONEWAY_VALUES.get(foot, 0)
    if tags.get("foot:backward") == "no":
        return FORWARD_ONLY
    if tags.get("foot:forward") == "no":
        return BACKWARD_ONLY
    if tags.get("highway") in ONEWAY_HIGHWAYS:
        return ONEWAY_VALUES.get(tags.get("oneway", ""), 0)
    return 0


def ride(tags: dict[str, str]) -> int:
    """docs/phase-7a.md § 2: the rider's direction, `oneway:bicycle` over `oneway` on any highway; a roundabout
    untagged by either is one-way along the way (docs/phase-7b.md § 6)."""
    value = tags.get("oneway:bicycle", tags.get("oneway"))
    if value is None:
        return FORWARD_ONLY if tags.get("junction") in RINGS else 0
    return ONEWAY_VALUES.get(value, 0)


def grade(value: str | None) -> int:
    """An `mtb:scale`'s leading digit plus one, 0 ungraded."""
    return int(value[0]) + 1 if value and value[0].isdigit() else 0


def surface_class(tags: dict[str, str]) -> int:
    """docs/phase-7b.md § 2: `surface` where the table holds it, else `highway` and `tracktype`; a way neither
    places is rough, the slowest a rider is assumed to go where nothing is known of the way."""
    surface = tags.get("surface", "")
    if surface in SURFACES:
        return SURFACES[surface]
    if surface.startswith("concrete"):
        return PAVED
    highway = tags.get("highway", "")
    if highway == "track":
        return TRACK_GRADES.get(tags.get("tracktype", ""), UNPAVED)
    if highway in PAVED_HIGHWAYS or highway.endswith("_link"):
        return PAVED
    return ROUGH


def rider_direction(ride: int, bikes: frozenset, kind: str) -> int:
    """docs/phase-7b.md § 2, for one kind: the way's own direction where it sets one, else the sense every one
    of that kind's Bike routes rides it in; both ways where any rides it both ways or two disagree, and 0 on an
    edge with none of that kind."""
    senses = {sense for _, _, sense, of in bikes if of == kind}
    if not senses:
        return 0
    if ride:
        return ride
    return senses.pop() if len(senses) == 1 else 0


class Relief:
    """Heights from the relief's Terrarium tiles, bilinear between pixel centres."""

    def __init__(self, path: Path) -> None:
        self.file = path.open("rb")
        self.data = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        header = self.data[:127]
        if header[:7] != b"PMTiles" or header[7] != 3:
            sys.exit(f"build-network: {path} is not a PMTiles v3 archive")
        self.root = struct.unpack_from("<QQ", header, 8)
        self.leaves = struct.unpack_from("<Q", header, 40)[0]
        self.tiles = struct.unpack_from("<Q", header, 56)[0]
        self.compression = header[97]
        self.missing = 0

    def _directory(self, offset: int, length: int) -> list[tuple[int, int, int, int]]:
        raw = self.data[offset:offset + length]
        data = gzip.decompress(raw) if self.compression == 2 else raw
        count, pos = _varint(data, 0)
        ids, runs, lengths, offsets = [], [], [], []
        last = 0
        for _ in range(count):
            delta, pos = _varint(data, pos)
            last += delta
            ids.append(last)
        for target in (runs, lengths):
            for _ in range(count):
                value, pos = _varint(data, pos)
                target.append(value)
        for i in range(count):
            value, pos = _varint(data, pos)
            offsets.append(offsets[i - 1] + lengths[i - 1] if value == 0 and i > 0 else value - 1)
        return list(zip(ids, runs, lengths, offsets))

    def _tile_bytes(self, wanted: int) -> bytes | None:
        offset, length = self.root
        for _ in range(4):
            entries = self._directory(offset, length)
            found = None
            for entry in entries:
                if entry[0] <= wanted:
                    found = entry
                else:
                    break
            if found is None:
                return None
            tile_id, run, size, at = found
            if run == 0:
                offset, length = self.leaves + at, size
                continue
            if wanted >= tile_id + run:
                return None
            return self.data[self.tiles + at:self.tiles + at + size]
        return None

    @functools.lru_cache(maxsize=256)
    def _tile(self, x: int, y: int) -> bytes | None:
        from PIL import Image

        webp = self._tile_bytes(_tile_id(RELIEF_ZOOM, x, y))
        if webp is None:
            return None
        image = Image.open(io.BytesIO(webp)).convert("RGB")
        if image.size != (RELIEF_TILE, RELIEF_TILE):
            sys.exit(f"build-network: relief tile {x}/{y} is {image.size}, not {RELIEF_TILE} px")
        return image.tobytes()

    def _pixel(self, px: int, py: int) -> float | None:
        tile = self._tile(px // RELIEF_TILE, py // RELIEF_TILE)
        if tile is None:
            return None
        i = 3 * ((py % RELIEF_TILE) * RELIEF_TILE + px % RELIEF_TILE)
        return tile[i] * 256 + tile[i + 1] + tile[i + 2] / 256 - 32768

    def height(self, lat: float, lon: float) -> float | None:
        world = RELIEF_TILE * (1 << RELIEF_ZOOM)
        x = (lon + 180) / 360 * world - 0.5
        sin = math.sin(math.radians(lat))
        y = (0.5 - math.log((1 + sin) / (1 - sin)) / (4 * math.pi)) * world - 0.5
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        corners = [self._pixel(x0, y0), self._pixel(x0 + 1, y0), self._pixel(x0, y0 + 1), self._pixel(x0 + 1, y0 + 1)]
        if any(c is None for c in corners):
            self.missing += 1
            return None
        top = corners[0] * (1 - fx) + corners[1] * fx
        bottom = corners[2] * (1 - fx) + corners[3] * fx
        return top * (1 - fy) + bottom * fy


class Level:
    """Heights for edges only their lengths are read from."""

    def height(self, lat: float, lon: float) -> float:
        return 0.0


def _varint(data: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, pos
        shift += 7


def _tile_id(z: int, x: int, y: int) -> int:
    n = 1 << z
    d = 0
    s = n // 2
    while s > 0:
        rx = 1 if x & s else 0
        ry = 1 if y & s else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x, y = n - 1 - x, n - 1 - y
            x, y = y, x
        s //= 2
    return (n * n - 1) // 3 + d


def place_kind(tags) -> str | None:
    """docs/phase-5.md § 2, in the `pois` layer's order where the two overlap."""
    if tags.get("tourism") == "alpine_hut":
        return "hut"
    if tags.get("tourism") == "wilderness_hut" or (
        tags.get("amenity") == "shelter" and tags.get("shelter_type") != "public_transport"
    ):
        return "shelter"
    if tags.get("natural") == "peak":
        return "peak"
    if tags.get("natural") == "saddle" or tags.get("mountain_pass") == "yes":
        return "pass"
    if tags.get("tourism") == "viewpoint":
        return "viewpoint"
    place = tags.get("place")
    if place in ("town", "village", "hamlet"):
        return place
    if place in ("locality", "quarter", "neighbourhood", "suburb"):
        return "place"
    if tags.get("natural") == "water":
        return "water"
    if tags.get("natural") == "grassland" or tags.get("landuse") == "meadow":
        return "clearing"
    return None


def names(tags) -> list[str]:
    found = []
    for key in NAME_KEYS:
        for value in (tags.get(key) or "").split(";"):
            value = value.strip()
            if value and value not in found:
                found.append(value)
    return found


def without_colour_code(value: str | None) -> str | None:
    """KČT leads a name with its colour's letter, `[Ž] A - B`, which the sheet shows already."""
    return COLOUR_CODE.sub("", value.strip(), count=1).strip() or None if value else None


def written_ends(value: str | None) -> tuple[str, str] | None:
    """Place names between spaced dashes, the first and last the ends; a mapper's prose fails the length."""
    places = [place.strip() for place in DASH.split(without_colour_code(value) or "")]
    if len(places) >= 2 and all(0 < len(place) <= LONGEST_END and ";" not in place for place in places):
        return places[0], places[-1]
    return None


def ends_of(tags: dict[str, str]) -> tuple[str, str] | None:
    """docs/phase-6c.md § 2: `from` and `to`, else the ends written in the first of ENDS_KEYS that holds them."""
    start, end = without_colour_code(tags.get("from")), without_colour_code(tags.get("to"))
    if start and end:
        return start, end
    return next((ends for key in ENDS_KEYS if (ends := written_ends(tags.get(key)))), None)


def point_on_surface(rings: list[list[tuple[float, float]]]) -> tuple[float, float]:
    """The middle of the widest stretch inside the area along the line through its middle latitude."""
    lats = [lat for ring in rings for lat, _ in ring]
    middle = (min(lats) + max(lats)) / 2
    crossings = []
    for ring in rings:
        for (lat1, lon1), (lat2, lon2) in zip(ring, ring[1:]):
            if (lat1 <= middle < lat2) or (lat2 <= middle < lat1):
                crossings.append(lon1 + (middle - lat1) * (lon2 - lon1) / (lat2 - lat1))
    crossings.sort()
    best = max(zip(crossings[::2], crossings[1::2]), key=lambda pair: pair[1] - pair[0], default=None)
    if best is None:
        return rings[0][0]
    return middle, (best[0] + best[1]) / 2


@dataclass
class TrailLine:
    colour: str
    symbol: str | None
    members: list[tuple[int, str]]
    kind: str | None = None
    senses: dict[int, int] = field(default_factory=dict)


def parse_trails(text: str) -> tuple[dict[int, TrailLine], dict[int, list[int]]]:
    """One Region's `<id>.trails`, as KozicaProfile.Membership.lines() writes it."""
    trails, long_distance = {}, {}
    for line in text.splitlines():
        kind, number, *rest = line.split("\t")
        if kind == "trail":
            colour, symbol, members = rest
            parsed = []
            for member in members.split():
                way, _, role = member.partition("/")
                parsed.append((int(way), role))
            trails[int(number)] = TrailLine(colour, symbol or None, parsed)
        elif kind == "long":
            long_distance[int(number)] = [int(section) for section in rest[0].split()]
    return trails, long_distance


def parse_bike_routes(text: str) -> tuple[dict[int, TrailLine], dict[int, list[int]]]:
    """One Region's Bike routes in the same file: `bike <id> <colour> <kind> <way>[/<role>][@f|@b] ...`, the
    sense the way is ridden in against its nodes, and `sections <id> <section> ...`."""
    bikes, sections = {}, {}
    senses = {"f": FORWARD_ONLY, "b": BACKWARD_ONLY}
    for line in text.splitlines():
        kind, number, *rest = line.split("\t")
        if kind == "bike":
            colour, route_kind, members = rest
            parsed, sensed = [], {}
            for member in members.split():
                listed, _, sense = member.partition("@")
                way, _, role = listed.partition("/")
                parsed.append((int(way), role))
                if sense:
                    sensed[int(way)] = senses[sense]
            bikes[int(number)] = TrailLine(colour, None, parsed, route_kind, sensed)
        elif kind == "sections":
            sections[int(number)] = [int(section) for section in rest[0].split()]
    return bikes, sections


def merge_lists(lists: list[list]) -> list:
    """A relation's members as the Regions saw them: the longest list's order, then the others' extras."""
    ordered = sorted(lists, key=len, reverse=True)
    merged = list(dict.fromkeys(ordered[0]))
    seen = set(merged)
    for extra in ordered[1:]:
        for item in extra:
            if item not in seen:
                seen.add(item)
                merged.append(item)
    return merged


def merge_trails(regions: list[tuple[dict[int, TrailLine], dict[int, list[int]]]]
                 ) -> tuple[dict[int, TrailLine], dict[int, list[int]]]:
    """A relation crossing a Region's boundary is in each Region's file with the members its cut holds."""
    lines: dict[int, list[TrailLine]] = {}
    sections: dict[int, list[list[int]]] = {}
    for trails, long_distance in regions:
        for number, line in trails.items():
            lines.setdefault(number, []).append(line)
        for number, members in long_distance.items():
            sections.setdefault(number, []).append(members)
    trails = {}
    for number, found in sorted(lines.items()):
        longest = max(found, key=lambda line: len(line.members))
        roles = {}
        for line in [longest, *found]:
            for way, role in line.members:
                roles.setdefault(way, role)
        order = merge_lists([[way for way, _ in line.members] for line in found])
        senses = {way: sense for line in found for way, sense in line.senses.items()}
        trails[number] = TrailLine(longest.colour, longest.symbol, [(way, roles[way]) for way in order], longest.kind, senses)
    return trails, {number: merge_lists(found) for number, found in sorted(sections.items())}


@dataclass
class Place:
    kind: str
    lat: float
    lon: float
    region: str
    names: list[str]


def read_cuts(regions: list[tuple[str, str, set[int], set[int]]]) -> tuple[dict, dict, dict]:
    """Every wanted Trail way, with its nodes and the tags the network reads, every wanted relation's tags
    that title a Trail, and every named place."""
    import osmium

    class Reader(osmium.SimpleHandler):
        def __init__(self, region: str, wanted_ways: set[int], wanted_relations: set[int], ways: dict,
                     relations: dict, places: dict) -> None:
            super().__init__()
            self.region, self.wanted_ways, self.ways, self.places = region, wanted_ways, ways, places
            self.wanted_relations, self.relations = wanted_relations, relations

        def _place(self, key, tags, lat: float, lon: float) -> None:
            if key in self.places:
                return
            kind = place_kind(tags)
            found = names(tags)
            if kind and found:
                self.places[key] = Place(kind, lat, lon, self.region, found)

        def node(self, n) -> None:
            if "name" in n.tags and n.location.valid():
                self._place(("n", n.id), n.tags, n.location.lat, n.location.lon)

        def way(self, w) -> None:
            if w.id in self.wanted_ways and w.id not in self.ways:
                nodes = [(nd.ref, nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
                if len(nodes) >= 2:
                    tags = {k: w.tags.get(k) for k in WAY_KEYS if k in w.tags}
                    self.ways[w.id] = (nodes, tags)

        def relation(self, r) -> None:
            if r.id in self.wanted_relations and r.id not in self.relations:
                self.relations[r.id] = {key: r.tags.get(key) for key in (*TRAIL_KEYS, *BIKE_ROUTE_KEYS) if key in r.tags}

        def area(self, a) -> None:
            if "name" not in a.tags or place_kind(a.tags) is None:
                return
            rings = [[(nd.location.lat, nd.location.lon) for nd in ring] for ring in a.outer_rings()]
            rings += [[(nd.location.lat, nd.location.lon) for nd in inner]
                      for outer in a.outer_rings() for inner in a.inner_rings(outer)]
            rings = [ring for ring in rings if len(ring) >= 4]
            if rings:
                lat, lon = point_on_surface(rings)
                self._place(("w" if a.from_way() else "r", a.orig_id()), a.tags, lat, lon)

    ways: dict = {}
    relations: dict = {}
    places: dict = {}
    for region, pbf, wanted_ways, wanted_relations in sorted(regions):
        Reader(region, wanted_ways, wanted_relations, ways, relations, places).apply_file(pbf, locations=True)
        log(f"read {region}: {len(ways)} Trail ways, {len(places)} places so far")
    return ways, relations, places


@dataclass
class Edge:
    way: int
    points: list[tuple[int, float, float]]
    heights: list[float]
    length: float
    climb: Climb
    flags: int
    sac: int
    trails: frozenset = frozenset()
    ways: tuple[int, ...] = ()
    sets: int = SET_TRAIL
    ride: int = 0
    mtb: int = 0
    mtb_up: int = 0
    bikes: frozenset = frozenset()
    surface: int = ROUGH
    ride_touring: int = 0
    ride_mtb: int = 0
    riding_forward: float = NO_RIDING
    riding_backward: float = NO_RIDING


def densify(points: list[tuple[int, float, float]]) -> list[tuple[int, float, float]]:
    """Every OSM node, and points between them no more than DENSIFY_METRES apart, for the climb alone:
    the added points have ref 0 and are not stored."""
    out = [points[0]]
    for (_, lat1, lon1), point in zip(points, points[1:]):
        _, lat2, lon2 = point
        steps = math.ceil(distance(lat1, lon1, lat2, lon2) / DENSIFY_METRES)
        for step in range(1, steps):
            f = step / steps
            out.append((0, lat1 + (lat2 - lat1) * f, lon1 + (lon2 - lon1) * f))
        out.append(point)
    return out


def fill_heights(heights: list[float | None]) -> list[float] | None:
    """A point the relief does not reach takes its neighbours' heights, interpolated along the edge."""
    known = [i for i, h in enumerate(heights) if h is not None]
    if not known:
        return None
    out = list(heights)
    for i, h in enumerate(heights):
        if h is not None:
            continue
        before = max((k for k in known if k < i), default=None)
        after = min((k for k in known if k > i), default=None)
        if before is None:
            out[i] = heights[after]
        elif after is None:
            out[i] = heights[before]
        else:
            out[i] = heights[before] + (heights[after] - heights[before]) * (i - before) / (after - before)
    return out


@dataclass(frozen=True)
class Piece:
    way: int
    points: list[tuple[int, float, float]]
    flags: int
    sac: int
    trails: frozenset = frozenset()
    ways: tuple[int, ...] = ()
    sets: int = SET_TRAIL
    ride: int = 0
    mtb: int = 0
    mtb_up: int = 0
    bikes: frozenset = frozenset()
    surface: int = ROUGH

    def turned(self) -> Piece:
        return replace(
            self, points=self.points[::-1], flags=TURNED.get(self.flags, self.flags), ride=TURNED.get(self.ride, self.ride),
            bikes=frozenset((number, on_walk, TURNED.get(sense, sense), kind) for number, on_walk, sense, kind in self.bikes))

    def walks_like(self, other: Piece) -> bool:
        """A change of surface class cuts only a Bike route's edges, a Trail's shared with one included, so only
        the Trail-only edges keep 7a's."""
        ridden = (self.sets | other.sets) & (SET_TOURING | SET_MTB)
        return ((self.flags, self.sac, self.trails, self.sets, self.ride, self.mtb, self.mtb_up, self.bikes)
                == (other.flags, other.sac, other.trails, other.sets, other.ride, other.mtb, other.mtb_up, other.bikes)
                and (not ridden or self.surface == other.surface))

    def length(self) -> float:
        return sum(distance(a[1], a[2], b[1], b[2]) for a, b in zip(self.points, self.points[1:]))


def sets_of(way: int, memberships: dict | None, bikes: dict | None) -> int:
    """With no Bike routes given, every way is a Trail's, as a network built for a test is."""
    if not bikes or way not in bikes:
        return SET_TRAIL
    kinds = 0
    for *_, kind in bikes[way]:
        kinds |= KIND_SETS[kind]
    return kinds | (SET_TRAIL if memberships and way in memberships else 0)


def pieces_of(ways: dict, memberships: dict[int, frozenset] | None = None, bikes: dict[int, frozenset] | None = None
              ) -> list[Piece]:
    """Every way split at each of its inner nodes another Trail way, or the same one, passes through, and
    a piece that closes on itself split again in its middle, so a loop is kept without an edge from a
    node to itself."""
    uses: dict[int, int] = {}
    for nodes, _ in ways.values():
        for ref, _, _ in nodes:
            uses[ref] = uses.get(ref, 0) + 1
    pieces = []
    for way_id in sorted(ways):
        nodes, tags = ways[way_id]
        cut = [0] + [i for i in range(1, len(nodes) - 1) if uses[nodes[i][0]] > 1] + [len(nodes) - 1]
        for start, end in zip(cut, cut[1:]):
            part = nodes[start:end + 1]
            closed = part[0][0] == part[-1][0]
            if closed and len(part) < 3:
                continue
            middle = len(part) // 2
            for half in (part[:middle + 1], part[middle:]) if closed else (part,):
                pieces.append(Piece(way_id, half, oneway(tags), SAC_GRADES.get(tags.get("sac_scale", ""), 0),
                                    (memberships or {}).get(way_id, frozenset()), (way_id,),
                                    sets_of(way_id, memberships, bikes), ride(tags), grade(tags.get("mtb:scale")),
                                    grade(tags.get("mtb:scale:uphill")), (bikes or {}).get(way_id, frozenset()),
                                    surface_class(tags)))
    return pieces


def chains(pieces: list[Piece]) -> list[Piece]:
    """docs/phase-5.md § 2's edges: pieces joined through every node where exactly two of them meet and
    both walk alike and carry the same Trails, so a climb is measured across a way's end and only a real
    junction, or a Trail's end (docs/phase-6c.md § 2), cuts it."""
    at: dict[int, list[int]] = {}
    for i, piece in enumerate(pieces):
        at.setdefault(piece.points[0][0], []).append(i)
        at.setdefault(piece.points[-1][0], []).append(i)
    taken = [False] * len(pieces)

    def beyond(node: int, current: int) -> int | None:
        meeting = at[node]
        if len(meeting) != 2:
            return None
        other = meeting[0] if meeting[1] == current else meeting[1]
        return None if taken[other] else other

    out = []
    for first in range(len(pieces)):
        if taken[first]:
            continue
        taken[first] = True
        behind, ahead = [], [pieces[first]]
        current = first
        while (other := beyond((head := (behind[-1] if behind else ahead[0])).points[0][0], current)) is not None:
            piece = pieces[other] if pieces[other].points[-1][0] == head.points[0][0] else pieces[other].turned()
            if not piece.walks_like(head):
                break
            taken[other] = True
            behind.append(piece)
            current = other
        current = first
        while (other := beyond(ahead[-1].points[-1][0], current)) is not None:
            tail = ahead[-1]
            piece = pieces[other] if pieces[other].points[0][0] == tail.points[-1][0] else pieces[other].turned()
            if not piece.walks_like(tail):
                break
            taken[other] = True
            ahead.append(piece)
            current = other
        chain = behind[::-1] + ahead
        closed = len(chain) > 1 and chain[0].points[0][0] == chain[-1].points[-1][0]
        for run in (chain[:-1], chain[-1:]) if closed else (chain,):
            points = run[0].points + [point for piece in run[1:] for point in piece.points[1:]]
            ways = tuple(dict.fromkeys(way for piece in run for way in piece.ways))
            surface = max(run, key=Piece.length).surface if len(run) > 1 else run[0].surface
            out.append(replace(run[0], points=points, ways=ways, surface=surface))
    return out


def measured(points: list[tuple[int, float, float]], relief: Relief | Level
             ) -> tuple[list[tuple[int, float, float]], list[float], list[float], bool]:
    """The points densified, the length from each to the next, their heights, and whether the relief reached
    none of them, which leaves them level. An edge's climb and both its times are taken over these."""
    dense = densify(points)
    heights = fill_heights([relief.height(lat, lon) for _, lat, lon in dense])
    lengths = [distance(a[1], a[2], b[1], b[2]) for a, b in zip(dense, dense[1:])]
    return dense, lengths, heights or [0.0] * len(dense), heights is None


def edges_of(ways: dict, relief: Relief | Level, memberships: dict[int, frozenset] | None = None,
             bikes: dict[int, frozenset] | None = None, constants: RidingConstants = RIDING) -> tuple[list[Edge], int]:
    edges, flat = [], 0
    for chain in chains(pieces_of(ways, memberships, bikes)):
        dense, lengths, heights, level = measured(chain.points, relief)
        flat += level
        kept = [i for i, point in enumerate(dense) if point[0] != 0]
        riding = (riding_seconds(lengths, heights, chain.surface, chain.mtb, chain.mtb_up, constants)
                  if chain.sets & (SET_TOURING | SET_MTB) else (NO_RIDING, NO_RIDING))
        edges.append(Edge(chain.way, [dense[i] for i in kept], [heights[i] for i in kept], sum(lengths),
                          climb(lengths, heights), chain.flags, chain.sac, chain.trails, chain.ways, chain.sets,
                          chain.ride, chain.mtb, chain.mtb_up, chain.bikes, chain.surface,
                          rider_direction(chain.ride, chain.bikes, "touring"),
                          rider_direction(chain.ride, chain.bikes, "mtb"), *riding))
    return edges, flat


def write_network(edges: list[Edge], target: Path) -> tuple[int, int, int]:
    """docs/phase-5.md § 2's binary, little-endian, its arrays one after another, widest first. An edge on no
    Bike route rides in NaN seconds, and its surface class, its longest piece's, means nothing: only a Bike
    route's edges are cut where the class changes."""
    vertices = sorted({ref for edge in edges for ref in (edge.points[0][0], edge.points[-1][0])})
    index = {ref: i for i, ref in enumerate(vertices)}
    where = {}
    for edge in edges:
        for (ref, lat, lon), height in ((edge.points[0], edge.heights[0]), (edge.points[-1], edge.heights[-1])):
            where[ref] = (lat, lon, height)
    point_count = sum(len(edge.points) for edge in edges)

    def e7(value: float) -> int:
        return round(value * 1e7)

    def dm(value: float) -> int:
        return round(value * 10)

    out = io.BytesIO()
    out.write(MAGIC + struct.pack("<HHIII", FORMAT_VERSION, 0, len(vertices), len(edges), point_count))
    out.write(struct.pack(f"<{len(edges)}q", *(edge.way for edge in edges)))
    out.write(struct.pack(f"<{len(vertices)}i", *(e7(where[ref][0]) for ref in vertices)))
    out.write(struct.pack(f"<{len(vertices)}i", *(e7(where[ref][1]) for ref in vertices)))
    out.write(struct.pack(f"<{len(edges)}I", *(index[edge.points[0][0]] for edge in edges)))
    out.write(struct.pack(f"<{len(edges)}I", *(index[edge.points[-1][0]] for edge in edges)))
    firsts, first = [], 0
    for edge in edges:
        firsts.append(first)
        first += len(edge.points)
    out.write(struct.pack(f"<{len(edges)}I", *firsts))
    out.write(struct.pack(f"<{len(edges)}I", *(len(edge.points) for edge in edges)))
    for values in (
        [edge.length for edge in edges],
        [walking_seconds(edge.length, edge.climb.ascent, edge.climb.steep_descent) for edge in edges],
        [walking_seconds(edge.length, edge.climb.descent, edge.climb.steep_ascent) for edge in edges],
        [edge.climb.ascent for edge in edges],
        [edge.climb.descent for edge in edges],
        [edge.riding_forward for edge in edges],
        [edge.riding_backward for edge in edges],
    ):
        out.write(struct.pack(f"<{len(edges)}f", *values))
    out.write(struct.pack(f"<{point_count}i", *(e7(lat) for edge in edges for _, lat, _ in edge.points)))
    out.write(struct.pack(f"<{point_count}i", *(e7(lon) for edge in edges for _, _, lon in edge.points)))
    out.write(struct.pack(f"<{len(vertices)}h", *(dm(where[ref][2]) for ref in vertices)))
    out.write(struct.pack(f"<{point_count}h", *(dm(h) for edge in edges for h in edge.heights)))
    for values in ("flags", "sac", "sets", "ride", "mtb", "mtb_up", "surface", "ride_touring", "ride_mtb"):
        out.write(bytes(getattr(edge, values) for edge in edges))
    target.write_bytes(out.getvalue())
    return len(vertices), len(edges), point_count


def memberships_of(trails: dict[int, TrailLine]) -> dict[int, frozenset]:
    """Each way's Trails, each with whether the way is on that Trail's walk: `backward` is off it only where
    the Trail has a `forward` strand to walk instead."""
    found: dict[int, set] = {}
    for number, line in trails.items():
        off = OFF_WALK | {"backward"} if any(role == "forward" for _, role in line.members) else OFF_WALK
        for way, role in line.members:
            found.setdefault(way, set()).add((number, role not in off))
    return {way: frozenset(pairs) for way, pairs in found.items()}


def strands_of(members: list[int], ways: dict) -> list[tuple[set[int], dict[int, tuple[float, float]]]]:
    """The members joined where they share a node, each strand as its ways and its nodes."""
    strands: list = []
    for way in members:
        held, nodes = {way}, {ref: (lat, lon) for ref, lat, lon in ways[way][0]}
        apart = []
        for strand in strands:
            if strand[1].keys() & nodes.keys():
                held |= strand[0]
                nodes |= strand[1]
            else:
                apart.append(strand)
        strands = [*apart, (held, nodes)]
    return strands


def beside_forward(line: TrailLine, ways: dict) -> set[int]:
    """docs/phase-7a.md § 2: the `backward` members that are the other strand of a `forward` one."""
    def with_role(wanted: str) -> list[int]:
        return [way for way, role in line.members if role == wanted and way in ways]

    def along(held: set[int]) -> list[tuple[float, float]]:
        return [(lat, lon) for way in held for _, lat, lon in densify(ways[way][0])]

    forward = strands_of(with_role("forward"), ways)
    beside: set[int] = set()
    for held, nodes in strands_of(with_role("backward"), ways):
        touched = [strand for strand in forward if strand[1].keys() & nodes.keys()]
        if len(touched) == 1 and len(touched[0][1].keys() & nodes.keys()) >= 2:
            near = along(touched[0][0])
            if all(min(distance(*point, *other) for other in near) <= BESIDE_METRES for point in along(held)):
                beside |= held
    return beside


def bike_memberships_of(bikes: dict[int, TrailLine], ways: dict) -> dict[int, frozenset]:
    """Each way's Bike routes, each with whether the way is on its walk, the sense it is ridden in against the
    way's nodes, and its kind."""
    found: dict[int, set] = {}
    for number, line in bikes.items():
        beside = beside_forward(line, ways)
        for way, role in line.members:
            found.setdefault(way, set()).add(
                (number, role not in OFF_WALK and way not in beside, line.senses.get(way, 0), line.kind))
    return {way: frozenset(routes) for way, routes in found.items()}


@dataclass(frozen=True)
class Step:
    edge: int
    reversed: bool
    breaks: bool = False


@dataclass
class Walk:
    steps: list[Step]
    shape: str
    estimated: bool = False


def trail_edges(edges: list[Edge]) -> dict[tuple[int, bool], list[int]]:
    """For each Trail, the edges on its walk under (id, True) and those off it under (id, False)."""
    found: dict[tuple[int, bool], list[int]] = {}
    for i, edge in enumerate(edges):
        for pair in (*edge.trails, *((number, on_walk) for number, on_walk, *_ in edge.bikes)):
            found.setdefault(pair, []).append(i)
    return found


def walk_of(number: int, line: TrailLine, edges: list[Edge], ways: dict, by_trail: dict) -> Walk | None:
    """docs/phase-6c.md § 2's walk: the Trail's edges as a graph, the members' order only picking which end
    comes first; a loop goes round once from the edge holding the first way, the way that way runs; a
    branch takes the longest path between two ends that takes no edge twice; stretches apart are joined in
    the order and directions that leave the least gap."""
    own = by_trail.get((number, True)) or by_trail.get((number, False))
    if not own:
        return None
    edge_of_way = {way: i for i in own for way in edges[i].ways}
    first_way = next((way for way, _ in line.members if way in edge_of_way), None)
    first_edge = edge_of_way[first_way]

    def start(i: int) -> int:
        return edges[i].points[0][0]

    def end(i: int) -> int:
        return edges[i].points[-1][0]

    ends_at = {ref: (lat, lon) for i in own for ref, lat, lon in (edges[i].points[0], edges[i].points[-1])}

    def where(vertex: int) -> tuple[float, float]:
        return ends_at[vertex]

    at: dict[int, list[int]] = {}
    for i in own:
        at.setdefault(start(i), []).append(i)
        at.setdefault(end(i), []).append(i)

    def other(i: int, vertex: int) -> int:
        return end(i) if start(i) == vertex else start(i)

    def step(i: int, vertex: int) -> Step:
        return Step(i, start(i) != vertex)

    components, placed = [], set()
    for i in sorted(own, key=lambda i: (i != first_edge, i)):
        if i in placed:
            continue
        component, queue = [], [i]
        placed.add(i)
        while queue:
            current = queue.pop()
            component.append(current)
            for vertex in (start(current), end(current)):
                for j in at[vertex]:
                    if j not in placed:
                        placed.add(j)
                        queue.append(j)
        components.append(sorted(component))

    shapes = set()
    stretches = []
    estimated = False
    for component in components:
        members = set(component)
        degree: dict[int, int] = {}
        for i in component:
            for vertex in (start(i), end(i)):
                degree[vertex] = degree.get(vertex, 0) + 1
        if max(degree.values()) <= 2 and all(d == 2 for d in degree.values()):
            shapes.add("loop")
            head = first_edge if first_edge in members else component[0]
            vertex = start(head)
            if head == first_edge and not runs_as_way(edges[head], ways.get(first_way)):
                vertex = end(head)
            steps, current = [], head
            while True:
                steps.append(step(current, vertex))
                vertex = other(current, vertex)
                following = [j for j in at[vertex] if j != current]
                if not following or following[0] == head:
                    break
                current = following[0]
            stretches.append(steps)
            continue
        ends = sorted(vertex for vertex, d in degree.items() if d == 1)
        if max(degree.values()) > 2:
            shapes.add("branch")
        found = None
        if len(component) >= len(degree):
            head = first_edge if first_edge in members else component[0]
            searched = longest(ends or [start(head)], members, at, edges, other, set(ends))
            if searched is not None:
                between, anywhere = searched
                found = anywhere if between is None or anywhere[0] > ENDLESS_LONGER * between[0] else between
                found = found[1:]
            estimated = estimated or found is None
        if found is None:
            sources = ends if len(ends) >= 2 else [farthest(min(degree), members, at, edges, other)[0]]
            best = None
            for source in sources:
                target, length, path = farthest(source, members, at, edges, other, ends if len(ends) >= 2 else None)
                if best is None or length > best[0]:
                    best = (length, source, path)
            found = best[1:]
        source, path = found
        steps, vertex = [], source
        for i in path:
            steps.append(step(i, vertex))
            vertex = other(i, vertex)
        stretches.append(steps)

    def along(steps: list[Step], i: int) -> float | None:
        travelled = 0.0
        for s in steps:
            if s.edge == i:
                return travelled + edges[i].length / 2
            travelled += edges[s.edge].length
        return None

    def turned(steps: list[Step]) -> list[Step]:
        return [Step(s.edge, not s.reversed) for s in reversed(steps)]

    def first_vertex(steps: list[Step]) -> int:
        return end(steps[0].edge) if steps[0].reversed else start(steps[0].edge)

    def last_vertex(steps: list[Step]) -> int:
        return start(steps[-1].edge) if steps[-1].reversed else end(steps[-1].edge)

    def is_loop(steps: list[Step]) -> bool:
        return first_vertex(steps) == last_vertex(steps)

    def directions(steps: list[Step]) -> list[list[Step]]:
        return [steps] if is_loop(steps) else [steps, turned(steps)]

    def joined(lead: list[Step], left: list[list[Step]]) -> tuple[float, list[Step]]:
        walked, gaps = list(lead), 0.0
        while left:
            here = where(last_vertex(walked))
            best = None
            for k, candidate in enumerate(left):
                for steps in directions(candidate):
                    gap = distance(*here, *where(first_vertex(steps)))
                    if best is None or gap < best[0]:
                        best = (gap, k, steps)
            gap, k, steps = best
            gaps += gap
            left = left[:k] + left[k + 1:]
            walked.append(Step(steps[0].edge, steps[0].reversed, True))
            walked.extend(steps[1:])
        return gaps, walked

    def from_first_way(walked: list[Step]) -> float:
        position = along(walked, first_edge)
        return position if position is not None else distance(*where(start(first_edge)), *where(first_vertex(walked)))

    options = [joined(lead, stretches[:k] + stretches[k + 1:])
               for k, stretch in enumerate(stretches) for lead in directions(stretch)]
    least = min(gaps for gaps, _ in options)
    walked = min((walked for gaps, walked in options if gaps <= least + TIED_GAPS), key=from_first_way)
    shape = "gaps" if len(stretches) > 1 else "branch" if "branch" in shapes else "loop" if "loop" in shapes else "line"
    return Walk(walked, shape, estimated)


def sense_along(number: int, edge: Edge, step: Step) -> int:
    """1 where the step goes the way the Bike route is ridden, -1 against it, 0 where the edge has no direction for it."""
    sense = next((sense for route, _, sense, _ in edge.bikes if route == number), 0)
    if not sense:
        return 0
    return 1 if (sense == FORWARD_ONLY) != step.reversed else -1


def turned_steps(steps: list[Step]) -> list[Step]:
    """The steps from the far end, each gap still between the same two stretches."""
    return [Step(step.edge, not step.reversed, i + 1 < len(steps) and steps[i + 1].breaks)
            for i, step in reversed(list(enumerate(steps)))]


def lengths_by_sense(number: int, steps: list[Step], edges: list[Edge]) -> tuple[float, float]:
    """How much of the steps goes with the ride and how much against it."""
    runs = [(sense_along(number, edges[step.edge], step), edges[step.edge].length) for step in steps]
    return sum(length for sense, length in runs if sense > 0), sum(length for sense, length in runs if sense < 0)


def rings_with_the_ride(number: int, steps: list[Step], edges: list[Edge]) -> list[Step]:
    """Each closed stretch of the walk that runs more against the ride than with it, turned: it is the same edges
    from the same vertex to the same vertex, and which way round the search took it was a tie."""
    def first(step: Step) -> int:
        return edges[step.edge].points[-1 if step.reversed else 0][0]

    def last(step: Step) -> int:
        return edges[step.edge].points[0 if step.reversed else -1][0]

    steps = list(steps)
    turned = True
    while turned:
        turned = False
        windows = sorted(((end - begin, begin, end) for begin in range(len(steps)) for end in range(begin + 1, len(steps) + 1)
                          if first(steps[begin]) == last(steps[end - 1])
                          and not any(step.breaks for step in steps[begin + 1:end])))
        for _, begin, end in windows:
            along, against = lengths_by_sense(number, steps[begin:end], edges)
            if against > along:
                ring = turned_steps(steps[begin:end])
                ring[0] = replace(ring[0], breaks=steps[begin].breaks)
                steps[begin:end] = ring
                turned = True
                break
    return steps


def ridden(number: int, walk: Walk, edges: list[Edge], signed: bool) -> tuple[Walk, bool]:
    """docs/phase-7a.md § 2, Ridden one way: the walk starting where its direction leaves, a ring turned only where
    turning the whole walk does not bring it with the ride, and whether it has a direction."""
    def margin(steps: list[Step]) -> float:
        along, against = lengths_by_sense(number, steps, edges)
        return along - against

    if not any(lengths_by_sense(number, walk.steps, edges)):
        return walk, signed
    plain = (walk.steps, turned_steps(walk.steps))
    best = max((*plain, *(rings_with_the_ride(number, steps, edges) for steps in plain)), key=margin)
    along, against = lengths_by_sense(number, best, edges)
    directed = (not along or not against) and along + against >= ONE_WAY_SHARE * sum(edges[s.edge].length for s in best)
    if not signed and not directed:
        return walk, False
    return replace(walk, steps=best), True


def runs_as_way(edge: Edge, way: tuple | None) -> bool:
    """Whether the edge runs the way its way does, read from two of the way's nodes in the edge."""
    if way is None:
        return True
    refs = [ref for ref, _, _ in edge.points]
    nodes = [ref for ref, _, _ in way[0]]
    for a, b in zip(nodes, nodes[1:]):
        if a in refs and b in refs:
            return refs.index(a) < refs.index(b)
    return True


def runs_of(members: set[int], at: dict, edges: list[Edge], other, stops: set[int]) -> list[tuple[int, list[int], int]]:
    """The component's edges joined through every vertex where two of them meet and no walk starts or ends, each
    run as (first vertex, its edges in order, last vertex)."""
    def through(vertex: int) -> bool:
        return vertex not in stops and len(at[vertex]) == 2

    runs, done = [], set()
    for i in sorted(members):
        for vertex in (edges[i].points[0][0], edges[i].points[-1][0]):
            if i in done or through(vertex):
                continue
            done.add(i)
            path, head = [i], other(i, vertex)
            while through(head):
                path.append(next(j for j in at[head] if j != path[-1]))
                done.add(path[-1])
                head = other(path[-1], head)
            runs.append((vertex, path, head))
    return runs


def longest(sources: list[int], members: set[int], at: dict, edges: list[Edge], other, ends: set[int]
            ) -> tuple[tuple | None, tuple] | None:
    """The longest walks over the component's edges from one of the sources that take no edge twice, as (length,
    source, edges): the longest to another of the ends, if any, and the longest to anywhere; ties go to the
    smallest edge indices. None when the component's runs or the search are too many to search through."""
    runs = runs_of(members, at, edges, other, set(sources) | ends)
    if len(runs) > SEARCHED_RUNS:
        return None
    leaving: dict[int, list] = {}
    for run, (first, path, last) in enumerate(runs):
        leaving.setdefault(first, []).append((path[0], run, path, last))
        leaving.setdefault(last, []).append((path[-1], run, path[::-1], first))
    for options in leaving.values():
        options.sort(key=lambda option: option[0])
    best: list = [(-1.0, ()), (-1.0, ())]
    found: list = [None, None]
    walked: list[int] = []
    taken: set[int] = set()
    budget = SEARCH_STEPS

    def visit(source: int, vertex: int, length: float) -> bool:
        nonlocal budget
        budget -= 1
        if budget < 0:
            return False
        if walked:
            for k in (0, 1) if vertex in ends else (1,):
                if length >= best[k][0]:
                    key = (length, tuple(-i for i in sorted(walked)))
                    if key > best[k]:
                        best[k], found[k] = key, (length, source, list(walked))
        for _, run, path, far in leaving[vertex]:
            if run not in taken:
                taken.add(run)
                walked.extend(path)
                reached = length
                for i in path:
                    reached += edges[i].length
                ok = visit(source, far, reached)
                del walked[-len(path):]
                taken.discard(run)
                if not ok:
                    return False
        return True

    for source in sources:
        if not visit(source, source, 0.0):
            return None
    return found[0], found[1]


def farthest(source: int, members: set[int], at: dict, edges: list[Edge], other, targets: list[int] | None = None
             ) -> tuple[int, float, list[int]]:
    """The vertex farthest from the source over the component's edges, of the targets if given, and the path."""
    best_length = {source: 0.0}
    came = {}
    queue = [(0.0, source)]
    while queue:
        length, vertex = heapq.heappop(queue)
        if length > best_length[vertex]:
            continue
        for i in sorted(at[vertex]):
            if i not in members:
                continue
            onward = other(i, vertex)
            reached = length + edges[i].length
            if reached < best_length.get(onward, math.inf):
                best_length[onward] = reached
                came[onward] = (i, vertex)
                heapq.heappush(queue, (reached, onward))
    candidates = [v for v in (targets or best_length) if v in best_length]
    target = max(candidates, key=lambda v: (best_length[v], -v))
    path, vertex = [], target
    while vertex != source:
        i, vertex = came[vertex]
        path.append(i)
    return target, best_length[target], path[::-1]


@dataclass
class TrailRow:
    number: int
    tags: dict[str, str]
    line: TrailLine
    walk: Walk
    long_distance: int | None
    one_way: bool = False
    back: list[Step] | None = None


def own_edges(lines: dict[int, TrailLine], ways: dict, memberships: dict[int, frozenset] | None = None,
              bikes: dict[int, frozenset] | None = None) -> list[Edge]:
    """docs/phase-7a.md § 2: the edges one kind's ways make with the other kind's ways left out."""
    wanted = {way for line in lines.values() for way, _ in line.members}
    return edges_of({way: found for way, found in ways.items() if way in wanted}, Level(), memberships, bikes)[0]


def laid_on(walk: Walk, own: list[Edge], edges: list[Edge], hops: dict) -> Walk:
    """A walk over a kind's own edges as steps of the network's. A loop whose start no network edge ends at
    starts where the edge holding its start does."""
    steps: list[Step] = []
    for own_step in walk.steps:
        refs = [ref for ref, _, _ in own[own_step.edge].points]
        refs = refs[::-1] if own_step.reversed else refs
        held = set(own[own_step.edge].ways)
        breaks = own_step.breaks
        for hop in zip(refs, refs[1:]):
            step = next(found for found in hops[hop] if held.intersection(edges[found.edge].ways))
            if not steps or steps[-1].edge != step.edge:
                steps.append(replace(step, breaks=breaks))
                breaks = False
    if len(steps) > 1 and steps[0].edge == steps[-1].edge:
        steps.pop()
    return replace(walk, steps=steps)


def route_rows(lines: dict[int, TrailLine], long_distance: dict[int, int], edges: list[Edge], ways: dict, own: list[Edge],
               names_of: dict[int, dict[str, str]]) -> list[TrailRow]:
    by_own = trail_edges(own)
    hops: dict[tuple[int, int], list[Step]] = {}
    for i, edge in enumerate(edges):
        for (a, _, _), (b, _, _) in zip(edge.points, edge.points[1:]):
            hops.setdefault((a, b), []).append(Step(i, False))
            hops.setdefault((b, a), []).append(Step(i, True))
    rows = []
    for number, line in lines.items():
        walk = walk_of(number, line, own, ways, by_own)
        if walk is not None:
            rows.append(TrailRow(number, names_of.get(number, {}), line, laid_on(walk, own, edges, hops),
                                 long_distance.get(number)))
    return rows


def bike_rows(bikes: dict[int, TrailLine], names_of: dict[int, dict[str, str]], sections: dict[int, list[int]],
              edges: list[Edge], ways: dict, own: list[Edge]) -> list[TrailRow]:
    rows = route_rows(bikes, outermost(sections), edges, ways, own, names_of)
    by_route = trail_edges(edges)
    for row in rows:
        row.walk, row.one_way = ridden(row.number, row.walk, edges, signed(row.tags))
        route_edges = by_route.get((row.number, True), []) + by_route.get((row.number, False), [])
        line = row.walk.steps
        row.walk = replace(row.walk, steps=with_the_ride(row.line.kind, line, edges, route_edges))
        if not row.one_way:
            row.back = with_the_ride(row.line.kind, turned_steps(line), edges, route_edges)
    return rows


def rider_may(kind: str, edge: Edge, step: Step) -> bool:
    """Whether a rider of the kind may take the step, by the edge's rider direction for that kind."""
    direction = edge.ride_touring if kind == "touring" else edge.ride_mtb
    return direction != (FORWARD_ONLY if step.reversed else BACKWARD_ONLY)


def first_of(step: Step, edges: list[Edge]) -> int:
    return edges[step.edge].points[-1 if step.reversed else 0][0]


def last_of(step: Step, edges: list[Edge]) -> int:
    return edges[step.edge].points[0 if step.reversed else -1][0]


def shortest_ride(kind: str, source: int, target: int, edges: list[Edge], at: dict[int, list[int]],
                  ridden: Counter, replaced: Counter) -> list[Step] | None:
    """The shortest run over the route's own edges, by vertex in at, from source to target that a rider of the kind
    may take, riding no edge the walk rides but where the run replaces it."""
    best = {source: 0.0}
    came: dict[int, Step] = {}
    queue = [(0.0, source)]
    while queue:
        length, vertex = heapq.heappop(queue)
        if vertex == target:
            break
        if length > best[vertex]:
            continue
        for i in at.get(vertex, []):
            if ridden[i] > replaced[i]:
                continue
            for step in (Step(i, False), Step(i, True)):
                if first_of(step, edges) != vertex or not rider_may(kind, edges[i], step):
                    continue
                onward, reached = last_of(step, edges), length + edges[i].length
                if reached < best.get(onward, math.inf):
                    best[onward] = reached
                    came[onward] = step
                    heapq.heappush(queue, (reached, onward))
    if target not in best:
        return None
    path, vertex = [], target
    while vertex != source:
        path.append(came[vertex])
        vertex = first_of(came[vertex], edges)
    return path[::-1]


def reach(steps: list[Step], edges: list[Edge]) -> list[float]:
    """The metres widened by taking the first 0, 1, 2… of steps, as far as WIDEN_METRES allows."""
    metres = [0.0]
    for step in steps:
        if metres[-1] + edges[step.edge].length > WIDEN_METRES:
            break
        metres.append(metres[-1] + edges[step.edge].length)
    return metres


def repair(kind: str, behind: list[Step], run: list[Step], ahead: list[Step], edges: list[Edge],
           at: dict[int, list[int]], ridden: Counter) -> tuple[int, int, list[Step]] | None:
    """docs/phase-7b.md § 6: the run's repair, widened by the fewest metres of the walk behind it (nearest first)
    and ahead of it, no gap between, that lets one fit, as (steps taken behind, steps taken ahead, the repair)."""
    behind = behind[:next((j + 1 for j, step in enumerate(behind) if step.breaks), len(behind))]
    ahead = ahead[:next((j for j, step in enumerate(ahead) if step.breaks), len(ahead))]
    widened = [(b + a, j, m) for j, b in enumerate(reach(behind, edges)) for m, a in enumerate(reach(ahead, edges))]
    for _, taken, onward in sorted(widened):
        replaced = [*behind[:taken][::-1], *run, *ahead[:onward]]
        source, target = first_of(replaced[0], edges), last_of(replaced[-1], edges)
        if source == target:
            found = ridden_round(kind, replaced, edges) or []
        else:
            found = shortest_ride(kind, source, target, edges, at, ridden, Counter(step.edge for step in replaced))
        if found is not None:
            return taken, onward, found
    return None


def ridden_round(kind: str, loop: list[Step], edges: list[Edge]) -> list[Step] | None:
    """docs/phase-7b.md § 6: a loop the walk rides against the rider, ridden round the other way, rather than the
    shortest ride from its junction back to itself, which is none."""
    turned = [replace(step, breaks=False) for step in turned_steps(loop)]
    return turned if all(rider_may(kind, edges[step.edge], step) for step in turned) else None


def with_the_ride(kind: str, steps: list[Step], edges: list[Edge], route_edges: list[int]) -> list[Step]:
    """docs/phase-7b.md § 6: each run of steps a rider of the route's kind may not take replaced by the shortest run
    over the route's own edges that the rider may take and that rides no edge the walk rides elsewhere, the run
    widened along the walk until one fits, or a gap where none does."""
    at: dict[int, list[int]] = {}
    for i in sorted(set(route_edges)):
        at.setdefault(edges[i].points[0][0], []).append(i)
        at.setdefault(edges[i].points[-1][0], []).append(i)
    ridden = Counter(step.edge for step in steps)
    walked: list[Step] = []
    gap = False
    k = 0
    while k < len(steps):
        if rider_may(kind, edges[steps[k].edge], steps[k]):
            run = [steps[k]]
            k += 1
        else:
            end = k + 1
            while end < len(steps) and not steps[end].breaks and not rider_may(kind, edges[steps[end].edge], steps[end]):
                end += 1
            gap = gap or steps[k].breaks
            fit = repair(kind, [] if gap else walked[::-1], steps[k:end], steps[end:], edges, at, ridden)
            if fit is None:
                ridden -= Counter(step.edge for step in steps[k:end])
                gap = True
                k = end
                continue
            taken, onward, run = fit
            if taken:
                gap = walked[-taken].breaks
                ridden -= Counter(step.edge for step in walked[-taken:])
                del walked[-taken:]
            ridden -= Counter(step.edge for step in steps[k:end + onward])
            ridden += Counter(step.edge for step in run)
            k = end + onward
        if run:
            gap = gap or run[0].breaks
            walked.append(replace(run[0], breaks=gap and bool(walked)))
            walked.extend(replace(step, breaks=False) for step in run[1:])
            gap = False
    return walked


def signed(tags: dict[str, str]) -> bool:
    """Whether the relation gives itself a direction: `signed_direction=yes`, or its own `oneway`
    (docs/phase-7b.md § 2)."""
    return tags.get("signed_direction") == "yes" or tags.get("oneway") in ONEWAY_VALUES


def outermost(sections: dict[int, list[int]]) -> dict[int, int]:
    """Each Section's Long-distance Trail: where one holds another, the outermost (docs/phase-6c.md § 2)."""
    parents: dict[int, list[int]] = {}
    for number, members in sections.items():
        for member in members:
            parents.setdefault(member, []).append(number)

    def top(number: int, seen: frozenset) -> int:
        above = [parent for parent in parents.get(number, []) if parent not in seen]
        return min((top(parent, seen | {parent}) for parent in above), default=number)

    return {member: top(member, frozenset({member})) for member in parents if member not in sections}


def against_the_ride(row: TrailRow, edges: list[Edge]) -> list[Step]:
    """The steps of the Bike route's walks a rider of its kind may not take, which docs/phase-7b.md § 6 leaves none of."""
    walks = [row.walk.steps] + ([] if row.one_way else [row.back if row.back is not None else turned_steps(row.walk.steps)])
    return [step for steps in walks for step in steps if not rider_may(row.line.kind, edges[step.edge], step)]


def own_back(row: TrailRow) -> list[Step]:
    """The row's back walk where it is not its walk turned, else nothing."""
    if row.back is None or row.back == turned_steps(row.walk.steps):
        return []
    return row.back


def write_walks(rows: list[TrailRow], target: Path) -> int:
    """docs/phase-6c.md § 2's `trails.edges` in docs/phase-7b.md § 6's format 2, little-endian: each route's walk
    and then its back walk, as steps into the network's edges, a step being its edge's index shifted left two, its
    second bit a gap before it, its first the edge walked from its last point to its first. A back walk of no steps
    is the walk turned."""
    rows = sorted(rows, key=lambda row: row.number)
    walks = [(row.walk.steps, own_back(row)) for row in rows]
    steps = [s for there, back in walks for s in (*there, *back)]
    out = io.BytesIO()
    out.write(WALK_MAGIC + struct.pack("<HHII", WALK_FORMAT_VERSION, 0, len(rows), len(steps)))
    out.write(struct.pack(f"<{len(rows)}q", *(row.number for row in rows)))
    firsts, first = [], 0
    for there, back in walks:
        firsts.append((first, first + len(there)))
        first += len(there) + len(back)
    out.write(struct.pack(f"<{len(rows)}I", *(there for there, _ in firsts)))
    out.write(struct.pack(f"<{len(rows)}I", *(len(there) for there, _ in walks)))
    out.write(struct.pack(f"<{len(rows)}I", *(back for _, back in firsts)))
    out.write(struct.pack(f"<{len(rows)}I", *(len(back) for _, back in walks)))
    out.write(struct.pack(f"<{len(steps)}I", *(s.edge << 2 | s.breaks << 1 | s.reversed for s in steps)))
    target.write_bytes(out.getvalue())
    return len(steps)


def trail_values(row: TrailRow, names_of: dict[int, dict[str, str]], edges: list[Edge], by_trail: dict) -> tuple:
    steps = row.walk.steps
    first, last = edges[steps[0].edge], edges[steps[-1].edge]
    start = first.points[-1] if steps[0].reversed else first.points[0]
    end = last.points[0] if steps[-1].reversed else last.points[-1]
    ascent = sum(edges[s.edge].climb.descent if s.reversed else edges[s.edge].climb.ascent for s in steps)
    descent = sum(edges[s.edge].climb.ascent if s.reversed else edges[s.edge].climb.descent for s in steps)
    points = [(lat, lon) for on_walk in (True, False) for i in by_trail.get((row.number, on_walk), [])
              for _, lat, lon in edges[i].points]
    ends = ends_of(row.tags) or (None, None)
    long_distance = names_of.get(row.long_distance, {}) if row.long_distance else {}
    return (
        row.number, without_colour_code(row.tags.get("name")), without_colour_code(row.tags.get("name:pl")),
        row.tags.get("ref"), row.line.colour, row.line.symbol, *ends, row.long_distance,
        without_colour_code(long_distance.get("name:pl") or long_distance.get("name")),
        round(start[1], 7), round(start[2], 7), round(end[1], 7), round(end[2], 7),
        round(sum(edges[s.edge].length for s in steps), 1), round(ascent, 1), round(descent, 1),
        sum(s.breaks for s in steps),
        round(min(lat for lat, _ in points), 7), round(min(lon for _, lon in points), 7),
        round(max(lat for lat, _ in points), 7), round(max(lon for _, lon in points), 7),
    )


def bike_route_values(row: TrailRow, names_of: dict[int, dict[str, str]], edges: list[Edge], by_route: dict) -> tuple:
    """`trails`' columns less `symbol`, then the Bike route's own."""
    values = trail_values(row, names_of, edges, by_route)

    def own_grade(key: str) -> int | None:
        found = grade(row.tags.get(key))
        return found - 1 if found else None

    return (*values[:5], *values[6:], row.line.kind, row.tags.get("network"), int(row.one_way),
            own_grade("mtb:scale"), own_grade("mtb:scale:uphill"))


def write_places(places: dict, rows: list[TrailRow], bikes: list[TrailRow], names_of: dict[int, dict[str, str]],
                 edges: list[Edge], by_trail: dict, target: Path) -> int:
    target.unlink(missing_ok=True)
    db = sqlite3.connect(target)
    db.executescript(
        """
        PRAGMA page_size = 4096;
        PRAGMA journal_mode = DELETE;
        CREATE TABLE places (
            id INTEGER PRIMARY KEY, kind TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
            region TEXT NOT NULL, name TEXT NOT NULL);
        CREATE VIRTUAL TABLE place_names USING fts4(place, name, folded, notindexed=place, notindexed=name);
        CREATE TABLE trails (
            id INTEGER PRIMARY KEY, name TEXT, name_pl TEXT, ref TEXT, colour TEXT NOT NULL, symbol TEXT,
            from_place TEXT, to_place TEXT, long_distance INTEGER, long_distance_name TEXT,
            start_lat REAL NOT NULL, start_lon REAL NOT NULL, end_lat REAL NOT NULL, end_lon REAL NOT NULL,
            length REAL NOT NULL, ascent REAL NOT NULL, descent REAL NOT NULL, gaps INTEGER NOT NULL,
            min_lat REAL NOT NULL, min_lon REAL NOT NULL, max_lat REAL NOT NULL, max_lon REAL NOT NULL);
        CREATE VIRTUAL TABLE trail_names USING fts4(trail, name, folded, notindexed=trail, notindexed=name);
        CREATE TABLE bike_routes (
            id INTEGER PRIMARY KEY, name TEXT, name_pl TEXT, ref TEXT, colour TEXT NOT NULL,
            from_place TEXT, to_place TEXT, long_distance INTEGER, long_distance_name TEXT,
            start_lat REAL NOT NULL, start_lon REAL NOT NULL, end_lat REAL NOT NULL, end_lon REAL NOT NULL,
            length REAL NOT NULL, ascent REAL NOT NULL, descent REAL NOT NULL, gaps INTEGER NOT NULL,
            min_lat REAL NOT NULL, min_lon REAL NOT NULL, max_lat REAL NOT NULL, max_lon REAL NOT NULL,
            kind TEXT NOT NULL, network TEXT, one_way INTEGER NOT NULL, mtb_scale INTEGER, mtb_scale_uphill INTEGER);
        CREATE VIRTUAL TABLE bike_route_names USING fts4(bike_route, name, folded, notindexed=bike_route, notindexed=name);
        """
    )
    order = sorted(places, key=lambda key: ({"n": 0, "w": 1, "r": 2}[key[0]], key[1]))
    with db:
        for number, key in enumerate(order, start=1):
            place = places[key]
            db.execute("INSERT INTO places VALUES (?, ?, ?, ?, ?, ?)",
                       (number, place.kind, round(place.lat, 7), round(place.lon, 7), place.region, place.names[0]))
            for name in place.names:
                db.execute("INSERT INTO place_names (place, name, folded) VALUES (?, ?, ?)", (number, name, fold(name)))
        for row in rows:
            db.execute("INSERT INTO trails VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                       trail_values(row, names_of, edges, by_trail))
            for name in dict.fromkeys(filter(None, (without_colour_code(row.tags.get(key)) for key in ("name", "name:pl")))):
                db.execute("INSERT INTO trail_names (trail, name, folded) VALUES (?, ?, ?)", (row.number, name, fold(name)))
        for row in bikes:
            values = bike_route_values(row, names_of, edges, by_trail)
            db.execute(f"INSERT INTO bike_routes VALUES ({', '.join('?' * len(values))})", values)
            for name in dict.fromkeys(filter(None, (without_colour_code(row.tags.get(key)) for key in ("name", "name:pl")))):
                db.execute("INSERT INTO bike_route_names (bike_route, name, folded) VALUES (?, ?, ?)",
                           (row.number, name, fold(name)))
    db.execute("INSERT INTO place_names (place_names) VALUES ('optimize')")
    db.execute("INSERT INTO trail_names (trail_names) VALUES ('optimize')")
    db.execute("INSERT INTO bike_route_names (bike_route_names) VALUES ('optimize')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(order)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--relief", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--region", nargs=3, action="append", required=True, metavar=("ID", "PBF", "TRAILS"))
    args = parser.parse_args()

    relief = Relief(args.relief)
    listed = [Path(listed).read_text() for _, _, listed in args.region]
    trails, sections = merge_trails([parse_trails(text) for text in listed])
    bikes, bike_sections = merge_trails([parse_bike_routes(text) for text in listed])
    wanted_ways = {way for line in (*trails.values(), *bikes.values()) for way, _ in line.members}
    wanted_relations = set(trails) | set(sections) | set(bikes) | set(bike_sections)
    ways, names_of, places = read_cuts([(region, pbf, wanted_ways, wanted_relations) for region, pbf, _ in args.region])
    memberships, bike_memberships = memberships_of(trails), bike_memberships_of(bikes, ways)
    edges, flat = edges_of(ways, relief, memberships, bike_memberships)
    by_route = trail_edges(edges)
    rows = route_rows(trails, outermost(sections), edges, ways, own_edges(trails, ways, memberships), names_of)
    ridden_rows = bike_rows(bikes, names_of, bike_sections, edges, ways, own_edges(bikes, ways, None, bike_memberships))
    for label, found, lines, of in (("Trails", rows, trails, "Long-distance Trails"),
                                    ("Bike routes", ridden_rows, bikes, "parents")):
        shapes: dict[str, int] = {"no way in any cut": len(lines) - len(found)}
        for row in found:
            shapes[row.walk.shape] = shapes.get(row.walk.shape, 0) + 1
        log(f"{len(found)} {label}, {sum(1 for row in found if row.long_distance)} of them Sections of "
            f"{len({row.long_distance for row in found if row.long_distance})} {of}; walks: "
            + ", ".join(f"{count} {shape}" for shape, count in sorted(shapes.items()))
            + f"; {sum(row.walk.estimated for row in found)} of them past the longest-path search, taken by the farthest ends instead")
    log(f"{sum(row.one_way for row in ridden_rows)} Bike routes ridden one way, {sum(bool(own_back(row)) for row in ridden_rows)} "
        f"with a back walk of their own; {sum(1 for row in ridden_rows if against_the_ride(row, edges))} with a walk "
        f"against the rider")
    args.out.mkdir(parents=True, exist_ok=True)
    nodes, edge_count, points = write_network(edges, args.out / "trails.network")
    log(f"trails.network: {nodes} nodes, {edge_count} edges, {points} points, "
        f"{(args.out / 'trails.network').stat().st_size:,} bytes; {relief.missing} points beyond the relief, "
        f"{flat} edges with no height at all")
    classes = [sum(edge.surface == of for edge in edges) for of in (PAVED, UNPAVED, ROUGH)]
    kinds = [sum(edge.sets & of == of for edge in edges) for of in (SET_TOURING, SET_MTB, SET_TOURING | SET_MTB)]
    log(f"surfaces: {classes[0]} edges paved, {classes[1]} unpaved, {classes[2]} rough; Bike route edges: {kinds[0]} "
        f"Touring, {kinds[1]} MTB, {kinds[2]} both; one way for a rider: {sum(edge.ride_touring > 0 for edge in edges)} "
        f"under Touring, {sum(edge.ride_mtb > 0 for edge in edges)} under MTB")
    steps = write_walks([*rows, *ridden_rows], args.out / "trails.edges")
    log(f"trails.edges: {len(rows) + len(ridden_rows)} routes, {steps} steps, {(args.out / 'trails.edges').stat().st_size:,} bytes")
    count = write_places(places, rows, ridden_rows, names_of, edges, by_route, args.out / "places.sqlite")
    log(f"places.sqlite: {count} places, {len(rows)} Trails and {len(ridden_rows)} Bike routes, "
        f"{(args.out / 'places.sqlite').stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
