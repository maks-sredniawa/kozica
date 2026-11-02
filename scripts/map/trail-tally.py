#!/usr/bin/env python3
"""What the Regions' Trail relations carry to title them, phase 6c's gate (docs/phase-6c.md § 1), and
the same for Bike routes with the largest union `count` on a way, phase 7a's R2 (docs/phase-7a.md § 1).

    python3 scripts/map/trail-tally.py

Reads every Region's merged cut that build-map-data.py leaves in map-data/merged/pin/, and for the
union its map-data/archives/<region>.trails, which are the Trails as the profile decided. A Trail is a
`route=hiking` relation, as KozicaProfile.java decides, with a way in a cut; each is counted once
across the union, since the cuts overlap. A Long-distance Trail is a relation with a Trail among its
members, which are then its Sections. The rules are § 2's title rule: a name, then `from` and `to`,
then the ends a mapper wrote as "A - B" in one of ENDS_KEYS, the Slovak and Czech conventions. A Bike
route is phase 7a § 2's reading of `route=bicycle` and `route=mtb`, and its title rule puts the number
after the name; one with neither of the rest takes the places nearest its ends, which only the build
can tell. Needs pyosmium 4.2+.
"""

import argparse
import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
REGIONS = ROOT / "scripts/regions"
CUTS = ROOT / "map-data/merged/pin"
ARCHIVES = ROOT / "map-data/archives"
ENDS_KEYS = ("description", "osmc:name", "note:cs", "note")
RULES = ("name", "from – to", *ENDS_KEYS, "neither")
COLOUR_CODE = re.compile(r"^\[[^\]]{1,3}\]\s*")
DASH = re.compile(r"\s+[-–—]\s+")
LONGEST_END = 40
BIKE_KINDS = {"bicycle": "touring", "mtb": "mtb"}
BIKE_RULES = ("name", "ref", "from – to", *ENDS_KEYS, "neither")
HAS_SECTIONS = ("icn", "ncn")
COLOUR_ALIASES = {
    "czerwony": "red", "#ff0000": "red",
    "niebieski": "blue", "#0000ff": "blue",
    "zielony": "green", "#00ff00": "green",
    "żółty": "yellow", "#ffff00": "yellow",
    "czarny": "black", "#000000": "black",
}


@dataclass
class Relation:
    tags: dict[str, str]
    ways: list[int]
    relations: list[int]
    regions: set[str] = field(default_factory=set)
    present: set[int] = field(default_factory=set)


def name(tags: dict[str, str]) -> str | None:
    """KČT's names lead with the colour's letter, `[Ž] A - B`, which the sheet shows already."""
    found = tags.get("name:pl") or tags.get("name")
    return COLOUR_CODE.sub("", found, count=1).strip() or None if found else None


def written_ends(value: str) -> tuple[str, str] | None:
    """Place names between spaced dashes, the first and last the ends. A mapper's prose in a note
    fails the length, so it is not read as ends."""
    places = [place.strip() for place in DASH.split(COLOUR_CODE.sub("", value.strip(), count=1))]
    if len(places) >= 2 and all(0 < len(place) <= LONGEST_END and ";" not in place for place in places):
        return places[0], places[-1]
    return None


def rule(tags: dict[str, str]) -> str:
    if name(tags):
        return "name"
    if tags.get("from") and tags.get("to"):
        return "from – to"
    return next((key for key in ENDS_KEYS if written_ends(tags.get(key, ""))), "neither")


def sample_title(rid: int, tags: dict[str, str]) -> str:
    match rule(tags):
        case "name":
            return name(tags)
        case "from – to":
            return f"{tags['from']} – {tags['to']}"
        case "neither":
            pass
        case key:
            return " – ".join(written_ends(tags[key]))
    marks = " ".join(f"{key}={tags[key]}" for key in ("ref", "osmc:symbol", "colour") if key in tags)
    return f"r{rid} {marks}".rstrip()


def read(regions: list[str], cuts: Path) -> tuple[dict[int, Relation], set[int]]:
    import osmium

    relations: dict[int, Relation] = {}
    every_way: set[int] = set()
    for region in regions:
        present: set[int] = set()
        candidates: dict[int, Relation] = {}
        for obj in osmium.FileProcessor(str(cuts / f"{region}.osm.pbf"), osmium.osm.WAY | osmium.osm.RELATION):
            if obj.is_way():
                present.add(obj.id)
            elif "route" in obj.tags or obj.tags.get("type") == "superroute":
                candidates[obj.id] = Relation(
                    dict(obj.tags),
                    [m.ref for m in obj.members if m.type == "w"],
                    [m.ref for m in obj.members if m.type == "r"],
                )
        for rid, relation in candidates.items():
            if relation.ways and not any(way in present for way in relation.ways):
                continue
            kept = relations.setdefault(rid, relation)
            kept.regions.add(region)
            kept.present.update(way for way in relation.ways if way in present)
        every_way |= present
    return relations, every_way


def trails_of(relations: dict[int, Relation]) -> dict[int, Relation]:
    return {rid: r for rid, r in relations.items()
            if r.tags.get("type") == "route" and r.tags.get("route") == "hiking" and r.regions and r.ways}


def long_distance_of(relations: dict[int, Relation], trails: dict[int, Relation]) -> dict[int, Relation]:
    return {rid: r for rid, r in relations.items() if any(member in trails for member in r.relations)}


def bike_rule(tags: dict[str, str]) -> str:
    return "ref" if not name(tags) and tags.get("ref") else rule(tags)


def bike_sample_title(rid: int, tags: dict[str, str]) -> str:
    return tags["ref"] if bike_rule(tags) == "ref" else sample_title(rid, tags)


def marking_key(tags: dict[str, str]) -> str:
    """Two colours outside the five are two markings, though the tiles write both as `other`."""
    colour = tags.get("colour", "").strip().lower()
    return COLOUR_ALIASES.get(colour, colour)


@dataclass
class BikeRoute:
    tags: dict[str, str]
    kind: str
    regions: set[str]


class BikeMembership:
    """Which relations are Bike routes, and whose ways are whose (docs/phase-7a.md § 2)."""

    def __init__(self, relations: dict[int, Relation]) -> None:
        self.bikes = {rid: r for rid, r in relations.items() if r.tags.get("route") in BIKE_KINDS}
        self.pieces = {rid: self._pieces_of(rid) for rid in self.bikes}
        self.parents: dict[int, list[int]] = {}
        for rid, pieces in self.pieces.items():
            for piece in pieces:
                self.parents.setdefault(piece, []).append(rid)

    def members(self, rid: int) -> list[int]:
        return [member for member in self.bikes[rid].relations if member in self.bikes]

    def _pieces_of(self, rid: int) -> list[int]:
        relation = self.bikes[rid]
        marking = marking_key(relation.tags)
        if not marking or relation.tags.get("network") in HAS_SECTIONS:
            return []
        return [member for member in self.members(rid) if marking_key(self.bikes[member].tags) == marking]

    def _below(self, rid: int) -> set[int]:
        found: set[int] = set()
        stack = [rid]
        while stack:
            current = stack.pop()
            if current not in found:
                found.add(current)
                stack.extend(self.pieces[current])
        return found

    def is_route(self, rid: int) -> bool:
        return rid not in self.parents and any(self.bikes[below].present for below in self._below(rid))

    def owners(self, rid: int) -> set[int]:
        found: set[int] = set()
        seen: set[int] = set()
        stack = [rid]
        while stack:
            current = stack.pop()
            if current in seen:
                continue
            seen.add(current)
            if self.is_route(current):
                found.add(current)
            stack.extend(self.parents.get(current, []))
        return found

    def routes(self) -> dict[int, BikeRoute]:
        routes = {}
        for rid, relation in self.bikes.items():
            if self.is_route(rid):
                regions = set()
                for below in self._below(rid):
                    if self.bikes[below].present:
                        regions |= self.bikes[below].regions
                routes[rid] = BikeRoute(relation.tags, BIKE_KINDS[relation.tags["route"]], regions)
        return routes

    def holders(self) -> list[int]:
        return sorted(rid for rid in self.bikes if self.members(rid))

    def ways_by_route(self) -> dict[int, set[int]]:
        """Each way's Bike routes: a piece's ways write its parent's id, a parent's own ways its own."""
        ways: dict[int, set[int]] = {}
        for rid, relation in self.bikes.items():
            owners = self.owners(rid)
            if owners:
                for way in relation.present:
                    ways.setdefault(way, set()).update(owners)
        return ways


def trails_by_way(regions: list[str], archives: Path, every_way: set[int]) -> dict[int, set[int]]:
    """From the `trail` lines KozicaProfile.Membership.lines() writes."""
    ways: dict[int, set[int]] = {}
    for region in regions:
        for line in (archives / f"{region}.trails").read_text(encoding="utf-8").splitlines():
            fields = line.split("\t")
            if fields[0] != "trail":
                continue
            for member in fields[4].split():
                way = int(member.split("/")[0])
                if way in every_way:
                    ways.setdefault(way, set()).add(int(fields[1]))
    return ways


def share(count: int, total: int) -> str:
    return f"{count:6,} {100 * count / total:5.1f} %" if total else f"{count:6,}     – "


def print_counts(label: str, routes: dict, rules: tuple[str, ...] = RULES, titling=rule) -> None:
    counts = Counter(titling(r.tags) for r in routes.values())
    titled = len(routes) - counts["neither"]
    cells = "".join(f"{counts[key]:>12,}" for key in rules)
    print(f"{label:<28}{len(routes):>7,}{cells}   {share(titled, len(routes))}")


def evenly(items: list, count: int) -> list:
    if len(items) <= count:
        return items
    return [items[i * len(items) // count] for i in range(count)]


def print_trails(regions: list[str], relations: dict[int, Relation], samples: int) -> None:
    trails = trails_of(relations)
    long_distance = long_distance_of(relations, trails)

    print("Trails by what titles them\n")
    print(f"{'':<28}{'Trails':>7}{''.join(f'{key:>12}' for key in RULES)}   titled by tags")
    print_counts("union", trails)
    for region in regions:
        print_counts(region, {rid: r for rid, r in trails.items() if region in r.regions})

    for key in RULES:
        found = sorted(rid for rid, r in trails.items() if rule(r.tags) == key)
        print(f"\n{key}: {len(found):,}, {min(samples, len(found))} evenly by id")
        for rid in evenly(found, samples):
            print(f"  {sample_title(rid, trails[rid].tags)}")

    sections = {member for r in long_distance.values() for member in r.relations if member in trails}
    named = sorted(rid for rid, r in long_distance.items() if name(r.tags))
    kinds = Counter(r.tags.get("type", "?") for r in long_distance.values())
    print(f"\nLong-distance Trails with Sections in the Regions: {len(long_distance):,}"
          f" ({', '.join(f'type={k} {n}' for k, n in sorted(kinds.items()))}),"
          f" {len(named):,} named; {len(sections):,} Sections")
    for rid in named:
        r = long_distance[rid]
        count = sum(member in trails for member in r.relations)
        print(f"  r{rid} {name(r.tags)}: {count} of {len(r.relations)} member relations are Sections here")
    for rid in sorted(set(long_distance) - set(named)):
        print(f"  {sample_title(rid, long_distance[rid].tags)} (no name)")

    untitled = {rid for rid, r in trails.items() if rule(r.tags) == "neither"}
    under_named = {member for rid in named for member in long_distance[rid].relations} & untitled
    print(f"\nOf the {len(untitled):,} Trails with neither, {len(under_named):,} are Sections of a named"
          " Long-distance Trail, whose name the sheet shows above their title")


def print_bike_routes(regions: list[str], membership: BikeMembership, samples: int) -> None:
    routes = membership.routes()
    kinds = sorted(set(BIKE_KINDS.values()), reverse=True)

    print("\n\nBike routes by what titles them\n")
    print(f"{'':<28}{'Routes':>7}{''.join(f'{key:>12}' for key in BIKE_RULES)}   titled by tags")
    print_counts("union", routes, BIKE_RULES, bike_rule)
    for scope, label in ((None, "union"), *((region, region) for region in regions)):
        for kind in kinds:
            print_counts(f"{label} {kind}",
                         {rid: r for rid, r in routes.items() if r.kind == kind and (not scope or scope in r.regions)},
                         BIKE_RULES, bike_rule)

    for kind in kinds:
        for key in BIKE_RULES:
            found = sorted(rid for rid, r in routes.items() if r.kind == kind and bike_rule(r.tags) == key)
            print(f"\n{kind}, {key}: {len(found):,}, {min(samples, len(found))} evenly by id")
            for rid in evenly(found, samples):
                print(f"  {bike_sample_title(rid, routes[rid].tags)}")

    neither = [r for r in routes.values() if bike_rule(r.tags) == "neither"]
    print(f"\nOf the {len(neither):,} with neither, for the places nearest their ends:"
          f" {sum(bool(marking_key(r.tags)) for r in neither):,} carry a colour")

    holders = membership.holders()
    print(f"\nBike routes holding Bike routes: {len(holders)}")
    for rid in holders:
        relation = membership.bikes[rid]
        pieces = membership.pieces[rid]
        sections = len(membership.members(rid)) - len(pieces)
        row = "a row" if rid in routes else "no row"
        print(f"  r{rid} {relation.tags.get('network', '-')} {marking_key(relation.tags) or 'no colour'}"
              f" {name(relation.tags) or relation.tags.get('ref', '')}: {len(pieces)} pieces, {sections} Sections,"
              f" {len(relation.present):,} ways of its own, {row}")


def print_union_count(trails: dict[int, set[int]], bikes: dict[int, set[int]]) -> None:
    union = {way: trails.get(way, set()) | bikes.get(way, set()) for way in trails.keys() | bikes.keys()}
    largest = max(len(routes) for routes in union.values())
    print("\nRoutes on a way, ways by count\n")
    print(f"{'count':>5}{'Trails alone':>14}{'union':>10}")
    alone = Counter(len(routes) for routes in trails.values())
    together = Counter(len(routes) for routes in union.values())
    for count in range(1, largest + 1):
        print(f"{count:>5}{alone[count]:>14,}{together[count]:>10,}")
    print(f"\nLargest union count: {largest}")
    for way in sorted(way for way, routes in union.items() if len(routes) == largest)[:10]:
        print(f"  w{way}: {len(trails.get(way, ())):,} Trails, {len(bikes.get(way, ())):,} Bike routes")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cuts", type=Path, default=CUTS, help="the directory holding <region>.osm.pbf")
    parser.add_argument("--archives", type=Path, default=ARCHIVES, help="the directory holding <region>.trails")
    parser.add_argument("--samples", type=int, default=20)
    args = parser.parse_args()

    regions = sorted(path.stem for path in REGIONS.glob("*.poly"))
    relations, every_way = read(regions, args.cuts)
    membership = BikeMembership(relations)

    print_trails(regions, relations, args.samples)
    print_bike_routes(regions, membership, args.samples)
    print_union_count(trails_by_way(regions, args.archives, every_way), membership.ways_by_route())


if __name__ == "__main__":
    main()
