"""docs/phase-5.md § Tests, docs/phase-6c.md § Tests and docs/phase-7b.md § Tests, for build-network.py:

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
from dataclasses import fields, replace
from pathlib import Path

network = runpy.run_path(str(Path(__file__).with_name("build-network.py")), run_name="test")
FIXTURE = Path(__file__).resolve().parent.parent / "app/src/test/resources/trails-fixture.network"
WALKS_FIXTURE = FIXTURE.with_suffix(".edges")
Edge = network["Edge"]


def edge(way: int, refs: list[int], points: list[tuple[float, float]], heights: list[float], flags: int = 0, sac: int = 0,
         **bike):
    lengths = [network["distance"](*a, *b) for a, b in zip(points, points[1:])]
    located = [(ref, lat, lon) for ref, (lat, lon) in zip(refs, points)]
    if bike.get("sets", network["SET_TRAIL"]) & (network["SET_TOURING"] | network["SET_MTB"]):
        bike["riding_forward"], bike["riding_backward"] = network["riding_seconds"](
            lengths, heights, bike["surface"], bike.get("mtb", 0), bike.get("mtb_up", 0))
    return Edge(way, located, heights, sum(lengths), network["climb"](lengths, heights), flags, sac, **bike)


def fixture_edges() -> list:
    """A–B–C along a flat line, B→C one-way; A–D up and D–C along a noisy ridge, then down; E–F a demanding
    island far from the rest, over a summit midway; G–H–I a Trail bending round J–K, G–H paved and a Touring
    route too, and J–K an unpaved MTB route alone, cut short between its two ends, ridden one way towards K and
    graded S2, uphill S4. Then docs/phase-7b.md's rides: P–Q a rough Trail and Touring route straight across,
    P–R–Q the same but paved and longer; Q–S unpaved, on both kinds, its MTB route alone directing it towards S
    and graded S1; S–T a rough MTB route that climbs with a wobble, then drops a little, graded S3, uphill S2.
    Last an island of Bike routes alone, so every constant in RIDING moves a stored second: U–V paved, V–W unpaved
    and W–X rough, each an ungraded Touring route dropping 20 m then running flat; X–Y an MTB route graded S4 that
    climbs 5 %, its cap the time uphill and the push the time down. Last of all docs/phase-7b.md § 6's pair of
    strands, every edge paved and flat: K–L, then L–M one-way over the strand bowed north and M–L one-way over the
    one bowed south, then M–N, all Touring; K–N a straight Touring shortcut, and L–M a straight MTB one.

    Node refs are OSM-like ids; only the ends of an edge are nodes of the network."""
    a, b, c, d = (49.0, 20.0), (49.0, 20.01), (49.0, 20.02), (49.01, 20.01)
    e, f = (49.2, 20.2), (49.2, 20.21)
    g, h, i = (49.3, 20.3), (49.31, 20.31), (49.3, 20.32)
    ridge = [(round(49.01 - k / 1000, 3), round(20.01 + k / 1000, 3)) for k in range(1, 8)]
    p, q, r, s, t = (49.5, 20.5), (49.5, 20.52), (49.506, 20.51), (49.5, 20.54), (49.51, 20.55)
    climb = [(round(49.5 + k / 600, 5), round(20.54 + k / 600, 5)) for k in range(1, 6)]
    u, v, w, x, y = (49.7, 20.7), (49.7, 20.72), (49.7, 20.74), (49.7, 20.76), (49.7, 20.78)
    trail_and_touring = network["SET_TRAIL"] | network["SET_TOURING"]
    return [
        edge(100, [1, 2], [a, b], [500.0, 500.0]),
        edge(101, [2, 0, 3], [b, (49.0, 20.015), c], [500.0, 500.0, 500.0], flags=network["FORWARD_ONLY"]),
        edge(102, [1, 0, 4], [a, (49.005, 20.005), d], [500.0, 600.0, 700.0], sac=3),
        edge(103, [4] + [0] * 7 + [3], [d, *ridge, c], [700.0, 696.0, 700.0, 696.0, 700.0, 696.0, 700.0, 700.0, 500.0]),
        edge(104, [5, 0, 6], [e, (49.2, 20.205), f], [900.0, 1000.0, 940.0], sac=3),
        edge(105, [7, 8], [g, h], [800.0, 800.0], sets=3, surface=network["PAVED"]),
        edge(106, [8, 9], [h, i], [800.0, 800.0]),
        edge(107, [7, 0, 9], [g, (49.3, 20.31), i], [800.0, 800.0, 800.0], sets=4, surface=network["UNPAVED"],
             ride=network["FORWARD_ONLY"], mtb=3, mtb_up=5, ride_mtb=network["FORWARD_ONLY"]),
        edge(108, [20, 0, 21], [p, (49.5, 20.51), q], [400.0] * 3, sets=trail_and_touring, surface=network["ROUGH"]),
        edge(109, [20, 22], [p, r], [400.0] * 2, sets=trail_and_touring, surface=network["PAVED"]),
        edge(110, [22, 21], [r, q], [400.0] * 2, sets=trail_and_touring, surface=network["PAVED"]),
        edge(111, [21, 23], [q, s], [400.0] * 2, sets=6, surface=network["UNPAVED"], mtb=2,
             ride_mtb=network["FORWARD_ONLY"]),
        edge(112, [23] + [0] * 5 + [24], [s, *climb, t], [400.0, 403.0, 420.0, 417.0, 450.0, 440.0, 436.0], sets=4,
             surface=network["ROUGH"], mtb=4, mtb_up=3),
        edge(113, [30, 0, 31], [u, (49.7, 20.71), v], [420.0, 400.0, 400.0], sets=network["SET_TOURING"],
             surface=network["PAVED"]),
        edge(114, [31, 0, 32], [v, (49.7, 20.73), w], [400.0, 380.0, 380.0], sets=network["SET_TOURING"],
             surface=network["UNPAVED"]),
        edge(115, [32, 0, 33], [w, (49.7, 20.75), x], [380.0, 360.0, 360.0], sets=network["SET_TOURING"],
             surface=network["ROUGH"]),
        edge(116, [33, 34], [x, y], [360.0, 433.0], sets=network["SET_MTB"], surface=network["ROUGH"], mtb=5),
        *strands(),
    ]


def strands() -> list:
    k, l, m, n = (49.9, 20.9), (49.905, 20.91), (49.905, 20.93), (49.9, 20.94)
    touring, forward = network["SET_TOURING"], network["FORWARD_ONLY"]

    def paved(way: int, refs: list[int], points: list, sets: int = touring, **one_way):
        return edge(way, refs, points, [300.0] * len(points), sets=sets, surface=network["PAVED"], **one_way)

    return [
        paved(117, [40, 41], [k, l]),
        paved(118, [41, 0, 42], [l, (49.908, 20.92), m], ride=forward, ride_touring=forward),
        paved(119, [42, 0, 41], [m, (49.902, 20.92), l], ride=forward, ride_touring=forward),
        paved(120, [42, 43], [m, n]),
        paved(121, [40, 0, 43], [k, (49.9, 20.92), n]),
        paved(122, [41, 0, 42], [l, (49.905, 20.92), m], sets=network["SET_MTB"]),
    ]


def fixture_walks() -> list:
    """Over fixture_edges(): Trail 7 along A–B–C, Trail 9 the island walked from F to E, and Trail 8 from D
    down to A, then across a gap, B to C; Bike route 11 from K to N over the north strand and back over the
    south one."""
    Step, Walk, TrailRow = network["Step"], network["Walk"], network["TrailRow"]

    def row(number: int, steps: list, back: list | None = None) -> object:
        return TrailRow(number, {}, network["TrailLine"]("red", None, []), Walk(steps, "line"), None, back=back)

    return [
        row(7, [Step(0, False), Step(1, False)]),
        row(8, [Step(2, True), Step(1, False, True)]),
        row(9, [Step(4, True)]),
        row(11, [Step(17, False), Step(18, False), Step(20, False)], [Step(20, True), Step(19, False), Step(17, True)]),
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
    return network["edges_of"](ways, Rising(), network["memberships_of"](trails), network["bike_memberships_of"](bikes, ways))[0]


def own_bike_edges(ways: dict, bikes: dict) -> list:
    return network["own_edges"](bikes, ways, None, network["bike_memberships_of"](bikes, ways))


def legs_of(walk, edges: list) -> list:
    legs = []
    for step in walk.steps:
        refs = [ref for ref, _, _ in edges[step.edge].points]
        refs = refs[::-1] if step.reversed else refs
        legs.append((refs[0], refs[-1], step.breaks))
    return legs


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
        self.assertEqual(edges[0].bikes, frozenset({(8, True, along, "mtb")}))


def index_of(ways: dict, bikes: dict, tags: dict, sections: dict | None = None) -> list[dict]:
    edges = edges_with_bikes(ways, {}, bikes)
    by_route = network["trail_edges"](edges)
    rows = network["bike_rows"](bikes, tags, sections or {}, edges, ways, own_bike_edges(ways, bikes))
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

    def test_a_relation_s_own_oneway_rides_it_one_way_and_no_says_nothing(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        line = bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], {1: self.backward, 2: self.backward})
        for oneway, one_way in (("yes", 1), ("-1", 1), ("no", 0)):
            row = index_of(ways, {7: line}, {7: {"oneway": oneway}})[0]
            self.assertEqual(row["one_way"], one_way, oneway)
        self.assertEqual(index_of(ways, {7: line}, {7: {"oneway": "-1"}})[0]["start_lon"], place(4)[1])

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

    def rows_of(self, ways: dict, line) -> tuple:
        bikes = {7: line}
        edges = edges_with_bikes(ways, {}, bikes)
        return network["bike_rows"](bikes, {}, {}, edges, ways, own_bike_edges(ways, bikes))[0], edges

    def against(self, row, edges: list) -> list:
        return [step for step in row.walk.steps if network["sense_along"](7, edges[step.edge], step) < 0]

    def test_a_ring_on_a_one_way_line_goes_round_with_the_ride(self):
        ring = {1: grid_way([1, 2]), 2: grid_way([2, 3, 13]), 3: grid_way([13, 12, 2])}
        for sense in (self.forward, self.backward):
            senses = {1: self.forward, 2: sense, 3: sense}
            row, edges = self.rows_of(ring, bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], senses))
            self.assertTrue(row.one_way)
            self.assertEqual(self.against(row, edges), [])

    def test_a_ring_is_turned_where_the_line_into_it_runs_against_the_walk(self):
        ring = {1: grid_way([1, 2]), 2: grid_way([2, 3, 13]), 3: grid_way([13, 12, 2])}
        senses = {1: self.backward, 2: self.backward, 3: self.backward}
        row, edges = self.rows_of(ring, bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], senses))
        self.assertTrue(row.one_way)
        self.assertEqual(self.against(row, edges), [])
        first = edges[row.walk.steps[0].edge]
        self.assertEqual((first.points[-1] if row.walk.steps[0].reversed else first.points[0])[0], 2)

    def test_turning_the_whole_walk_wins_over_turning_only_its_ring(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3, 4, 5, 15]), 3: grid_way([15, 14, 13, 12, 2])}
        members = [(1, ""), (2, ""), (3, "")]
        ends = set()
        for sense in (self.forward, self.backward):
            row, edges = self.rows_of(ways, bike_line("red", "mtb", members, {2: sense, 3: sense}))
            self.assertTrue(row.one_way)
            self.assertEqual(self.against(row, edges), [])
            first, last = row.walk.steps[0], row.walk.steps[-1]
            ends.add((edges[first.edge].points[-1 if first.reversed else 0][0],
                      edges[last.edge].points[0 if last.reversed else -1][0]))
        self.assertEqual(ends, {(1, 2), (2, 1)})

    def test_a_stretch_against_the_ride_on_no_ring_stays_two_way(self):
        ways = {way: grid_way([way, way + 1]) for way in range(1, 5)}
        senses = {1: self.forward, 2: self.forward, 3: self.backward, 4: self.backward}
        row, _ = self.rows_of(ways, bike_line("red", "mtb", [(way, "") for way in ways], senses))
        self.assertFalse(row.one_way)

    def test_a_two_way_route_keeps_the_walk_the_search_gave_it(self):
        ring = {1: grid_way([1, 2]), 2: grid_way([2, 3, 13]), 3: grid_way([13, 12, 2])}
        row, edges = self.rows_of(ring, bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], {3: self.backward}))
        self.assertFalse(row.one_way)
        self.assertIs(network["ridden"](7, row.walk, edges, False)[0], row.walk)

    def test_a_loop_goes_round_the_way_it_is_ridden(self):
        ways = {1: grid_way([1, 2, 12]), 2: grid_way([12, 11, 1])}
        for sense, second in ((self.forward, 2), (self.backward, 11)):
            edges = edges_with_bikes(ways, {}, {7: bike_line("red", "mtb", [(1, ""), (2, "")], {1: sense, 2: sense})})
            line = bike_line("red", "mtb", [(1, ""), (2, "")], {1: sense, 2: sense})
            rows = network["bike_rows"]({7: line}, {}, {}, edges, ways, own_bike_edges(ways, {7: line}))
            self.assertTrue(rows[0].one_way)
            first = rows[0].walk.steps[0]
            refs = [ref for ref, _, _ in edges[first.edge].points]
            self.assertEqual((refs[::-1] if first.reversed else refs)[1], second)

    def test_a_touring_loop_with_one_one_way_street_is_two_way(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 12]), 3: grid_way([12, 11, 1])}
        line = bike_line("red", "touring", [(1, ""), (2, ""), (3, "")], {2: self.forward})
        self.assertEqual(index_of(ways, {7: line}, {})[0]["one_way"], 0)

    def test_a_forward_backward_pair_in_town_is_two_way(self):
        line = bike_line("red", "touring", [(1, ""), (2, "forward"), (3, "backward"), (4, "")],
                         {2: self.forward, 3: self.backward})
        self.assertEqual(index_of(TOWN_PAIR, {7: line}, {})[0]["one_way"], 0)

    def test_a_backward_strand_beside_a_one_way_line_leaves_it_one_way(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: TOWN_PAIR[3]}
        line = bike_line("red", "mtb", [(1, "forward"), (2, "forward"), (3, "backward")],
                         {1: self.forward, 2: self.forward, 3: self.backward})
        row = index_of(ways, {7: line}, {})[0]
        self.assertEqual(row["one_way"], 1)
        self.assertAlmostEqual(row["length"], 1459, delta=1)

    def test_a_turned_walk_keeps_its_gaps_where_they_were(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([5, 6])}
        line = bike_line("red", "mtb", [(1, ""), (2, "")], {1: self.backward, 2: self.backward})
        edges = edges_with_bikes(ways, {}, {7: line})
        walk = network["bike_rows"]({7: line}, {}, {}, edges, ways, own_bike_edges(ways, {7: line}))[0].walk
        self.assertEqual([(step.breaks) for step in walk.steps], [False, True])
        self.assertTrue(all(step.reversed for step in walk.steps))


TOWN_PAIR = {
    1: grid_way([1, 2]),
    2: grid_way([2, 3]),
    3: ([(2, *place(2)), (900, 49.0005, 20.025), (3, *place(3))], {}),
    4: grid_way([3, 4]),
}


class BackwardMembersTest(unittest.TestCase):
    forward = network["FORWARD_ONLY"]
    backward = network["BACKWARD_ONLY"]

    def line(self, *members: tuple) -> object:
        senses = {way: {"forward": self.forward, "backward": self.backward}[role] for way, role in members if role}
        return bike_line("red", "mtb", list(members), senses)

    def test_the_other_strand_of_a_pair_is_off_the_walk(self):
        line = self.line((1, ""), (2, "forward"), (3, "backward"), (4, ""))
        self.assertEqual(network["bike_memberships_of"]({7: line}, TOWN_PAIR)[3], frozenset({(7, False, self.backward, "mtb")}))
        row = index_of(TOWN_PAIR, {7: line}, {})[0]
        self.assertAlmostEqual(row["length"], 2188, delta=1)
        self.assertEqual(row["gaps"], 0)

    def test_stretches_drawn_against_the_ride_are_on_the_walk(self):
        ways = {1: grid_way([2, 1]), 2: grid_way([2, 3]), 3: grid_way([4, 3])}
        row = index_of(ways, {7: self.line((1, "backward"), (2, "forward"), (3, "backward"))}, {})[0]
        self.assertAlmostEqual(row["length"], 2188, delta=1)
        self.assertEqual(row["one_way"], 1)
        self.assertEqual((row["start_lon"], row["end_lon"]), (place(1)[1], place(4)[1]))
        self.assertAlmostEqual(row["ascent"], 12)
        self.assertAlmostEqual(row["descent"], 0)

    def test_a_backward_stretch_between_two_forward_strands_is_on_the_walk_however_close(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([3, 2]), 3: grid_way([3, 4])}
        row = index_of(ways, {7: self.line((1, "forward"), (2, "backward"), (3, "forward"))}, {})[0]
        self.assertAlmostEqual(row["length"], 2188, delta=1)
        self.assertEqual(row["one_way"], 1)

    def test_a_ring_of_one_forward_and_one_backward_strand_goes_all_the_way_round(self):
        ways = {1: grid_way([1, 2, 3]), 2: grid_way([1, 11, 21, 22, 23, 13, 3])}
        row = index_of(ways, {7: self.line((1, "forward"), (2, "backward"))}, {})[0]
        self.assertAlmostEqual(row["length"], 5697, delta=1)
        self.assertEqual(row["one_way"], 1)
        self.assertEqual((row["start_lon"], row["end_lon"]), (place(1)[1], place(1)[1]))
        self.assertAlmostEqual(row["ascent"], 8)
        self.assertAlmostEqual(row["descent"], 8)

    def test_a_trail_keeps_its_own_rule(self):
        memberships = network["memberships_of"]({7: TrailLine("red", None, [(1, "forward"), (2, "backward")])})
        self.assertEqual(memberships[2], frozenset({(7, False)}))


class OwnWaysTest(unittest.TestCase):
    def rows(self, ways: dict, trails: dict, bikes: dict) -> tuple:
        memberships = network["memberships_of"](trails)
        edges = network["edges_of"](ways, Rising(), memberships, network["bike_memberships_of"](bikes, ways))[0]
        own = network["own_edges"](trails, ways, memberships)
        return network["route_rows"](trails, {}, edges, ways, own, {}), edges

    def test_a_bike_route_s_cut_does_not_turn_a_trail(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4]), 4: grid_way([4, 5]), 9: grid_way([3, 13])}
        trails = {7: TrailLine("red", None, [(3, ""), (4, ""), (2, ""), (1, "")])}
        alone = walked({k: ways[k] for k in (1, 2, 3, 4)}, trails, 7)[2]
        self.assertEqual(alone, [(1, 5, False)])
        rows, edges = self.rows(ways, trails, {8: bike_line("blue", "touring", [(9, "")])})
        self.assertEqual(legs_of(rows[0].walk, edges), [(1, 3, False), (3, 5, False)])

    def test_a_bike_route_s_cut_does_not_move_a_trail_s_loop(self):
        ways = {1: grid_way([1, 2, 3]), 2: grid_way([3, 4, 1]), 9: grid_way([4, 14])}
        trails = {7: TrailLine("red", None, [(2, ""), (1, "")])}
        rows, edges = self.rows(ways, trails, {8: bike_line("blue", "touring", [(9, "")])})
        self.assertEqual(legs_of(rows[0].walk, edges), [(3, 4, False), (4, 3, False)])

    def test_a_loop_starting_inside_a_network_edge_starts_at_that_edge_s_end_and_goes_round_once(self):
        ways = {1: grid_way([1, 2, 3, 4, 5]), 2: grid_way([5, 6, 1]), 9: grid_way([3, 13])}
        trails = {7: TrailLine("red", None, [(2, ""), (1, "")])}
        rows, edges = self.rows(ways, trails, {8: bike_line("blue", "touring", [(9, "")])})
        self.assertEqual(legs_of(rows[0].walk, edges), [(3, 1, False), (1, 3, False)])

    def test_a_gap_stays_before_the_stretch_it_came_before(self):
        ways = {1: grid_way([1, 2, 3]), 2: grid_way([5, 6, 7]), 9: grid_way([2, 12]), 10: grid_way([6, 16])}
        trails = {7: TrailLine("red", None, [(1, ""), (2, "")])}
        rows, edges = self.rows(ways, trails, {8: bike_line("blue", "touring", [(9, ""), (10, "")])})
        self.assertEqual(legs_of(rows[0].walk, edges), [(1, 2, False), (2, 3, False), (5, 6, True), (6, 7, False)])

    def test_a_bike_route_is_not_turned_by_a_trail_s_cut(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4]), 4: grid_way([4, 5]), 9: grid_way([3, 13])}
        bikes = {8: bike_line("blue", "touring", [(3, ""), (4, ""), (2, ""), (1, "")])}
        edges = edges_with_bikes(ways, {7: TrailLine("red", None, [(9, "")])}, bikes)
        rows = network["bike_rows"](bikes, {}, {}, edges, ways, own_bike_edges(ways, bikes))
        self.assertEqual(legs_of(rows[0].walk, edges), [(1, 3, False), (3, 5, False)])


def bowed_way(first: int, last: int, hops: int) -> dict:
    """A long way round from node `first` to node `last`, one way per hop, bowing north between them."""
    refs = [first, *range(1001, 1000 + hops), last]
    nodes = [(ref, 49.0 + 0.001 * min(k, hops - k), 20.0 + 0.001 * k) for k, ref in enumerate(refs)]
    return {100 + k: ([nodes[k], nodes[k + 1]], {}) for k in range(hops)}


class SearchTest(unittest.TestCase):
    def test_cuts_do_not_count_against_the_search(self):
        long_side = bowed_way(2, 3, 70)
        ways = {1: ([(1, 49.0, 19.99), (2, 49.0, 20.0)], {}), 2: ([(2, 49.0, 20.0), (3, 49.0, 20.07)], {}),
                3: ([(3, 49.0, 20.07), (4, 49.0, 20.08)], {}), **long_side}
        trails = {7: TrailLine("red", None, [(1, ""), (2, ""), *((way, "") for way in long_side), (3, "")]),
                  8: TrailLine("blue", None, [(way, "") for way in long_side if way % 2 == 0])}
        walk, _, legs = walked(ways, trails, 7)
        self.assertFalse(walk.estimated)
        self.assertEqual(walk.shape, "branch")
        self.assertEqual(len(legs), 72)
        self.assertEqual((legs[0], legs[1], legs[-2], legs[-1]),
                         ((1, 2, False), (2, 1001, False), (1069, 3, False), (3, 4, False)))

    def test_a_real_network_is_still_past_the_search(self):
        ways = {}
        for k in range(30):
            ways[10 + k] = grid_way([1000 + k, 1001 + k])
            ways[50 + k] = grid_way([2000 + k, 2001 + k])
        for k in range(31):
            ways[90 + k] = grid_way([1000 + k, 2000 + k])
        walk, _, _ = walked(ways, {7: TrailLine("red", None, [(way, "") for way in ways])}, 7)
        self.assertTrue(walk.estimated)


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
        self.assertEqual(row["names"], ["skret", "t1", "twister"])
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
        edges = edges_with_bikes(ways, trails, bikes)
        own_trails = network["own_edges"](trails, ways, network["memberships_of"](trails))
        rows = network["route_rows"](trails, {}, edges, ways, own_trails, {}) + network["bike_rows"](bikes, {}, {}, edges, ways, own_bike_edges(ways, bikes))
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


PAVED, UNPAVED, ROUGH = network["PAVED"], network["UNPAVED"], network["ROUGH"]
FORWARD, BACKWARD = network["FORWARD_ONLY"], network["BACKWARD_ONLY"]


class SurfaceTest(unittest.TestCase):
    def classes(self, key: str, values: list[str], **others) -> list[int]:
        return [network["surface_class"]({key: value, **others}) for value in values]

    def test_each_surface_in_the_table(self):
        self.assertEqual(set(self.classes("surface", ["asphalt", "concrete", "concrete:plates", "concrete:lanes",
                                                      "paving_stones", "sett", "chipseal", "paved", "metal", "wood"])),
                         {PAVED})
        self.assertEqual(set(self.classes("surface", ["compacted", "fine_gravel", "gravel", "unpaved", "dirt", "earth",
                                                      "ground", "grass_paver"])), {UNPAVED})
        self.assertEqual(set(self.classes("surface", ["rock", "stone", "pebblestone", "roots", "mud", "sand", "grass",
                                                      "woodchips"])), {ROUGH})

    def test_each_fallback_by_highway_and_tracktype(self):
        self.assertEqual(set(self.classes("highway", ["primary", "tertiary", "unclassified", "residential",
                                                      "living_street", "secondary_link", "cycleway", "service"])), {PAVED})
        self.assertEqual(set(self.classes("highway", ["path", "footway", "bridleway", "steps"])), {ROUGH})
        self.assertEqual(self.classes("tracktype", ["grade1", "grade2", "grade3", "", "grade4", "grade5"], highway="track"),
                         [PAVED, UNPAVED, UNPAVED, UNPAVED, ROUGH, ROUGH])
        self.assertEqual(network["surface_class"]({}), ROUGH)

    def test_surface_wins_over_highway(self):
        surface_class = network["surface_class"]
        self.assertEqual(surface_class({"highway": "path", "surface": "asphalt"}), PAVED)
        self.assertEqual(surface_class({"highway": "residential", "surface": "gravel"}), UNPAVED)
        self.assertEqual(surface_class({"highway": "track", "tracktype": "grade1", "surface": "roots"}), ROUGH)
        self.assertEqual(surface_class({"highway": "track", "tracktype": "grade1", "surface": "cobblestone"}), PAVED)

    WAYS = {1: way([1, 2], {"highway": "track", "surface": "gravel"}), 2: way([2, 3], {"highway": "track"}),
            3: way([3, 5], {"highway": "track", "tracktype": "grade1"})}

    def ends(self, edges: list) -> list:
        return [(edge.points[0][0], edge.points[-1][0], edge.surface) for edge in edges]

    def test_a_change_of_class_cuts_a_bike_route_s_edge_so_each_has_one(self):
        edges = edges_with_bikes(self.WAYS, {}, {8: bike_line("blue", "touring", [(1, ""), (2, ""), (3, "")])})
        self.assertEqual(self.ends(edges), [(1, 3, UNPAVED), (3, 5, PAVED)])

    def test_trail_only_pieces_of_different_classes_stay_one_edge_with_the_longest_s(self):
        self.assertEqual(self.ends(edges_of(self.WAYS)), [(1, 5, PAVED)])

    def test_the_class_leaves_trail_only_edges_alone_and_cuts_shared_ones(self):
        surfaces = ["asphalt", "gravel", "roots", "asphalt", "sand", "grass", "compacted"]
        ways = {k: way([k, k + 1], {"highway": "path", "surface": surface}) for k, surface in enumerate(surfaces, start=1)}
        ways[8] = way([3, 13], {"highway": "path", "surface": "concrete"})
        trails = {9: TrailLine("red", None, [(1, ""), (2, ""), (3, ""), (8, "")]),
                  10: TrailLine("blue", None, [(4, ""), (5, ""), (6, ""), (7, "")])}
        bikes = {11: bike_line("red", "mtb", [(4, ""), (5, ""), (6, "")])}
        level = {k: (nodes, {}) for k, (nodes, _) in ways.items()}

        def hiking(edges: list) -> list:
            return sorted((tuple(edge.points), edge.ways, edge.climb, edge.flags, edge.sac, edge.trails, edge.sets)
                          for edge in edges if edge.sets == network["SET_TRAIL"])

        def shared(edges: list) -> list:
            trail = network["SET_TRAIL"]
            return sorted(edge.ways for edge in edges if edge.sets & trail and edge.sets != trail)

        with_class, without = edges_with_bikes(ways, trails, bikes), edges_with_bikes(level, trails, bikes)
        self.assertEqual(hiking(with_class), hiking(without))
        self.assertEqual(len(hiking(with_class)), 4)
        self.assertEqual(shared(with_class), [(4,), (5, 6)])
        self.assertEqual(shared(without), [(4, 5, 6)])


def level(metres: float, step: float = 100.0, height: float = 500.0) -> tuple[list[float], list[float]]:
    count = round(metres / step)
    return [step] * count, [height] * (count + 1)


def rising(metres: float, up: float, step: float = 100.0) -> tuple[list[float], list[float]]:
    count = round(metres / step)
    return [step] * count, [500.0 + up * k / count for k in range(count + 1)]


def ride(lengths: list[float], heights: list[float], surface: int = PAVED, mtb: str | None = None,
         mtb_up: str | None = None, **constants) -> tuple[float, float]:
    return network["riding_seconds"](lengths, heights, surface, network["grade"](mtb), network["grade"](mtb_up),
                                     network["RidingConstants"](**constants))


class RidingTimeTest(unittest.TestCase):
    def test_the_flat_at_each_class_s_speed_both_ways(self):
        for surface, kmh in ((PAVED, 20), (UNPAVED, 15), (ROUGH, 10)):
            forward, backward = ride(*level(1000), surface)
            self.assertAlmostEqual(forward, 3600 / kmh)
            self.assertAlmostEqual(backward, 3600 / kmh)

    def test_a_climb_costs_its_metres_and_its_descent_rides_faster(self):
        forward, backward = ride(*rising(1000, 50))
        self.assertAlmostEqual(forward, 180 + 50 * 10)
        self.assertAlmostEqual(backward, 1000 / (30 / 3.6))

    def test_forward_and_backward_swap_climb_and_descent(self):
        up = ride(*rising(1000, 50))
        lengths, heights = rising(1000, 50)
        down = ride(lengths[::-1], heights[::-1])
        self.assertAlmostEqual(up[0], down[1])
        self.assertAlmostEqual(up[1], down[0])

    def test_a_descent_reaches_its_class_s_cap(self):
        _, backward = ride(*rising(1000, 50), UNPAVED, descent_factor=3.0)
        self.assertAlmostEqual(backward, 1000 / (25 / 3.6))
        _, backward = ride(*rising(1000, 50), ROUGH)
        self.assertAlmostEqual(backward, 1000 / (15 / 3.6))

    def test_mtb_scale_caps_the_ride_and_its_uphill_grade_only_uphill(self):
        self.assertEqual(ride(*level(1000), mtb="1"), ride(*level(1000)))
        self.assertAlmostEqual(ride(*level(1000), mtb="2")[0], 1000 / (10 / 3.6))
        self.assertAlmostEqual(ride(*level(1000), mtb="3")[1], 1000 / (6 / 3.6))
        self.assertAlmostEqual(ride(*level(1000), mtb="5")[0], 1000 / (4 / 3.6))
        forward, backward = ride(*rising(1000, 50), mtb="3", mtb_up="4")
        self.assertAlmostEqual(forward, 1000 / (4 / 3.6))
        self.assertAlmostEqual(backward, 1000 / (6 / 3.6))
        forward, backward = ride(*rising(1000, 50), mtb_up="4")
        self.assertAlmostEqual(forward, 1000 / (4 / 3.6))
        self.assertAlmostEqual(backward, 1000 / (30 / 3.6))
        self.assertAlmostEqual(ride(*rising(1000, 50), mtb="4")[0], 1000 / (4 / 3.6))

    def test_a_steep_push_takes_what_walking_it_does(self):
        forward, backward = ride(*rising(100, 30, step=20))
        self.assertAlmostEqual(forward, network["walking_seconds"](100, 30, 0))
        self.assertAlmostEqual(backward, 100 / (30 / 3.6))
        pushed, _ = ride(*rising(100, 30, step=20), pushing_factor=1.1)
        self.assertAlmostEqual(pushed, 1.1 * network["walking_seconds"](100, 30, 0))
        self.assertAlmostEqual(ride(*level(1000), mtb="5", pushing_factor=0.8)[0], 0.8 * 900)

    def test_a_wobble_under_the_dead_band_inside_a_run_adds_nothing(self):
        heights = [500.0 + (3 if k % 2 else 0) for k in range(10)] + [500.0]
        self.assertEqual(ride([100.0] * 10, heights), ride(*level(1000)))

    def test_the_last_run_inside_the_band_is_timed_by_the_change_climb_counts(self):
        lengths, heights = [100.0] * 4, [500.0, 510.0, 510.0, 510.0, 513.0]
        self.assertEqual(network["runs"](lengths, heights), [(100.0, 10.0), (300.0, 3.0)])
        forward, backward = ride(lengths, heights)
        self.assertAlmostEqual(forward, 400 * 0.18 + 13 * 10)
        self.assertAlmostEqual(backward, 400 / (30 / 3.6))

    def test_the_metres_the_time_climbs_are_the_edge_s_ascent(self):
        lengths = [60.0] * 200
        heights = [500 + 20 * math.sin(k / 8) + 2 * (-1) ** k for k in range(201)]
        found = network["climb"](lengths, heights)
        forward, backward = ride(lengths, heights, descent_factor=1.0)
        flat = sum(lengths) * 0.18
        self.assertAlmostEqual(forward - flat, 10 * found.ascent)
        self.assertAlmostEqual(backward - flat, 10 * found.descent)
        self.assertGreater(found.ascent, 100)


def bike_edges(ways: dict, bikes: dict, trails: dict | None = None) -> list:
    return edges_with_bikes(ways, trails or {}, bikes)


def at(edges: list, first: int, last: int):
    return next(edge for edge in edges if {edge.points[0][0], edge.points[-1][0]} == {first, last})


def along(edge, first: int, sense: int) -> int:
    """The sense, given against the way from node `first`, against the edge's own points."""
    return sense if edge.points[0][0] == first else network["TURNED"].get(sense, sense)


class BikeRouteKindsTest(unittest.TestCase):
    def test_a_way_shared_by_a_touring_and_an_mtb_route_carries_both(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        edges = bike_edges(ways, {7: bike_line("red", "touring", [(1, ""), (2, "")]),
                                  8: bike_line("blue", "mtb", [(2, ""), (3, "")])},
                           {9: TrailLine("red", None, [(3, "")])})
        self.assertEqual([at(edges, *ends).sets for ends in ((1, 2), (2, 3), (3, 4))], [2, 6, 5])

    def test_no_riding_seconds_on_an_edge_of_trails_alone(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3])}
        edges = bike_edges(ways, {8: bike_line("blue", "touring", [(2, "")])}, {9: TrailLine("red", None, [(1, "")])})
        trail, bike = at(edges, 1, 2), at(edges, 2, 3)
        self.assertTrue(math.isnan(trail.riding_forward) and math.isnan(trail.riding_backward))
        self.assertFalse(math.isnan(bike.riding_forward) or math.isnan(bike.riding_backward))

    def test_the_build_times_an_edge_by_the_one_function_over_its_measured_points(self):
        ways = {1: grid_way([1, 2, 3], ), 2: (grid_way([3, 13])[0], {"surface": "gravel", "mtb:scale": "3"})}
        edges = bike_edges(ways, {8: bike_line("blue", "mtb", [(1, ""), (2, "")])})
        for edge in edges:
            _, lengths, heights, _ = network["measured"](edge.points, Rising())
            self.assertEqual((edge.riding_forward, edge.riding_backward),
                             network["riding_seconds"](lengths, heights, edge.surface, edge.mtb, edge.mtb_up))
        self.assertEqual(sorted(edge.surface for edge in edges), [UNPAVED, ROUGH])


class RiderDirectionTest(unittest.TestCase):
    def directions(self, tags: dict, *lines) -> tuple[int, int, int]:
        ways = {1: (grid_way([1, 2])[0], tags)}
        edge = bike_edges(ways, dict(enumerate(lines, start=7)))[0]
        return edge.sets, along(edge, 1, edge.ride_touring), along(edge, 1, edge.ride_mtb)

    def line(self, kind: str, sense: int = 0, role: str = ""):
        return bike_line("red", kind, [(1, role)], {1: sense} if sense else {})

    def test_from_the_way_s_tag(self):
        self.assertEqual(self.directions({"oneway:bicycle": "-1"}, self.line("touring"), self.line("mtb")),
                         (6, BACKWARD, BACKWARD))
        self.assertEqual(self.directions({"oneway": "yes"}, self.line("mtb")), (4, 0, FORWARD))

    def test_from_a_forward_role_alone(self):
        self.assertEqual(self.directions({}, self.line("mtb", FORWARD, "forward")), (4, 0, FORWARD))

    def test_from_signed_direction_or_the_relation_s_oneway_alone(self):
        self.assertEqual(self.directions({}, self.line("touring", BACKWARD)), (2, BACKWARD, 0))

    def test_both_ways_where_two_of_a_kind_disagree_or_one_rides_it_both_ways(self):
        self.assertEqual(self.directions({}, self.line("mtb", FORWARD), self.line("mtb", BACKWARD))[2], 0)
        self.assertEqual(self.directions({}, self.line("mtb", FORWARD), self.line("mtb"))[2], 0)
        self.assertEqual(self.directions({}, self.line("mtb", FORWARD), self.line("mtb", FORWARD))[2], FORWARD)

    def test_each_kind_its_own_on_a_way_shared_by_both(self):
        self.assertEqual(self.directions({}, self.line("touring"), self.line("mtb", FORWARD)), (6, 0, FORWARD))
        self.assertEqual(self.directions({}, self.line("touring", BACKWARD), self.line("mtb", FORWARD)),
                         (6, BACKWARD, FORWARD))

    def test_a_direction_follows_the_edge_when_its_way_is_drawn_against_it(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([3, 2])}
        line = bike_line("red", "mtb", [(1, ""), (2, "")], {1: FORWARD, 2: BACKWARD})
        edge = bike_edges(ways, {7: line})[0]
        self.assertEqual(along(edge, 1, edge.ride_mtb), FORWARD)


def read_network(data: bytes) -> dict:
    """The format back from its bytes, every array by name, as TrailNetwork.kt reads it."""
    version, _, nodes, edges, points = struct.unpack_from("<HHIII", data, 4)
    layout = [("way", "q", edges), ("node_lat", "i", nodes), ("node_lon", "i", nodes), ("from", "I", edges),
              ("to", "I", edges), ("first", "I", edges), ("count", "I", edges), ("length", "f", edges),
              ("seconds_forward", "f", edges), ("seconds_backward", "f", edges), ("ascent", "f", edges),
              ("descent", "f", edges), ("riding_forward", "f", edges), ("riding_backward", "f", edges),
              ("point_lat", "i", points), ("point_lon", "i", points), ("node_height", "h", nodes),
              ("point_height", "h", points), ("flags", "B", edges), ("sac", "B", edges), ("sets", "B", edges),
              ("ride", "B", edges), ("mtb", "B", edges), ("mtb_up", "B", edges), ("surface", "B", edges),
              ("ride_touring", "B", edges), ("ride_mtb", "B", edges)]
    found, pos = {"magic": data[:4], "version": version}, 20
    for name, kind, count in layout:
        found[name] = struct.unpack_from(f"<{count}{kind}", data, pos)
        pos += struct.calcsize(f"<{count}{kind}")
    found["left"] = len(data) - pos
    return found


class FormatTest(unittest.TestCase):
    def test_format_3_written_and_read_back(self):
        edges = fixture_edges()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "trails.network"
            network["write_network"](edges, target)
            read = read_network(target.read_bytes())
        self.assertEqual((read["magic"], read["version"], read["left"]), (b"KZTN", 3, 0))
        self.assertEqual(read["sets"], (1, 1, 1, 1, 1, 3, 1, 4, 3, 3, 3, 6, 4, 2, 2, 2, 4) + (2,) * 5 + (4,))
        self.assertEqual(read["surface"], (ROUGH, ROUGH, ROUGH, ROUGH, ROUGH, PAVED, ROUGH, UNPAVED, ROUGH, PAVED,
                                           PAVED, UNPAVED, ROUGH, PAVED, UNPAVED, ROUGH, ROUGH) + (PAVED,) * 6)
        self.assertEqual(read["ride_touring"], (0,) * 18 + (FORWARD, FORWARD) + (0,) * 3)
        self.assertEqual(read["ride_mtb"], (0,) * 7 + (FORWARD,) + (0,) * 3 + (FORWARD,) + (0,) * 11)
        riding = list(zip(read["riding_forward"], read["riding_backward"]))
        self.assertTrue(all(math.isnan(f) and math.isnan(b) for k, (f, b) in enumerate(riding) if k < 8 and k not in (5, 7)))
        self.assertFalse(any(math.isnan(f) or math.isnan(b) for f, b in riding[8:]))
        self.assertAlmostEqual(riding[5][0], edges[5].length / (network["RIDING"].flat[PAVED] / 3.6), places=2)
        self.assertAlmostEqual(riding[7][1], edges[7].length / (10 / 3.6), places=2)


def read_walks(data: bytes) -> dict:
    """`trails.edges` format 2 read back: each route's walk and back walk as (edge, reversed, gap before) steps."""
    version, _, routes, total = struct.unpack_from("<HHII", data, 4)
    ids = struct.unpack_from(f"<{routes}q", data, 16)
    arrays = [struct.unpack_from(f"<{routes}I", data, 16 + 8 * routes + 4 * routes * k) for k in range(4)]
    steps = struct.unpack_from(f"<{total}I", data, 16 + 24 * routes)
    assert 16 + 24 * routes + 4 * total == len(data)

    def of(first: int, count: int) -> list:
        return [(step >> 2, bool(step & 1), bool(step & 2)) for step in steps[first:first + count]]

    return {"version": version, **{number: (of(first, count), of(back_first, back_count))
                                   for number, first, count, back_first, back_count in zip(ids, *arrays)}}


class WalkEachWayTest(unittest.TestCase):
    """docs/phase-7b.md § 6: a Bike route's walk for each direction it is ridden, each keeping to the rider."""
    forward = network["FORWARD_ONLY"]

    def row(self, ways: dict, line, number: int = 7):
        bikes = {number: line}
        edges = edges_with_bikes(ways, {}, bikes)
        return network["bike_rows"](bikes, {}, {}, edges, ways, own_bike_edges(ways, bikes))[0], edges

    def against(self, row, edges: list, steps: list) -> list:
        return [step for step in steps if not network["rider_may"](row.line.kind, edges[step.edge], step)]

    def strands(self, steps: list, edges: list) -> set:
        return {way for step in steps for way in edges[step.edge].ways if way in (2, 3)}

    def test_a_pair_of_one_way_strands_gives_each_way_its_own(self):
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), (3, *place(3))], {"oneway": "yes"}),
                3: ([(3, *place(3)), (900, 49.0005, 20.025), (2, *place(2))], {"oneway": "yes"}), 4: grid_way([3, 4])}
        line = bike_line("red", "touring", [(1, ""), (2, "forward"), (3, "forward"), (4, "")],
                         {2: self.forward, 3: self.forward})
        row, edges = self.row(ways, line)
        self.assertFalse(row.one_way)
        self.assertEqual(self.against(row, edges, row.walk.steps), [])
        self.assertEqual(self.against(row, edges, row.back), [])
        self.assertEqual(len(self.strands(row.walk.steps, edges)), 1)
        self.assertEqual(len(self.strands(row.back, edges)), 1)
        self.assertNotEqual(self.strands(row.walk.steps, edges), self.strands(row.back, edges))
        self.assertFalse(any(step.breaks for step in (*row.walk.steps, *row.back)))
        self.assertEqual(network["own_back"](row), row.back)
        self.assertEqual(network["against_the_ride"](row, edges), [])

    def test_a_one_way_way_with_no_other_strand_breaks_the_back_walk_there(self):
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), (3, *place(3))], {"oneway": "yes"}), 3: grid_way([3, 4])}
        row, edges = self.row(ways, bike_line("red", "touring", [(1, ""), (2, ""), (3, "")], {2: self.forward}))
        self.assertFalse(row.one_way)
        self.assertEqual(legs_of(row.walk, edges), [(1, 2, False), (2, 3, False), (3, 4, False)])
        self.assertEqual(legs_of(network["Walk"](row.back, "line"), edges), [(4, 3, False), (2, 1, True)])

    def test_a_one_way_route_has_no_back_walk_and_its_walk_keeps_to_its_direction(self):
        ways = {1: grid_way([1, 2]), 2: grid_way([2, 3]), 3: grid_way([3, 4])}
        backward = network["BACKWARD_ONLY"]
        row, edges = self.row(ways, bike_line("red", "mtb", [(1, ""), (2, ""), (3, "")], {1: backward, 2: backward,
                                                                                        3: backward}))
        self.assertTrue(row.one_way)
        self.assertIsNone(row.back)
        self.assertEqual(network["own_back"](row), [])
        self.assertEqual(legs_of(row.walk, edges), [(4, 1, False)])
        self.assertEqual(network["against_the_ride"](row, edges), [])

    def test_a_run_with_another_strand_is_swapped_and_one_without_is_dropped(self):
        Step = network["Step"]
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), (3, *place(3))], {"oneway": "-1"}),
                3: ([(2, *place(2)), (900, 49.0005, 20.025), (3, *place(3))], {}), 4: grid_way([3, 4]),
                5: ([(4, *place(4)), (5, *place(5))], {"oneway": "-1"}), 6: grid_way([5, 6])}
        line = bike_line("red", "touring", [(way, "") for way in ways])
        edges = edges_with_bikes(ways, {}, {7: line})
        index = {edge.ways[0]: i for i, edge in enumerate(edges)}
        own = list(range(len(edges)))
        walked = network["with_the_ride"]("touring", [Step(index[way], False) for way in (1, 2, 4, 5, 6)], edges, own)
        self.assertEqual(legs_of(network["Walk"](walked, "line"), edges),
                         [(1, 2, False), (2, 3, False), (3, 4, False), (5, 6, True)])
        self.assertEqual(edges[walked[1].edge].ways, (3,))

    def test_a_loop_round_a_one_way_street_breaks_there_rather_than_riding_the_rest_of_the_loop_back(self):
        Step = network["Step"]
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), (12, *place(12))], {"oneway": "-1"}),
                3: grid_way([12, 11]), 4: grid_way([11, 1]), 5: grid_way([1, 0]), 6: grid_way([12, 13])}
        edges = edges_with_bikes(ways, {}, {7: bike_line("red", "touring", [(way, "") for way in (1, 2, 3, 4)])})
        index = {edge.ways[0]: i for i, edge in enumerate(edges)}
        own = [index[way] for way in (1, 2, 3)]
        walked = network["with_the_ride"]("touring", [Step(i, False) for i in own], edges, own)
        self.assertEqual(legs_of(network["Walk"](walked, "loop"), edges), [(1, 2, False), (12, 1, True)])

    def widened(self, ways: dict, legs: list) -> tuple[list, list]:
        Step = network["Step"]
        edges = edges_with_bikes(ways, {}, {7: bike_line("red", "touring", [(way, "") for way in ways])})
        ends = {(edge.points[0][0], edge.points[-1][0]): i for i, edge in enumerate(edges)}
        steps = [Step(ends[(a, b)], False) if (a, b) in ends else Step(ends[(b, a)], True) for a, b in legs]
        return network["with_the_ride"]("touring", steps, edges, list(range(len(edges)))), edges

    def assert_ridden_once_with_the_ride(self, walked: list, edges: list):
        self.assertEqual(len({step.edge for step in walked}), len(walked))
        self.assertTrue(all(network["rider_may"]("touring", edges[step.edge], step) for step in walked))

    def test_a_fork_one_two_way_edge_before_the_run_is_repaired_over_the_other_strand(self):
        fork = (900, 49.0, 20.0203)
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), fork], {}),
                3: ([fork, (3, *place(3))], {"oneway": "-1"}),
                4: ([(2, *place(2)), (901, 49.0006, 20.025), (3, *place(3))], {"oneway": "yes"}), 5: grid_way([3, 4])}
        walked, edges = self.widened(ways, [(1, 2), (2, 900), (900, 3), (3, 4)])
        self.assertEqual(legs_of(network["Walk"](walked, "line"), edges), [(1, 2, False), (2, 3, False), (3, 4, False)])
        self.assertEqual(edges[walked[1].edge].ways, (4,))
        self.assert_ridden_once_with_the_ride(walked, edges)

    def test_a_roundabout_reached_against_its_exit_slip_road_is_repaired_over_the_entry(self):
        p, q, t = (901, 49.0001, 20.021), (902, 48.9999, 20.021), (903, 49.0, 20.022)
        ways = {1: grid_way([1, 2]), 2: ([p, q, t, p], {"junction": "roundabout"}),
                3: ([p, (2, *place(2))], {"oneway": "yes"}), 4: ([(2, *place(2)), q], {"oneway": "yes"}),
                5: ([t, (4, *place(4))], {})}
        walked, edges = self.widened(ways, [(1, 2), (2, 901), (901, 902), (902, 903), (903, 4)])
        self.assertEqual(legs_of(network["Walk"](walked, "line"), edges),
                         [(1, 2, False), (2, 902, False), (902, 903, False), (903, 4, False)])
        self.assert_ridden_once_with_the_ride(walked, edges)

    def test_a_loop_walked_against_the_rider_is_ridden_round_the_other_way_rather_than_dropped(self):
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), (11, *place(11))], {"oneway": "yes"}),
                3: ([(11, *place(11)), (12, *place(12))], {"oneway": "yes"}),
                4: ([(12, *place(12)), (2, *place(2))], {"oneway": "yes"}), 5: grid_way([2, 3]),
                6: grid_way([11, 13]), 7: grid_way([12, 0])}
        walked, edges = self.widened(ways, [(1, 2), (2, 12), (12, 11), (11, 2), (2, 3)])
        self.assertEqual(legs_of(network["Walk"](walked, "line"), edges),
                         [(1, 2, False), (2, 11, False), (11, 12, False), (12, 2, False), (2, 3, False)])
        self.assert_ridden_once_with_the_ride(walked, edges)

    def test_a_widened_loop_the_rider_may_take_neither_way_round_is_left_out(self):
        a, b = (901, 49.0005, 20.0205), (902, 49.0005, 20.0195)
        ways = {1: grid_way([1, 2]), 2: ([(2, *place(2)), a], {"oneway": "yes"}), 3: ([b, a], {"oneway": "yes"}),
                4: ([b, (2, *place(2))], {}), 5: grid_way([2, 3])}
        walked, edges = self.widened(ways, [(1, 2), (2, 901), (901, 902), (902, 2), (2, 3)])
        self.assertEqual(legs_of(network["Walk"](walked, "line"), edges), [(1, 2, False), (2, 3, False)])
        self.assert_ridden_once_with_the_ride(walked, edges)

    def test_a_trail_s_back_walk_is_its_walk_turned(self):
        rows = fixture_walks()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "walks"
            network["write_walks"](rows, target)
            read = read_walks(target.read_bytes())
        self.assertEqual(read["version"], 2)
        for number in (7, 8, 9):
            self.assertEqual(read[number][1], [])
        self.assertEqual(read[8][0], [(2, True, False), (1, False, True)])
        self.assertEqual(read[11], ([(17, False, False), (18, False, False), (20, False, False)],
                                    [(20, True, False), (19, False, False), (17, True, False)]))

    def test_a_back_walk_that_is_the_walk_turned_is_written_as_none(self):
        Step, Walk, TrailRow = network["Step"], network["Walk"], network["TrailRow"]
        there = [Step(0, False), Step(1, False, True)]
        row = TrailRow(5, {}, TrailLine("red", None, []), Walk(there, "gaps"), None,
                       back=network["turned_steps"](there))
        self.assertEqual(network["own_back"](row), [])

    def test_an_untagged_roundabout_is_one_way_for_a_rider_and_two_way_for_a_walker(self):
        ride, oneway, forward = network["ride"], network["oneway"], network["FORWARD_ONLY"]
        for junction in ("roundabout", "circular"):
            self.assertEqual(ride({"highway": "tertiary", "junction": junction}), forward)
            self.assertEqual(ride({"highway": "tertiary", "junction": junction, "oneway": "no"}), 0)
            self.assertEqual(ride({"highway": "tertiary", "junction": junction, "oneway:bicycle": "no"}), 0)
            self.assertEqual(ride({"highway": "tertiary", "junction": junction, "oneway": "-1"}), network["BACKWARD_ONLY"])
            self.assertEqual(oneway({"highway": "footway", "junction": junction}), 0)
        ring = {1: ([(1, *place(1)), (2, *place(2)), (12, *place(12)), (1, *place(1))],
                    {"highway": "tertiary", "junction": "roundabout"})}
        edges = edges_with_bikes(ring, {}, {7: bike_line("red", "touring", [(1, "")])})
        self.assertTrue(all(edge.ride_touring == forward and edge.flags == 0 for edge in edges))


class RidingFixtureTest(unittest.TestCase):
    def test_every_riding_constant_moves_a_stored_second(self):
        """CyclingRouterTest holds EdgePart.kt's mirror to the fixture's seconds within 0.05 s, so a constant no
        ride depends on could drift there unseen."""
        rides = [e for e in fixture_edges() if not math.isnan(e.riding_forward)]
        lengths = [[network["distance"](*a[1:], *b[1:]) for a, b in zip(e.points, e.points[1:])] for e in rides]

        stored = [s for e in rides for s in (e.riding_forward, e.riding_backward)]

        def moves(constants) -> bool:
            seconds = [s for e, ls in zip(rides, lengths)
                       for s in network["riding_seconds"](ls, e.heights, e.surface, e.mtb, e.mtb_up, constants)]
            return max(abs(a - b) for a, b in zip(seconds, stored)) > 0.05

        riding = network["RIDING"]
        for field in fields(riding):
            value = getattr(riding, field.name)
            for k in range(len(value)) if isinstance(value, tuple) else [None]:
                def scaled(factor: float):
                    if k is None:
                        return value * factor
                    return value[:k] + (value[k] * factor,) + value[k + 1:]
                with self.subTest(field=field.name, index=k):
                    self.assertTrue(any(moves(replace(riding, **{field.name: scaled(f)})) for f in (0.8, 1.25)))


if __name__ == "__main__":
    if sys.argv[1:] == ["--write"]:
        FIXTURE.parent.mkdir(parents=True, exist_ok=True)
        print(network["write_network"](fixture_edges(), FIXTURE))
        print(network["write_walks"](fixture_walks(), WALKS_FIXTURE))
    else:
        unittest.main()
