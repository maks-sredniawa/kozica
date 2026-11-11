"""docs/phase-6c.md § Tests, a regional Trail in pieces, and docs/phase-7a.md § Tests, Bike routes, for
KozicaProfile.java:

    python3 -m unittest scripts/test_kozica_profile.py
    map-data/network-venv/bin/python scripts/test_kozica_profile.py --write    # rewrites the fixtures

Runs the profile over a hand-made cut, so it needs Java and the planetiler jar build-map-data.py pins;
without them it is skipped. Writing the fixture needs pyosmium.
"""

import math
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "scripts/map/pieces-fixture.osm.pbf"
BIKES_FIXTURE = ROOT / "scripts/map/bike-routes-fixture.osm.pbf"
PROFILE = ROOT / "scripts/map/KozicaProfile.java"
PLANETILER = ROOT / "map-data/planetiler-0.10.2.jar"
decode = runpy.run_path(str(ROOT / "scripts/map/decode-tile.py"), run_name="test")

ZOOM = 14
LAT, LON = 49.25, 20.0
BAR, TRIANGLE = "red:white:red_bar", "red:white:red_triangle"

RELATIONS = {
    100: ("rwn", BAR, ["w1", "w2", "r101", "r102", "r103", "r106"]),
    101: ("rwn", BAR, ["w2", "w3"]),
    102: ("rwn", BAR, ["w4"]),
    103: ("rwn", BAR, ["w5"]),
    104: ("rwn", BAR, ["w6", "r103"]),
    106: ("rwn", TRIANGLE, ["w7"]),
    107: ("rwn", "blue:white:blue_bar", ["r108"]),
    108: ("rwn", "green:white:green_bar", ["w8"]),
    109: ("nwn", "blue:white:blue_bar", ["r110", "r111"]),
    110: ("nwn", "blue:white:blue_bar", ["w9"]),
    111: ("nwn", "blue:white:blue_bar", ["w10"]),
    112: ("nwn", BAR, ["w11", "r113/excursion"]),
    113: ("lwn", BAR, ["w12"]),
}
"""r100 is a bar-marked parent with ways of its own, w2 also in its piece r101, and pieces r101 to r103,
r103 also in a second parent r104; r106 is a triangle-marked spur. r107 has no ways and its only member
is marked otherwise. r109 is an `nwn` parent of the same shape as r100, so a Long-distance Trail. r112 is
KČT's main Trail, `nwn`, holding its spur as an `excursion`."""

WAYS = 12


def write_fixture() -> None:
    import osmium

    FIXTURE.unlink(missing_ok=True)
    with osmium.SimpleWriter(str(FIXTURE)) as writer:
        for way in range(1, WAYS + 1):
            for end in (0, 1):
                writer.add_node(osmium.osm.mutable.Node(
                    id=2 * way + end, version=1, location=(LON + way / 1000, LAT + end / 2000)))
        for way in range(1, WAYS + 1):
            writer.add_way(osmium.osm.mutable.Way(
                id=way, version=1, nodes=[2 * way, 2 * way + 1], tags={"highway": "path"}))
        for number, (network, symbol, members) in RELATIONS.items():
            parsed = []
            for member in members:
                ref, _, role = member.partition("/")
                parsed.append((ref[0], int(ref[1:]), role))
            writer.add_relation(osmium.osm.mutable.Relation(
                id=number, version=1, members=parsed,
                tags={"type": "route", "route": "hiking", "network": network, "osmc:symbol": symbol}))


def bike(kind: str, members: list[str], **tags) -> dict:
    return {"type": "route", "route": kind, **{key.replace("_", ":") if key.startswith("oneway") else key: value
                                               for key, value in tags.items()}, "members": members}


BIKE_WAYS = 41
"""Each way two nodes of its own, but for the chains CHAINED names."""
CHAINED = {7: (700, 701), 8: (702, 701), 10: (1000, 1001), 9: (1002, 1001), 38: (3800, 3801), 39: (3802, 3801)}
BIKE_WAY_TAGS = {
    1: {"oneway:bicycle": "yes", "oneway": "-1"},
    2: {"oneway:bicycle": "-1", "oneway": "yes"},
    3: {"oneway:bicycle": "no", "oneway": "yes"},
    4: {"oneway": "-1"},
    9: {"oneway": "yes"},
}
BIKE_RELATIONS = {
    201: bike("mtb", ["w1"]),
    202: bike("mtb", ["w2"]),
    203: bike("mtb", ["w3"]),
    204: bike("bicycle", ["w4"]),
    205: bike("mtb", ["w5/forward"]),
    206: bike("mtb", ["w6/backward"]),
    207: bike("mtb", ["w7", "w8"], signed_direction="yes"),
    208: bike("mtb", ["w9"]),
    209: bike("bicycle", ["w11"], colour="red"),
    210: bike("mtb", ["w11"], colour="blue"),
    220: bike("bicycle", ["r221", "r222"], network="ncn", colour="red"),
    221: bike("bicycle", ["w12"], colour="red"),
    222: bike("bicycle", ["w13"], colour="red"),
    223: bike("bicycle", ["r224", "r225/excursion"], network="ncn", colour="orange"),
    224: bike("bicycle", ["w14"]),
    225: bike("bicycle", ["w15"]),
    226: bike("bicycle", ["r227/connection"], network="ncn"),
    227: bike("bicycle", ["w16"]),
    228: bike("bicycle", ["r229/excursion"], network="icn"),
    229: bike("bicycle", ["w17"]),
    230: bike("mtb", ["r231", "r232"], network="lcn"),
    231: bike("mtb", ["w18"], colour="red"),
    232: bike("mtb", ["w19"], colour="blue"),
    233: bike("mtb", ["r234"], network="lcn", colour="red"),
    234: bike("mtb", ["w20"], colour="blue"),
    235: bike("bicycle", ["r236", "r237"], network="rcn", colour="red"),
    236: bike("bicycle", ["w21"], colour="#ff0000"),
    237: bike("bicycle", ["w22"], colour="blue"),
    238: bike("bicycle", ["w23/part", "r239/part"], network="icn"),
    239: bike("bicycle", ["w24"]),
    240: {**bike("mtb", ["r241"], network="lcn"), "type": "site"},
    241: bike("mtb", ["w25"]),
    243: {**bike("bicycle", ["r244"], network="rcn"), "type": "network"},
    244: bike("bicycle", ["w26"]),
    245: bike("mtb", ["r246", "r247"], colour="purple"),
    246: bike("mtb", ["w27"], colour="orange"),
    247: bike("mtb", ["w28"], colour="orange"),
    250: bike("bicycle", ["w29"], state="abandoned"),
    251: bike("mtb", ["w30"], state="proposed"),
    252: bike("bicycle", ["w31", "r253"], network="icn", state="abandoned"),
    253: bike("bicycle", ["w32"]),
    254: bike("bicycle", ["w33"], state="recommended"),
    255: bike("mtb", ["w37"], oneway="yes"),
    256: bike("mtb", ["w38", "w39"], oneway="-1"),
    257: bike("mtb", ["w40"], oneway="no"),
    258: bike("mtb", ["w41/backward"], oneway="yes"),
    301: {"type": "route", "route": "hiking", "network": "rwn", "colour": "green", "members": ["w10", "w9"]},
    302: {"type": "route", "route": "hiking", "network": "rwn", "colour": "red", "members": ["w11"]},
    303: {"type": "route", "route": "hiking", "network": "rwn", "colour": "blue", "members": ["w11"]},
    304: {"type": "route", "route": "hiking", "network": "nwn", "colour": "red", "state": "proposed", "members": ["w34"]},
    305: {"type": "route", "route": "hiking", "network": "rwn", "colour": "red", "state": "abandoned", "members": ["w35"]},
    306: {"type": "route", "route": "hiking", "network": "rwn", "colour": "blue", "members": ["w35"]},
    307: {"type": "superroute", "route": "hiking", "network": "nwn", "state": "proposed", "members": ["r308"]},
    308: {"type": "route", "route": "hiking", "network": "nwn", "colour": "red", "members": ["w36"]},
}
"""w1 to w4 the way's own direction; w5, w6 a role's; w7, w8 `signed_direction` in a member order that w8 runs
against; w9 a way drawn reversed by the Trail r301, whose order it continues head to head with w10; w11 two
Trails and two Bike routes, one of each red. r220 to r229 `icn`/`ncn` parents; r230 to r237 the others, r235
mixed; r238 a parent with a Section and a way of its own; r240 and r243 the `type`s that are not routes, without
a colour; r245's members share a colour outside the five, not r245's. r250 to r254 and r304 to r308 `state`.
r255 to r258 the relation's own `oneway`: w38, w39 in a member order that w39 runs against; w41's role over it."""


def write_bikes_fixture() -> None:
    import osmium

    STEP = 1 / 3000
    BIKES_FIXTURE.unlink(missing_ok=True)
    with osmium.SimpleWriter(str(BIKES_FIXTURE)) as writer:
        nodes = {}
        for way in range(1, BIKE_WAYS + 1):
            for end, ref in enumerate(CHAINED.get(way, (2 * way, 2 * way + 1))):
                nodes.setdefault(ref, (LON + way * STEP, LAT + end / 2000))
        for ref in sorted(nodes):
            writer.add_node(osmium.osm.mutable.Node(id=ref, version=1, location=nodes[ref]))
        for way in range(1, BIKE_WAYS + 1):
            writer.add_way(osmium.osm.mutable.Way(
                id=way, version=1, nodes=list(CHAINED.get(way, (2 * way, 2 * way + 1))),
                tags={"highway": "path", **BIKE_WAY_TAGS.get(way, {})}))
        for number, relation in sorted(BIKE_RELATIONS.items()):
            tags = {key: value for key, value in relation.items() if key != "members"}
            members = []
            for member in relation["members"]:
                ref, _, role = member.partition("/")
                members.append((ref[0], int(ref[1:]), role))
            writer.add_relation(osmium.osm.mutable.Relation(id=number, version=1, members=members, tags=tags))


def tile_of(lat: float, lon: float) -> tuple[int, int]:
    n = 1 << ZOOM
    y = (1 - math.log(math.tan(math.radians(lat)) + 1 / math.cos(math.radians(lat))) / math.pi) / 2 * n
    return int((lon + 180) / 360 * n), int(y)


def built(out: Path, fixture: Path, east: float) -> tuple[list[str], dict[str, dict[int, list[dict]]]]:
    """The fixture's `.trails` lines, and its tile's features by layer and way."""
    bounds = f"{LON},{LAT},{LON + east},{LAT + 1 / 1000}"
    subprocess.run(
        ["java", "-cp", PLANETILER, PROFILE, f"--osm_path={fixture}", f"--output={out / 'fixture.pmtiles'}",
         f"--tmpdir={out / 'tmp'}", f"--trails={out / 'fixture.trails'}", f"--bounds={bounds}",
         "--tile_compression=none", f"--maxzoom={ZOOM}"],
        check=True, capture_output=True)
    archive = (out / "fixture.pmtiles").read_bytes()
    tile = decode["find_tile"](archive, decode["tile_id"](ZOOM, *tile_of(LAT, LON + east / 2)))
    layers: dict[str, dict[int, list[dict]]] = {}
    for number, _, value in decode["fields"](tile):
        if number == 3:
            name, features = decode["layer"](value)
            for _, feature_id, attributes in features:
                layers.setdefault(name, {}).setdefault(feature_id // 10, []).append(attributes)
    return (out / "fixture.trails").read_text().splitlines(), layers


@unittest.skipUnless(PLANETILER.is_file() and shutil.which("java"),
                     "needs Java and the planetiler jar; `python3 scripts/build-map-data.py` fetches it")
class PiecesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.directory = tempfile.TemporaryDirectory()
        cls.lines, layers = built(Path(cls.directory.name), FIXTURE, (WAYS + 1) / 1000)
        cls.features = layers["trails"]

    @classmethod
    def tearDownClass(cls) -> None:
        cls.directory.cleanup()

    def test_the_parent_is_one_trail_over_its_ways_and_its_pieces(self):
        self.assertIn("trail\t100\tred\tred_bar\t1 2 3 4 5", self.lines)
        self.assertIn("trail\t104\tred\tred_bar\t6 5", self.lines)

    def test_a_piece_is_no_trail_and_a_spur_is_its_own(self):
        trails = {line.split("\t")[1] for line in self.lines if line.startswith("trail")}
        self.assertEqual(trails, {"100", "104", "106", "108", "110", "111", "112"})
        self.assertIn("trail\t106\tred\tred_triangle\t7", self.lines)

    def test_a_piece_s_ways_carry_the_parent_once(self):
        trails_on = {way: sorted(a["trail"] for a in found) for way, found in self.features.items()}
        self.assertEqual(trails_on[2], [100])
        self.assertEqual(trails_on[3], [100])
        self.assertEqual(trails_on[5], [100, 104])
        self.assertEqual(trails_on[7], [106])
        self.assertEqual([a["count"] for a in self.features[2]], [1])

    def test_a_parent_marked_otherwise_than_its_only_member_is_nothing(self):
        self.assertFalse(any("\t107\t" in line for line in self.lines))
        self.assertEqual([a["trail"] for a in self.features[8]], [108])

    def test_an_nwn_parent_is_a_long_distance_trail_and_kct_s_spur_its_trail_s(self):
        self.assertIn("long\t109\t110 111", self.lines)
        self.assertIn("trail\t112\tred\tred_bar\t11 12/excursion", self.lines)
        self.assertEqual([a["trail"] for a in self.features[12]], [112])


@unittest.skipUnless(PLANETILER.is_file() and shutil.which("java"),
                     "needs Java and the planetiler jar; `python3 scripts/build-map-data.py` fetches it")
class BikeRoutesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.directory = tempfile.TemporaryDirectory()
        cls.lines, layers = built(Path(cls.directory.name), BIKES_FIXTURE, (BIKE_WAYS + 1) / 3000)
        cls.bikes = layers["bike_routes"]
        cls.trails = layers["trails"]
        cls.rows = {int(line.split("\t")[1]): line.split("\t") for line in cls.lines if line.startswith("bike\t")}
        cls.sections = {int(line.split("\t")[1]): line.split("\t")[2] for line in cls.lines
                        if line.startswith("sections\t")}

    @classmethod
    def tearDownClass(cls) -> None:
        cls.directory.cleanup()

    def dir_on(self, way: int) -> str | None:
        return self.bikes[way][0].get("dir")

    def test_a_feature_says_what_the_style_and_the_tap_read(self):
        red = self.bikes[12][0]
        self.assertEqual((red["bike_route"], red["kind"], red["colour"], red["network"] if "network" in red else None),
                         (221, "touring", "red", None))
        self.assertEqual(self.bikes[1][0]["kind"], "mtb")
        self.assertEqual(self.bikes[27][0]["colour"], "other")

    def test_oneway_bicycle_wins_over_oneway_both_senses_and_no(self):
        self.assertEqual(self.dir_on(1), "forward")
        self.assertEqual(self.dir_on(2), "backward")
        self.assertIsNone(self.dir_on(3))

    def test_oneway_on_any_highway_and_the_roles(self):
        self.assertEqual(self.dir_on(4), "backward")
        self.assertEqual(self.dir_on(5), "forward")
        self.assertEqual(self.dir_on(6), "backward")

    def test_two_way_ways_carry_no_dir(self):
        self.assertNotIn("dir", self.bikes[12][0])

    def test_signed_direction_is_the_member_order_against_each_way_as_drawn(self):
        self.assertEqual(self.dir_on(7), "forward")
        self.assertEqual(self.dir_on(8), "forward")
        self.assertEqual(self.rows[207][4], "7@f 8@b")

    def test_the_relation_s_own_oneway_is_its_member_order_and_minus_one_the_reverse(self):
        self.assertEqual(self.rows[255][4], "37@f")
        self.assertEqual(self.rows[256][4], "38@b 39@f")
        self.assertEqual((self.dir_on(38), self.dir_on(39)), ("backward", "backward"))
        self.assertEqual(self.rows[257][4], "40")
        self.assertIsNone(self.dir_on(40))
        self.assertEqual(self.rows[258][4], "41/backward@b")

    def test_a_way_drawn_reversed_by_the_union_s_reference_turns_its_dir(self):
        self.assertEqual(self.dir_on(9), "backward")
        self.assertEqual(self.rows[208][4], "9@f")

    def test_one_order_over_both_kinds(self):
        trails = {a["trail"]: a for a in self.trails[11]}
        bikes = {a["bike_route"]: a for a in self.bikes[11]}
        self.assertEqual((trails[302]["index"], trails[302]["count"]), (0, 2))
        self.assertEqual((trails[303]["index"], trails[303]["count"]), (1, 2))
        self.assertEqual((trails[302]["union_index"], trails[302]["union_count"]), (0, 4))
        self.assertEqual((trails[303]["union_index"], trails[303]["union_count"]), (1, 4))
        self.assertEqual((bikes[209]["index"], bikes[209]["count"]), (2, 4))
        self.assertEqual((bikes[210]["index"], bikes[210]["count"]), (3, 4))

    def test_an_ncn_parent_has_sections_whatever_their_marking(self):
        self.assertEqual(self.sections[220], "221 222")
        self.assertNotIn(220, self.rows)
        self.assertEqual([a["bike_route"] for a in self.bikes[12]], [221])

    def test_an_ncn_parent_s_excursion_is_its_own_outside_the_whole(self):
        self.assertEqual(self.sections[223], "224")
        self.assertIn(225, self.rows)
        self.assertEqual([a["bike_route"] for a in self.bikes[15]], [225])

    def test_a_connection_is_a_section_and_an_excursion_alone_no_piece(self):
        self.assertEqual(self.sections[226], "227")
        self.assertNotIn(228, self.sections)
        self.assertNotIn(228, self.rows)
        self.assertEqual([a["bike_route"] for a in self.bikes[17]], [229])

    def test_an_lcn_parent_with_all_members_marked_otherwise_has_sections(self):
        self.assertEqual(self.sections[230], "231 232")
        self.assertEqual(self.sections[233], "234")

    def test_a_mixed_rcn_parent_takes_the_same_marked_member_as_a_piece(self):
        self.assertEqual([a["bike_route"] for a in self.bikes[21]], [235])
        self.assertEqual(self.rows[235][4], "21")
        self.assertNotIn(236, self.rows)
        self.assertIn(237, self.rows)
        self.assertNotIn(235, self.sections)

    def test_a_parent_with_sections_keeps_a_row_for_its_own_ways(self):
        self.assertEqual(self.rows[238][4], "23")
        self.assertEqual(self.sections[238], "239")
        self.assertEqual([a["bike_route"] for a in self.bikes[23]], [238])

    def test_a_site_or_a_network_is_read_like_any_parent(self):
        self.assertEqual(self.sections[240], "241")
        self.assertEqual(self.sections[243], "244")
        self.assertNotIn(240, self.rows)

    def test_two_colours_outside_the_five_are_two_markings(self):
        self.assertEqual(self.sections[245], "246 247")

    def test_abandoned_and_proposed_are_not_kept_but_recommended_is(self):
        self.assertNotIn(29, self.bikes)
        self.assertNotIn(30, self.bikes)
        self.assertEqual(sorted(number for number in (250, 251, 252, 254) if number in self.rows), [254])

    def test_a_dropped_parent_leaves_its_section_on_its_own(self):
        self.assertNotIn(252, self.sections)
        self.assertNotIn(31, self.bikes)
        self.assertEqual([a["bike_route"] for a in self.bikes[32]], [253])

    def test_a_dropped_trail_is_gone_but_a_live_one_on_its_way_stays(self):
        self.assertNotIn(34, self.trails)
        self.assertEqual([(a["trail"], a["count"]) for a in self.trails[35]], [(306, 1)])
        self.assertFalse(any(line.split("\t")[:2] == ["trail", "304"] for line in self.lines))
        self.assertFalse(any(line.startswith("long\t307") for line in self.lines))
        self.assertEqual([a["trail"] for a in self.trails[36]], [308])


if __name__ == "__main__":
    if sys.argv[1:] == ["--write"]:
        write_fixture()
        write_bikes_fixture()
        print(FIXTURE, BIKES_FIXTURE)
    else:
        unittest.main()
