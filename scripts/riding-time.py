#!/usr/bin/env python3
"""docs/phase-7b.md § 1's gate: build-network.py's riding time against R1's references.

    python3 scripts/riding-time.py --split 20261111
    map-data/network-venv/bin/python scripts/riding-time.py --data map-data/pin-4965 \\
        --relief relief/src/main/assets/relief.pmtiles --fit tune --judge judge

--data holds `merged/<region>.osm.pbf` and `archives/<region>.trails` at one pin; the edges are built from them
exactly as build-network.py builds them. --cache keeps what the gate reads of them, so a second run skips the build.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import runpy
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REFERENCES = HERE / "riding-references.json"
SETS = ("tune", "judge", "reserve")
DIRECTIONS = ("walk's", "reverse")
MEDIAN_MARK = 0.20
WORST_MARK = 0.40
LENGTH_MARK = 0.02
WALKING_KMH = 4.0
DESCENT_GAIN = 1.1

# docs/cycling-research.md § 2, each constant's bounds the spread of the published routers.
BOUNDS = {
    "flat paved": (15.0, 25.0),
    "flat unpaved": (6.0, 16.0),
    "flat rough": (3.0, 13.0),
    "descent cap paved": (16.5, 45.0),
    "descent cap unpaved": (6.6, 45.0),
    "descent cap rough": (3.3, 45.0),
    "seconds per metre up": (0.5, 8.8),
    "descent factor": (1.1, 2.2),
    "pushing factor": (WALKING_KMH / 5.1, 1.0),
    "S2 cap": (4.0, 10.0),
    "S3 cap": (4.0, 6.0),
}
SOURCES = {
    "flat paved": "OSRM's default 15 km/h to Valhalla's road bike 25",
    "flat unpaved": "OSRM's gravel 6 km/h to GraphHopper's MTB dirt and Valhalla's mountain bike 16",
    "flat rough": "OSRM's mud and sand 3 km/h to its path 13",
    "descent cap paved": "GraphHopper's ×1.10 on the class's flat to BRouter's maxSpeed 45 km/h",
    "descent cap unpaved": "GraphHopper's ×1.10 on the class's flat to BRouter's 45 km/h",
    "descent cap rough": "GraphHopper's ×1.10 on the class's flat to BRouter's 45 km/h",
    "seconds per metre up": "GraphHopper's ×0.9 at 4 % (0.56 s/m) to BRouter's 90 kg at 100 W, m·g/P = 8.8 s/m",
    "descent factor": "GraphHopper's ×1.10 to Valhalla's ×2.2",
    "pushing factor": "Valhalla's 5.1 km/h dismount to everyone else's 4 km/h push, against walking's 4 km/h",
    "S2 cap": "GraphHopper's 4 km/h to our own 10",
    "S3 cap": "GraphHopper's 4 km/h to our own 6",
}


def bounds_of(name: str, values: dict[str, float]) -> tuple[float, float]:
    """A descent cap never below its class's flat at GraphHopper's ×1.10, so a descent is never the slower."""
    lo, hi = BOUNDS[name]
    if name.startswith("descent cap "):
        lo = DESCENT_GAIN * values["flat " + name.removeprefix("descent cap ")]
    return lo, hi


def load_network() -> dict:
    return runpy.run_path(str(HERE / "build-network.py"), run_name="riding-time")


def load_references(path: Path = REFERENCES) -> dict:
    return json.loads(path.read_text())


def split(seed: int, strata: list[dict], replaced: dict | None = None) -> dict[str, list[int]]:
    """R1's deal: one generator, the strata in order, each stratum's ids sorted, shuffled, and dealt tune, judge
    and reserve; then the one replacement R1 records."""
    rng = random.Random(seed)
    dealt: dict[str, list[int]] = {name: [] for name in SETS}
    for stratum in strata:
        numbers = sorted(stratum["routes"])
        rng.shuffle(numbers)
        for name, number in zip(SETS, numbers):
            dealt[name].append(number)
    if replaced:
        held = dealt[replaced["set"]]
        held[held.index(replaced["out"])] = replaced["in"]
    return dealt


def reference_seconds(direction: dict) -> float:
    """docs/phase-7b.md § 1: the midpoint of mapy.com's time and Komoot's."""
    return (direction["mapy"] + direction["komoot"]) / 2


def error(estimate: float, reference: float) -> float:
    return abs(estimate - reference) / reference


def log_error(estimate: float, reference: float) -> float:
    return math.log(estimate / reference)


def constants_of(values: dict[str, float], network: dict):
    return network["RidingConstants"](
        flat=(values["flat paved"], values["flat unpaved"], values["flat rough"]),
        descent_cap=(values["descent cap paved"], values["descent cap unpaved"], values["descent cap rough"]),
        seconds_per_metre_up=values["seconds per metre up"],
        descent_factor=values["descent factor"],
        pushing_factor=values["pushing factor"],
        mtb_caps=(values["S2 cap"], values["S3 cap"], network["RIDING"].mtb_caps[2]),
    )


def values_of(constants) -> dict[str, float]:
    return {
        "flat paved": constants.flat[0], "flat unpaved": constants.flat[1], "flat rough": constants.flat[2],
        "descent cap paved": constants.descent_cap[0], "descent cap unpaved": constants.descent_cap[1],
        "descent cap rough": constants.descent_cap[2], "seconds per metre up": constants.seconds_per_metre_up,
        "descent factor": constants.descent_factor, "pushing factor": constants.pushing_factor,
        "S2 cap": constants.mtb_caps[0], "S3 cap": constants.mtb_caps[1],
    }


def build(network: dict, data: Path, relief_path: Path, numbers: list[int]) -> dict:
    """The edges at the pin in `data`, built by build-network.py's own functions as its main() builds them, and
    for each Bike route asked for its walk, its line and, for every edge on it, what riding_seconds() reads."""
    relief = network["Relief"](relief_path)
    regions = sorted(path.name.removesuffix(".osm.pbf") for path in (data / "merged").glob("*.osm.pbf"))
    listed = [(data / "archives" / f"{region}.trails").read_text() for region in regions]
    trails, sections = network["merge_trails"]([network["parse_trails"](text) for text in listed])
    bikes, bike_sections = network["merge_trails"]([network["parse_bike_routes"](text) for text in listed])
    wanted_ways = {way for line in (*trails.values(), *bikes.values()) for way, _ in line.members}
    wanted_relations = set(trails) | set(sections) | set(bikes) | set(bike_sections)
    ways, names_of, _ = network["read_cuts"](
        [(region, str(data / "merged" / f"{region}.osm.pbf"), wanted_ways, wanted_relations) for region in regions])
    memberships, bike_memberships = network["memberships_of"](trails), network["bike_memberships_of"](bikes, ways)
    edges, _ = network["edges_of"](ways, relief, memberships, bike_memberships)
    own = network["own_edges"](bikes, ways, None, bike_memberships)
    rows = network["bike_rows"]({number: bikes[number] for number in numbers}, names_of, bike_sections, edges, ways, own)
    found: dict = {"routes": {}, "edges": {}}
    for row in rows:
        steps = row.walk.steps
        line = []
        for step in steps:
            points = [(lat, lon) for _, lat, lon in edges[step.edge].points]
            line += (points[::-1] if step.reversed else points)[1 if line else 0:]
            if str(step.edge) not in found["edges"]:
                edge = edges[step.edge]
                _, lengths, heights, _ = network["measured"](edge.points, relief)
                riding = network["riding_seconds"](lengths, heights, edge.surface, edge.mtb, edge.mtb_up)
                if not all(math.isclose(a, b, rel_tol=1e-9) for a, b in zip(riding, (edge.riding_forward, edge.riding_backward))):
                    sys.exit(f"edge {step.edge}: re-measured riding seconds {riding} are not the build's")
                found["edges"][str(step.edge)] = {"lengths": lengths, "heights": heights, "surface": edge.surface,
                                                  "mtb": edge.mtb, "mtb_up": edge.mtb_up}
        found["routes"][str(row.number)] = {"steps": [[step.edge, step.reversed] for step in steps],
                                            "length": sum(edges[step.edge].length for step in steps),
                                            "one_way": row.one_way, "line": line}
    return found


def along(line: list[tuple[float, float]], point: tuple[float, float], network: dict) -> int:
    return min(range(len(line)), key=lambda i: network["distance"](*line[i], *point))


def same_way(route: dict, waypoints: list, network: dict) -> tuple[bool, float]:
    """Whether this pin's walk runs as 7a's did, from where R1's inner waypoints fall along it, and how far its
    start lies from R1's. A loop starts and ends at one point, so the start alone cannot tell."""
    line = route["line"]
    places = [along(line, point, network) for point in waypoints[1:-1]]
    rising = sum(b > a for a, b in zip(places, places[1:]))
    falling = sum(b < a for a, b in zip(places, places[1:]))
    start = network["distance"](*line[0 if rising >= falling else -1], *waypoints[0])
    return rising >= falling, start


def directions(references: dict, built: dict, numbers: list[int], network: dict) -> list[dict]:
    """Every direction R1 read of these routes, as steps over this pin's edges."""
    out = []
    for number in numbers:
        reference = references["routes"][str(number)]
        route = built["routes"].get(str(number))
        if route is None:
            sys.exit(f"r{number} has no walk at this pin")
        same, start = same_way(route, reference["waypoints"], network)
        for direction, readings in reference["directions"].items():
            forward = (direction == "walk's") == same
            steps = route["steps"] if forward else [[edge, not turned] for edge, turned in route["steps"]]
            out.append({"number": number, "name": reference["name"], "direction": direction, "steps": steps,
                        "reference": reference_seconds(readings), "length": route["length"],
                        "length_7a": reference["length"], "start_off": start, "turned": not same})
    return out


def estimate(direction: dict, timed: dict[str, tuple[float, float]]) -> float:
    return sum(timed[str(edge)][1 if turned else 0] for edge, turned in direction["steps"])


def timings(edges: dict, constants, network: dict) -> dict[str, tuple[float, float]]:
    return {key: network["riding_seconds"](edge["lengths"], edge["heights"], edge["surface"], edge["mtb"], edge["mtb_up"],
                                           constants) for key, edge in edges.items()}


def loss(rows: list[dict], edges: dict, constants, network: dict) -> float:
    timed = timings(edges, constants, network)
    return sum(log_error(estimate(row, timed), row["reference"]) ** 2 for row in rows)


def nelder_mead(f, start: list[float], step: float = 0.3, iterations: int = 3000) -> list[float]:
    n = len(start)
    simplex = [list(start)] + [[x + (step if i == j else 0.0) for j, x in enumerate(start)] for i in range(n)]
    scores = [f(point) for point in simplex]
    for _ in range(iterations):
        order = sorted(range(n + 1), key=lambda i: scores[i])
        simplex, scores = [simplex[i] for i in order], [scores[i] for i in order]
        if scores[-1] - scores[0] < 1e-12:
            break
        centre = [sum(point[j] for point in simplex[:-1]) / n for j in range(n)]

        def toward(t: float) -> list[float]:
            return [c + t * (w - c) for c, w in zip(centre, simplex[-1])]

        reflected = toward(-1.0)
        score = f(reflected)
        if score < scores[0]:
            expanded = toward(-2.0)
            expanded_score = f(expanded)
            simplex[-1], scores[-1] = (expanded, expanded_score) if expanded_score < score else (reflected, score)
        elif score < scores[-2]:
            simplex[-1], scores[-1] = reflected, score
        else:
            contracted = toward(0.5 if score >= scores[-1] else -0.5)
            contracted_score = f(contracted)
            if contracted_score < min(score, scores[-1]):
                simplex[-1], scores[-1] = contracted, contracted_score
            else:
                simplex = [simplex[0]] + [[b + 0.5 * (x - b) for b, x in zip(simplex[0], point)] for point in simplex[1:]]
                scores = [scores[0]] + [f(point) for point in simplex[1:]]
    return simplex[min(range(n + 1), key=lambda i: scores[i])]


def fit(rows: list[dict], edges: dict, network: dict) -> tuple[dict[str, float], list[str]]:
    """Least squares on log error, each constant held inside its bounds by lo + (hi − lo)·sin²(u); a constant
    the rows' edges never reach keeps its starting value, § 2's. Rounded to what build-network.py would carry."""
    start = {name: min(max(value, BOUNDS[name][0]), BOUNDS[name][1])
             for name, value in values_of(network["RidingConstants"]()).items()}
    base = loss(rows, edges, constants_of(start, network), network)
    reached = [name for name in BOUNDS
               if any(not math.isclose(loss(rows, edges, constants_of({**start, name: bound}, network), network), base)
                      for bound in BOUNDS[name])]

    def unpack(u: list[float]) -> dict[str, float]:
        values = dict(start)
        for name, x in zip(reached, u):
            lo, hi = bounds_of(name, values)
            values[name] = lo + (hi - lo) * math.sin(x) ** 2
        return values

    def objective(u: list[float]) -> float:
        return loss(rows, edges, constants_of(unpack(u), network), network)

    u = []
    for name in reached:
        lo, hi = bounds_of(name, start)
        u.append(math.asin(math.sqrt(min(max((start[name] - lo) / (hi - lo), 0.02), 0.98))))
    for _ in range(4):
        u = nelder_mead(objective, u)
    return {name: round(value, 2) for name, value in unpack(u).items()}, reached


def report(label: str, rows: list[dict], edges: dict, constants, network: dict) -> list[float]:
    timed = timings(edges, constants, network)
    errors = []
    print(f"\n{label}: route, direction, estimate, reference, error")
    for row in rows:
        seconds = estimate(row, timed)
        errors.append(error(seconds, row["reference"]))
        print(f"  r{row['number']:<9} {row['name'][:44]:<44} {row['direction']:<8} {seconds / 60:6.1f} min "
              f"{row['reference'] / 60:6.1f} min {100 * (seconds - row['reference']) / row['reference']:+6.1f} %")
    print(f"  median {100 * statistics.median(errors):.1f} %, worst {100 * max(errors):.1f} %")
    return errors


def check_walks(rows: list[dict]) -> None:
    for row in rows:
        if row["direction"] != "walk's":
            continue
        moved = row["length"] / row["length_7a"] - 1
        flag = "  ** differs from 7a's by more than 2 %" if abs(moved) > LENGTH_MARK else ""
        print(f"  r{row['number']:<9} walk {row['length'] / 1000:6.2f} km against 7a's {row['length_7a'] / 1000:6.2f} km "
              f"({100 * moved:+.1f} %), {'turned against' if row['turned'] else 'as'} 7a's, start {row['start_off']:.0f} m "
              f"from R1's{flag}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--split", type=int, metavar="SEED", help="deal R1's strata with this seed and print the sets")
    parser.add_argument("--data", type=Path, help="a pin's merged/ cuts and archives/ .trails")
    parser.add_argument("--relief", type=Path, help="the relief the network's heights come from")
    parser.add_argument("--cache", type=Path, help="keep, or reuse, what the gate reads of the build")
    parser.add_argument("--fit", choices=("tune",), help="fit the constants on this set")
    parser.add_argument("--judge", choices=("judge",), help="judge the constants on this set")
    args = parser.parse_args()
    references = load_references()
    seed = args.split if args.split is not None else references["seed"]
    dealt = split(seed, references["strata"], references["replaced"] if seed == references["seed"] else None)
    if args.split is not None:
        for name in SETS:
            print(f"{name}: " + ", ".join(f"r{number}" for number in dealt[name]))
    if not (args.fit or args.judge):
        return

    network = load_network()
    numbers = [number for name in ("tune", "judge") for number in dealt[name]]
    if args.cache and args.cache.exists():
        built = json.loads(args.cache.read_text())
    else:
        if not (args.data and args.relief):
            parser.error("--fit and --judge need --data and --relief, or a --cache already written")
        built = build(network, args.data, args.relief, numbers)
        if args.cache:
            args.cache.write_text(json.dumps(built))
    sets = {name: directions(references, built, dealt[name], network) for name in ("tune", "judge")}
    print("walks at this pin against R1's:")
    check_walks(sets["tune"] + sets["judge"])

    starting = network["RidingConstants"]()
    print("\nstarting constants: " + ", ".join(f"{k} {v:g}" for k, v in values_of(starting).items()))
    report("tune, starting constants", sets["tune"], built["edges"], starting, network)
    constants = network["RIDING"]
    if args.fit:
        fitted, reached = fit(sets["tune"], built["edges"], network)
        constants = constants_of(fitted, network)
        print("\nfitted on tune (least squares on log error):")
        for name, value in fitted.items():
            lo, hi = bounds_of(name, fitted)
            note = "not reached by tune's edges, kept" if name not in reached else (
                "at its bound" if math.isclose(value, lo, rel_tol=1e-3) or math.isclose(value, hi, rel_tol=1e-3) else "")
            print(f"  {name:<22} {value:7.2f}   bounds {lo:.3g}–{hi:.3g} ({SOURCES[name]}) {note}")
        report("tune, fitted", sets["tune"], built["edges"], constants, network)
    if args.judge:
        errors = report("judge", sets["judge"], built["edges"], constants, network)
        median, worst = statistics.median(errors), max(errors)
        passed = median <= MEDIAN_MARK and worst <= WORST_MARK
        print(f"\nverdict: {'pass, verdict 1' if passed else 'fail, verdict 2'}: median {100 * median:.1f} % "
              f"(mark {100 * MEDIAN_MARK:.0f} %), worst {100 * worst:.1f} % (mark {100 * WORST_MARK:.0f} %)")


if __name__ == "__main__":
    main()
