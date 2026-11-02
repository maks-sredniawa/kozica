"""docs/phase-5.md § Tests and docs/phase-6c.md § Tests, for build-network.py:

    python3 -m unittest scripts/test_build_network.py
    python3 scripts/test_build_network.py --write    # rewrites the fixtures TrailNetworkTest and TrailWalksTest read
"""

import math
import runpy
import sqlite3
import struct
import sys
import tempfile
import unittest
from pathlib import Path

network = runpy.run_path(str(Path(__file__).with_name("build-network.py")), run_name="test")
FIXTURE = Path(__file__).resolve().parent.parent / "app/src/test/resources/trails-fixture.network"
WALKS_FIXTURE = FIXTURE.with_suffix(".edges")
Edge = network["Edge"]


def edge(way: int, refs: list[int], points: list[tuple[float, float]], heights: list[float], flags: int = 0, sac: int = 0,
         **bike):
    lengths = [network["distance"](*a, *b) for a, b in zip(points, points[1:])]
    located = [(ref, lat, lon) for ref, (lat, lon) in zip(refs, points)]
    return Edge(way, located, heights, sum(lengths), network["climb"](lengths, heights), flags, sac, **bike)


def fixture_edges() -> list:
    """A–B–C along a flat line, B→C one-way; A–D up and D–C along a noisy ridge, then down; E–F a demanding
    island far from the rest, over a summit midway; G–H–I a Trail bending round J–K, which is a Bike route
    alone, cut short between its two ends, ridden downhill towards K and graded S2, uphill S4.

    Node refs are OSM-like ids; only the ends of an edge are nodes of the network."""
    a, b, c, d = (49.0, 20.0), (49.0, 20.01), (49.0, 20.02), (49.01, 20.01)
    e, f = (49.2, 20.2), (49.2, 20.21)
    g, h, i = (49.3, 20.3), (49.31, 20.31), (49.3, 20.32)
    ridge = [(round(49.01 - k / 1000, 3), round(20.01 + k / 1000, 3)) for k in range(1, 8)]
    return [
        edge(100, [1, 2], [a, b], [500.0, 500.0]),
        edge(101, [2, 0, 3], [b, (49.0, 20.015), c], [500.0, 500.0, 500.0], flags=network["FORWARD_ONLY"]),
        edge(102, [1, 0, 4], [a, (49.005, 20.005), d], [500.0, 600.0, 700.0], sac=3),
        edge(103, [4] + [0] * 7 + [3], [d, *ridge, c], [700.0, 696.0, 700.0, 696.0, 700.0, 696.0, 700.0, 700.0, 500.0]),
        edge(104, [5, 0, 6], [e, (49.2, 20.205), f], [900.0, 1000.0, 940.0], sac=3),
        edge(105, [7, 8], [g, h], [800.0, 800.0], sets=3),
        edge(106, [8, 9], [h, i], [800.0, 800.0]),
        edge(107, [7, 0, 9], [g, (49.3, 20.31), i], [800.0, 800.0, 800.0], sets=2,
             ride=network["FORWARD_ONLY"], mtb=3, mtb_up=5),
    ]


def fixture_walks() -> list:
    """Over fixture_edges(): Trail 7 along A–B–C, Trail 9 the island walked from F to E, and Trail 8 from D
    down to A, then across a gap, B to C."""
    Step, Walk, TrailRow = network["Step"], network["Walk"], network["TrailRow"]

    def row(number: int, steps: list) -> object:
        return TrailRow(number, {}, network["TrailLine"]("red", None, []), Walk(steps, "line"), None)

    return [
        row(7, [Step(0, False), Step(1, False)]),
        row(8, [Step(2, True), Step(1, False, True)]),
        row(9, [Step(4, True)]),
    ]


class RideTest(unittest.TestCase):
    def test_a_rider_follows_oneway_bicycle_over_oneway_on_any_highway(self):
        ride, forward, backward = network["ride"], network["FORWARD_ONLY"], network["BACKWARD_ONLY"]
        self.assertEqual(ride({"highway": "track", "oneway": "yes"}), forward)
        self.assertEqual(ride({"highway": "unclassified", "oneway": "-1"}), backward)
        self.assertEqual(ride({"highway": "path", "oneway": "yes", "oneway:bicycle": "no"}), 0)
        self.assertEqual(ride({"highway": "path", "oneway": "yes", "oneway:bicycle": "-1"}), backward)
        self.assertEqual(ride({"highway": "path", "oneway:bicycle": "yes"}), forward)
        self.assertEqual(ride({"highway": "path"}), 0)

    def test_a_grade_is_its_leading_digit_plus_one(self):
        grade = network["grade"]
        self.assertEqual([grade(value) for value in ("0", "2+", "1-", "6", "hard", "", None)], [1, 3, 2, 7, 0, 0, 0])


class FoldTest(unittest.TestCase):
    def test_the_names_a_user_types_without_accents(self):
        fold = network["fold"]
        self.assertEqual(fold("Śnieżka"), "sniezka")
        self.assertEqual(fold("Łomnica"), "lomnica")
        self.assertEqual(fold("Gerlachovský štít"), "gerlachovsky stit")
        self.assertEqual(fold("Sněžka"), "snezka")
        self.assertEqual(fold("Hala Gąsienicowa"), "hala gasienicowa")
        self.assertEqual(fold("Schronisko PTTK „Na Hali”"), "schronisko pttk na hali")


class ClimbTest(unittest.TestCase):
    def test_noise_inside_the_dead_band_adds_no_climb(self):
        heights = [500 + (2 if i % 2 else -2) for i in range(41)]
        climb = network["climb"]([25.0] * 40, heights)
        self.assertEqual((climb.ascent, climb.descent), (0, 0))

    def test_a_steady_climb_counts_in_full(self):
        climb = network["climb"]([25.0] * 50, [500 + 2 * i for i in range(51)])
        self.assertAlmostEqual(climb.ascent, 100)
        self.assertEqual(climb.steep_ascent, 0)

    def test_a_steep_descent_is_counted_as_steep(self):
        climb = network["climb"]([25.0] * 10, [600 - 10 * i for i in range(11)])
        self.assertAlmostEqual(climb.descent, 100)
        self.assertAlmostEqual(climb.steep_descent, 100)

    def test_a_gentle_descent_costs_no_time(self):
        climb = network["climb"]([25.0] * 50, [600 - 2 * i for i in range(51)])
        self.assertAlmostEqual(climb.descent, 100)
        self.assertEqual(climb.steep_descent, 0)
        self.assertAlmostEqual(network["walking_seconds"](1250, climb.ascent, climb.steep_descent), 1125)


class WalkingTimeTest(unittest.TestCase):
    def test_the_pttk_marker_rule(self):
        seconds = network["walking_seconds"]
        self.assertAlmostEqual(seconds(4000, 0, 0), 3600)
        self.assertAlmostEqual(seconds(4000, 100, 0), 3600 + 600)
        self.assertAlmostEqual(seconds(4000, 0, 100), 3600 + 300)


class OnewayTest(unittest.TestCase):
    def test_walkers_follow_their_own_tags_and_never_a_road_s(self):
        oneway, forward, backward = network["oneway"], network["FORWARD_ONLY"], network["BACKWARD_ONLY"]
        self.assertEqual(oneway({"highway": "path", "oneway": "yes"}), forward)
        self.assertEqual(oneway({"highway": "steps", "oneway": "-1"}), backward)
        self.assertEqual(oneway({"highway": "track", "oneway": "yes"}), 0)
        self.assertEqual(oneway({"highway": "unclassified", "oneway": "yes"}), 0)
        self.assertEqual(oneway({"highway": "track", "oneway:foot": "yes"}), forward)
        self.assertEqual(oneway({"highway": "path", "oneway": "yes", "oneway:foot": "no"}), 0)
        self.assertEqual(oneway({"highway": "path", "foot:backward": "no"}), forward)


class PointOnSurfaceTest(unittest.TestCase):
    def test_a_point_inside_a_crescent(self):
        ring = [(0, 0), (0, 10), (10, 10), (10, 0), (8, 0), (8, 8), (2, 8), (2, 0), (0, 0)]
        lat, lon = network["point_on_surface"]([[(lon, lat) for lat, lon in ring]])
        self.assertTrue(0 < lat < 10 and (0 < lon < 2 or 8 < lon < 10 or 8 < lat))


class FixtureTest(unittest.TestCase):
    def test_the_committed_fixture_is_what_the_writer_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            written = Path(directory) / "fixture.network"
            network["write_network"](fixture_edges(), written)
            self.assertEqual(written.read_bytes(), FIXTURE.read_bytes(),
                             "run `python3 scripts/test_build_network.py --write` and commit the fixture")

    def test_the_committed_walks_are_what_the_writer_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            written = Path(directory) / "fixture.edges"
            network["write_walks"](fixture_walks(), written)
            self.assertEqual(written.read_bytes(), WALKS_FIXTURE.read_bytes(),
                             "run `python3 scripts/test_build_network.py --write` and commit the fixtures")

    def test_the_lengths_in_it(self):
        edges = fixture_edges()
        self.assertTrue(math.isclose(edges[0].length, 730, rel_tol=0.01))


class Rising:
    """4 m up every 0.01° east, under the dead band for any one way below."""

    def height(self, lat: float, lon: float) -> float:
        return 500 + 400 * (lon - 20)


def way(refs: list[int], tags: dict | None = None) -> tuple:
    return [(ref, 49.0, 20 + ref / 100) for ref in refs], tags or {}


def edges_of(ways: dict) -> list:
    return network["edges_of"](ways, Rising())[0]


class EdgesTest(unittest.TestCase):
    def test_ways_meeting_end_to_end_are_one_edge_so_their_climb_counts(self):
        edges = edges_of({1: way([1, 2]), 2: way([3, 2]), 3: way([3, 4])})
        self.assertEqual([[ref for ref, _, _ in edge.points] for edge in edges], [[1, 2, 3, 4]])
        self.assertAlmostEqual(edges[0].climb.ascent, 12)

    def test_a_loop_is_kept_whole(self):
        def walked(ways: dict) -> list:
            return sorted(sorted(tuple(sorted(pair)) for pair in zip(refs, refs[1:])) for edge in edges_of(ways)
                          for refs in [[ref for ref, _, _ in edge.points]])

        self.assertEqual(walked({1: way([5, 1]), 2: way([1, 2, 3, 1])}), [[(1, 2), (2, 3)], [(1, 3)], [(1, 5)]])
        self.assertEqual(walked({1: way([1, 2, 3, 4, 2, 5])}), [[(1, 2)], [(2, 3), (3, 4)], [(2, 4)], [(2, 5)]])

    def test_a_junction_or_a_change_of_walking_cuts_an_edge(self):
        self.assertEqual(len(edges_of({1: way([1, 2]), 2: way([2, 3]), 3: way([2, 5])})), 3)
        self.assertEqual(len(edges_of({1: way([1, 2]), 2: way([2, 3], {"highway": "path", "oneway": "yes"})})), 2)


class EndsTest(unittest.TestCase):
    def test_the_ends_each_convention_writes(self):
        ends = network["ends_of"]
        self.assertEqual(ends({"from": "Kuźnice", "to": "Kasprowy Wierch", "description": "A - B"}),
                         ("Kuźnice", "Kasprowy Wierch"))
        self.assertEqual(ends({"description": "Biely Váh - Kriváň", "note": "C - D"}), ("Biely Váh", "Kriváň"))
        self.assertEqual(ends({"osmc:name": "Hronov - Kohoutov", "note:cs": "C - D"}), ("Hronov", "Kohoutov"))
        self.assertEqual(ends({"note:cs": "Hronov - Kohoutov", "note": "C - D"}), ("Hronov", "Kohoutov"))
        self.assertEqual(ends({"note": "Rázc. pri Žabom potoku – Rysy"}), ("Rázc. pri Žabom potoku", "Rysy"))
        self.assertEqual(ends({"description": "Štrbské Pleso - Popradské pleso - Rysy"}), ("Štrbské Pleso", "Rysy"))
        self.assertEqual(ends({"description": "[Ž] Náchod - Dobrošov"}), ("Náchod", "Dobrošov"))

    def test_a_colour_code_leaves_a_name(self):
        self.assertEqual(network["without_colour_code"]("[Č] Hronov - Kohoutov"), "Hronov - Kohoutov")
        self.assertEqual(network["without_colour_code"]("Główny Szlak Beskidzki"), "Główny Szlak Beskidzki")

    def test_a_mapper_s_sentence_is_no_ends(self):
        ends = network["ends_of"]
        self.assertIsNone(ends({"description": "Kriváň"}))
        self.assertIsNone(ends({"note": "szlak dziki, operator nie znany, prowadzi z doliny - na grzbiet"}))
        self.assertIsNone(ends({"note:cs": "Větrník; Česká Čermná - Nad Bělovsí"}))
        self.assertIsNone(ends({"from": "Kuźnice"}))


TrailLine = network["TrailLine"]


class MembershipTest(unittest.TestCase):
    def test_a_trail_crossing_regions_is_one_line(self):
        west = ({7: TrailLine("red", "red_bar", [(1, ""), (2, "")])}, {9: [7]})
        east = ({7: TrailLine("red", "red_bar", [(2, ""), (3, "excursion"), (4, "")]), 8: TrailLine("blue", None, [(5, "")])},
                {9: [8]})
        trails, sections = network["merge_trails"]([west, east])
        self.assertEqual(trails[7].members, [(2, ""), (3, "excursion"), (4, ""), (1, "")])
        self.assertEqual(sorted(trails), [7, 8])
        self.assertEqual(sections, {9: [7, 8]})

    def test_the_profile_s_lines(self):
        trails, sections = network["parse_trails"]("trail\t5\tgreen\tgreen_bar\t11 12/excursion\nlong\t6\t5 7\n")
        self.assertEqual(trails[5], TrailLine("green", "green_bar", [(11, ""), (12, "excursion")]))
        self.assertEqual(sections, {6: [5, 7]})

    def test_a_nest_belongs_to_its_outermost(self):
        self.assertEqual(network["outermost"]({1: [2, 3], 3: [4, 5], 6: [7]}), {2: 1, 4: 1, 5: 1, 7: 6})


def place(ref: int) -> tuple[float, float]:
    """Node refs on a grid: tens north, units east, about 700 m apart."""
    return 49.0 + (ref // 10) / 160, 20.0 + (ref % 10) / 100


def grid_way(refs: list[int]) -> tuple:
    return [(ref, *place(ref)) for ref in refs], {}


def walked(ways: dict, trails: dict, number: int):
    edges = edges_of_trails(ways, trails)
    walk = network["walk_of"](number, trails[number], edges, ways, network["trail_edges"](edges))
    legs = []
    for step in walk.steps:
        refs = [ref for ref, _, _ in edges[step.edge].points]
        refs = refs[::-1] if step.reversed else refs
        legs.append((refs[0], refs[-1], step.breaks))
    return walk, edges, legs


def edges_of_trails(ways: dict, trails: dict) -> list:
    return network["edges_of"](ways, Rising(), network["memberships_of"](trails))[0]


class WalkTest(unittest.TestCase):
    def assert_joined(self, legs: list) -> None:
        for (_, end, _), (start, _, breaks) in zip(legs, legs[1:]):
            if not breaks:
                self.assertEqual(end, start)

    def test_a_line_starts_at_the_end_nearer_its_first_member(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4]), 4: grid_way([2, 12])}
        trails = {7: TrailLine("red", None, [(3, ""), (1, ""), (2, "")]), 8: TrailLine("blue", None, [(4, "")])}
        walk, edges, legs = walked(ways, trails, 7)
        self.assertEqual((legs[0][0], legs[-1][1]), (4, 1))
        self.assert_joined(legs)
        self.assertEqual(walk.shape, "line")
        self.assertAlmostEqual(sum(edges[s.edge].length for s in walk.steps),
                               sum(e.length for e in edges if (7, True) in e.trails))

    def test_a_loop_goes_round_once_from_its_first_way(self):
        ways = {1: grid_way([1, 2, 12]), 2: grid_way([12, 11, 1])}
        walk, _, legs = walked(ways, {7: TrailLine("red", None, [(2, ""), (1, "")])}, 7)
        self.assertEqual(walk.shape, "loop")
        self.assertEqual(legs[0][0], 12)
        self.assertEqual(legs[-1][1], 12)
        self.assertEqual(len(legs), 2)
        self.assert_joined(legs)

    def test_a_gap_is_recorded_and_walked_across(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([5, 6]), 3: grid_way([2, 3])}
        walk, _, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (3, ""), (2, "")])}, 7)
        self.assertEqual(walk.shape, "gaps")
        self.assertEqual(legs, [(1, 3, False), (5, 6, True)])

    def test_a_loop_between_two_ends_is_walked_round_its_long_side(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3, 4]), 3: grid_way([2, 12, 13, 14, 4]), 4: grid_way([4, 5])}
        walk, edges, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (2, ""), (3, ""), (4, "")])}, 7)
        self.assertEqual(walk.shape, "branch")
        self.assertEqual(legs, [(1, 2, False), (2, 4, False), (4, 5, False)])
        self.assertEqual([len(edges[s.edge].points) for s in walk.steps], [2, 5, 2])

    def test_a_loop_on_the_way_is_walked_round_and_left_where_it_was_entered(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([2, 12, 13]), 4: grid_way([13, 2])}
        walk, _, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (3, ""), (4, ""), (2, "")])}, 7)
        self.assertEqual((legs[0][0], legs[-1][1], len(legs)), (1, 3, 4))
        self.assert_joined(legs)

    def test_gaps_are_joined_in_the_order_that_leaves_the_least_gap(self):
        ways = {1: grid_way([4, 5]), 2: grid_way([1, 2]), 3: grid_way([7, 8, 9])}
        walk, _, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (2, ""), (3, "")])}, 7)
        self.assertEqual(walk.shape, "gaps")
        self.assertEqual(legs, [(1, 2, False), (4, 5, True), (7, 9, True)])

    def test_backward_is_off_the_walk_only_beside_a_forward_strand(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([2, 13, 3]), 4: grid_way([3, 4])}
        split = {7: TrailLine("red", None, [(1, ""), (2, "forward"), (3, "backward"), (4, "")])}
        walk, edges, legs = walked(ways, split, 7)
        self.assertEqual(legs, [(1, 2, False), (2, 3, False), (3, 4, False)])
        self.assertEqual([len(edges[s.edge].points) for s in walk.steps], [2, 2, 2])
        alone = {7: TrailLine("red", None, [(1, ""), (3, "backward"), (4, "")])}
        walk, _, legs = walked({k: ways[k] for k in (1, 3, 4)}, alone, 7)
        self.assertEqual(walk.shape, "line")
        self.assertEqual(legs, [(1, 4, False)])

    def test_an_alternative_is_the_trail_s_but_off_its_walk(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([2, 12])}
        walk, edges, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (2, ""), (3, "alternative")])}, 7)
        self.assertEqual(legs, [(1, 2, False), (2, 3, False)])
        self.assertTrue(any((7, False) in edge.trails for edge in edges))
        self.assertEqual(walk.shape, "line")

    def test_a_branch_with_no_role_takes_the_longest_path(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3, 4, 5]), 3: grid_way([2, 12])}
        walk, _, legs = walked(ways, {7: TrailLine("red", None, [(1, ""), (3, ""), (2, "")])}, 7)
        self.assertEqual(walk.shape, "branch")
        self.assertEqual((legs[0][0], legs[-1][1]), (1, 5))
        self.assert_joined(legs)


def bike_line(colour: str, kind: str, members: list, senses: dict | None = None):
    return TrailLine(colour, None, members, kind, senses or {})


def edges_with_bikes(ways: dict, trails: dict, bikes: dict) -> list:
    return network["edges_of"](ways, Rising(), network["memberships_of"](trails), network["bike_memberships_of"](bikes))[0]


class BikeEdgesTest(unittest.TestCase):
    def test_each_edge_says_which_sets_it_is_in(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        edges = edges_with_bikes(ways, {7: TrailLine("red", None, [(1, ""), (2, "")])},
                                 {8: bike_line("blue", "touring", [(2, ""), (3, "")])})
        self.assertEqual(sorted((e.points[0][0], e.points[-1][0], e.sets) for e in edges),
                         [(1, 2, 1), (2, 3, 3), (3, 4, 2)])

    def test_a_change_of_grade_or_of_riding_cuts_an_edge(self):
        def count(first: dict, second: dict) -> int:
            ways = {1: (grid_way([1, 2])[0], first), 2: (grid_way([2, 3])[0], second)}
            return len(edges_with_bikes(ways, {}, {8: bike_line("blue", "mtb", [(1, ""), (2, "")])}))

        self.assertEqual(count({}, {}), 1)
        self.assertEqual(count({"mtb:scale": "1"}, {"mtb:scale": "2"}), 2)
        self.assertEqual(count({"mtb:scale:uphill": "1"}, {}), 2)
        self.assertEqual(count({"oneway": "yes"}, {}), 2)

    def test_two_ways_drawn_head_to_head_are_one_edge_with_one_sense(self):
        forward, backward = network["FORWARD_ONLY"], network["BACKWARD_ONLY"]
        ways = {1: grid_way([1, 2]), 2: grid_way([3, 2])}
        edges = edges_with_bikes(ways, {}, {8: bike_line("blue", "mtb", [(1, ""), (2, "")], {1: forward, 2: backward})})
        self.assertEqual(len(edges), 1)
        along = forward if edges[0].points[0][0] == 1 else backward
        self.assertEqual(edges[0].bikes, frozenset({(8, True, along)}))


def index_of(ways: dict, bikes: dict, tags: dict, sections: dict | None = None) -> list[dict]:
    edges = edges_with_bikes(ways, {}, bikes)
    by_route = network["trail_edges"](edges)
    rows = network["bike_rows"](bikes, tags, sections or {}, edges, ways, by_route)
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory) / "places.sqlite"
        network["write_places"]({}, [], rows, tags, edges, by_route, target)
        db = sqlite3.connect(target)
        db.row_factory = sqlite3.Row
        found = [dict(row) for row in db.execute("SELECT * FROM bike_routes ORDER BY id")]
        found_names = db.execute("SELECT bike_route, folded FROM bike_route_names ORDER BY bike_route, folded").fetchall()
        db.close()
    for row in found:
        row["names"] = [tuple(pair)[1] for pair in found_names if tuple(pair)[0] == row["id"]]
    return found


class BikeRouteLinesTest(unittest.TestCase):
    def test_the_profile_s_lines(self):
        forward, backward = network["FORWARD_ONLY"], network["BACKWARD_ONLY"]
        bikes, sections = network["parse_bike_routes"](
            "trail\t5\tgreen\t\t1\nbike\t7\tred\tmtb\t11@f 12/forward@b 13\nsections\t9\t7 8\n")
        self.assertEqual(bikes, {7: TrailLine("red", None, [(11, ""), (12, "forward"), (13, "")], "mtb",
                                              {11: forward, 12: backward})})
        self.assertEqual(sections, {9: [7, 8]})

    def test_a_bike_route_crossing_regions_keeps_its_kind_and_every_sense(self):
        forward = network["FORWARD_ONLY"]
        west = ({7: bike_line("red", "mtb", [(1, "")], {1: forward})}, {})
        east = ({7: bike_line("red", "mtb", [(1, ""), (2, "")], {2: forward})}, {})
        bikes, _ = network["merge_trails"]([west, east])
        self.assertEqual(bikes[7], bike_line("red", "mtb", [(1, ""), (2, "")], {1: forward, 2: forward}))


class RiddenOneWayTest(unittest.TestCase):
    backward = network["BACKWARD_ONLY"]
    forward = network["FORWARD_ONLY"]

    def test_a_descent_whose_members_run_uphill_is_walked_downhill(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        line = bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], {1: self.backward, 2: self.backward, 3: self.backward})
        row = index_of(ways, {7: line}, {})[0]
        self.assertEqual(row["one_way"], 1)
        self.assertAlmostEqual(row["ascent"], 0)
        self.assertAlmostEqual(row["descent"], 12)
        self.assertEqual((row["start_lon"], row["end_lon"]), (place(4)[1], place(1)[1]))

    def test_a_signed_route_with_one_undirected_way_is_still_one_way(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        line = bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], {1: self.backward, 2: self.backward})
        unsigned = index_of(ways, {7: line}, {})[0]
        self.assertEqual(unsigned["one_way"], 0)
        signed = index_of(ways, {7: line}, {7: {"signed_direction": "yes"}})[0]
        self.assertEqual(signed["one_way"], 1)
        self.assertEqual(signed["start_lon"], place(4)[1])

    def test_a_line_mostly_directed_is_ridden_one_way_past_its_untagged_connector(self):
        ways = {way: grid_way([way, way + 1]) for way in range(1, 6)}
        members = [(way, "") for way in ways]
        line = bike_line("blue", "mtb", members, {way: self.backward for way in (1, 2, 4, 5)})
        row = index_of(ways, {7: line}, {})[0]
        self.assertEqual(row["one_way"], 1)
        self.assertEqual((row["start_lon"], row["end_lon"]), (place(6)[1], place(1)[1]))
        self.assertAlmostEqual(row["ascent"], 0)
        self.assertAlmostEqual(row["descent"], 20)

    def test_a_stretch_against_the_rest_keeps_it_two_way_however_much_is_directed(self):
        ways = {way: grid_way([way, way + 1]) for way in range(1, 6)}
        members = [(way, "") for way in ways]
        senses = {1: self.forward, 2: self.forward, 3: self.forward, 4: self.forward, 5: self.backward}
        self.assertEqual(index_of(ways, {7: bike_line("blue", "mtb", members, senses)}, {})[0]["one_way"], 0)

    def test_a_loop_goes_round_the_way_it_is_ridden(self):
        ways = {1: grid_way([1, 2, 12]), 2: grid_way([12, 11, 1])}
        for sense, second in ((self.forward, 2), (self.backward, 11)):
            edges = edges_with_bikes(ways, {}, {7: bike_line("red", "mtb", [(1, ""), (2, "")], {1: sense, 2: sense})})
            line = bike_line("red", "mtb", [(1, ""), (2, "")], {1: sense, 2: sense})
            rows = network["bike_rows"]({7: line}, {}, {}, edges, ways, network["trail_edges"](edges))
            self.assertTrue(rows[0].one_way)
            first = rows[0].walk.steps[0]
            refs = [ref for ref, _, _ in edges[first.edge].points]
            self.assertEqual((refs[::-1] if first.reversed else refs)[1], second)

    def test_a_touring_loop_with_one_one_way_street_is_two_way(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 12]), 3: grid_way([12, 11, 1])}
        line = bike_line("red", "touring", [(1, ""), (2, ""), (3, "")], {2: self.forward})
        self.assertEqual(index_of(ways, {7: line}, {})[0]["one_way"], 0)

    def test_a_forward_backward_pair_in_town_is_two_way(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([2, 13, 3]), 4: grid_way([3, 4])}
        line = bike_line("red", "touring", [(1, ""), (2, "forward"), (3, "backward"), (4, "")],
                         {2: self.forward, 3: self.backward})
        self.assertEqual(index_of(ways, {7: line}, {})[0]["one_way"], 0)

    def test_a_turned_walk_keeps_its_gaps_where_they_were(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([5, 6])}
        line = bike_line("red", "mtb", [(1, ""), (2, "")], {1: self.backward, 2: self.backward})
        edges = edges_with_bikes(ways, {}, {7: line})
        walk = network["bike_rows"]({7: line}, {}, {}, edges, ways, network["trail_edges"](edges))[0].walk
        self.assertEqual([(step.breaks) for step in walk.steps], [False, True])
        self.assertTrue(all(step.reversed for step in walk.steps))


class BikeRouteIndexTest(unittest.TestCase):
    def test_a_row_holds_what_the_sheet_reads(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3])}
        tags = {7: {"name": "[Č] Twister", "name:pl": "Skręt", "ref": "T1", "network": "lcn", "mtb:scale": "2+",
                    "mtb:scale:uphill": "hard", "from": "A", "to": "B"}, 9: {"name": "Szlak"}}
        row = index_of(ways, {7: bike_line("red", "mtb", [(1, ""), (2, "")])}, tags, {9: [7]})[0]
        self.assertEqual((row["id"], row["name"], row["name_pl"], row["ref"], row["colour"]), (7, "Twister", "Skręt", "T1", "red"))
        self.assertEqual((row["kind"], row["network"], row["one_way"]), ("mtb", "lcn", 0))
        self.assertEqual((row["mtb_scale"], row["mtb_scale_uphill"]), (2, None))
        self.assertEqual((row["from_place"], row["to_place"]), ("A", "B"))
        self.assertEqual((row["long_distance"], row["long_distance_name"]), (9, "Szlak"))
        self.assertEqual(row["names"], ["skret", "twister"])
        self.assertNotIn("symbol", row)

    def test_a_parent_s_own_ways_are_a_row_beside_its_sections(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([5, 6])}
        bikes = {9: bike_line("other", "touring", [(1, "")]), 7: bike_line("red", "touring", [(2, "")])}
        rows = index_of(ways, bikes, {9: {"name": "Greenway"}}, {9: [7]})
        self.assertEqual([(row["id"], row["long_distance"]) for row in rows], [(7, 9), (9, None)])

    def test_walks_of_both_kinds_are_one_sorted_file(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([5, 6])}
        bikes = {7: bike_line("red", "mtb", [(2, "")])}
        trails = {9: TrailLine("red", None, [(1, "")])}
        edges = network["edges_of"](ways, Rising(), network["memberships_of"](trails), network["bike_memberships_of"](bikes))[0]
        by_route = network["trail_edges"](edges)
        rows = network["route_rows"](trails, {}, edges, ways, by_route, {}) + network["bike_rows"](bikes, {}, {}, edges, ways, by_route)
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "walks"
            network["write_walks"](rows, target)
            header = target.read_bytes()
        count = struct.unpack_from("<I", header, 8)[0]
        self.assertEqual(struct.unpack_from(f"<{count}q", header, 16), (7, 9))


class TrailCutTest(unittest.TestCase):
    def test_a_trail_s_end_is_an_edge_s_end(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3])}
        edges = edges_of_trails(ways, {7: TrailLine("red", None, [(1, "")]), 8: TrailLine("blue", None, [(1, ""), (2, "")])})
        self.assertEqual(sorted((e.points[0][0], e.points[-1][0]) for e in edges), [(1, 2), (2, 3)])

    def test_the_same_trails_on_both_ways_join_them(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3])}
        edges = edges_of_trails(ways, {7: TrailLine("red", None, [(1, ""), (2, "")])})
        self.assertEqual(len(edges), 1)


if __name__ == "__main__":
    if sys.argv[1:] == ["--write"]:
        FIXTURE.parent.mkdir(parents=True, exist_ok=True)
        print(network["write_network"](fixture_edges(), FIXTURE))
        print(network["write_walks"](fixture_walks(), WALKS_FIXTURE))
    else:
        unittest.main()
