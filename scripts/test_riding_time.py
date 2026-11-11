"""docs/phase-7b.md § Tests, for riding-time.py: its error measure and the split are deterministic from the seed.

    python3 -m unittest scripts/test_riding_time.py
"""

import re
import runpy
import unittest
from pathlib import Path

riding = runpy.run_path(str(Path(__file__).with_name("riding-time.py")), run_name="test")
PHASE = Path(__file__).resolve().parent.parent / "docs/phase-7b.md"


def r1_rows() -> list[list[str]]:
    text = PHASE.read_text()
    table = text[text.index("### R1, the references"):]
    return [[cell.strip() for cell in line.split("|")[1:-1]] for line in table.splitlines() if re.match(r"\| [MT]\d ", line)]


def number_of(cell: str) -> int:
    return int(re.search(r"\(r(\d+)\)", cell).group(1))


def minutes(cell: str) -> float:
    return float(re.search(r"([\d.]+) min", cell).group(1))


class SplitTest(unittest.TestCase):
    def test_the_seed_deals_r1s_sets(self):
        references = riding["load_references"]()
        dealt = riding["split"](references["seed"], references["strata"], references["replaced"])
        written = {name: [] for name in riding["SETS"]}
        for row in r1_rows():
            if number_of(row[2]) not in written[row[1]]:
                written[row[1]].append(number_of(row[2]))
        self.assertEqual(dealt, written)

    def test_the_same_seed_deals_the_same_and_another_seed_differently(self):
        strata = riding["load_references"]()["strata"]
        self.assertEqual(riding["split"](20261111, strata), riding["split"](20261111, strata))
        self.assertNotEqual(riding["split"](20261111, strata), riding["split"](20261112, strata))

    def test_each_stratum_gives_one_route_to_each_set(self):
        strata = riding["load_references"]()["strata"]
        dealt = riding["split"](7, strata)
        for i, stratum in enumerate(strata):
            self.assertEqual(sorted(dealt[name][i] for name in riding["SETS"]), sorted(stratum["routes"]))

    def test_the_replacement_takes_singletrack_14s_judge_slot(self):
        references = riding["load_references"]()
        plain = riding["split"](references["seed"], references["strata"])
        replaced = riding["split"](references["seed"], references["strata"], references["replaced"])
        self.assertIn(10113028, plain["judge"])
        self.assertEqual(replaced["judge"], [14354061 if n == 10113028 else n for n in plain["judge"]])
        self.assertEqual((plain["tune"], plain["reserve"]), (replaced["tune"], replaced["reserve"]))


class ReferencesTest(unittest.TestCase):
    def test_every_read_direction_matches_r1s_table(self):
        routes = riding["load_references"]()["routes"]
        read = [row for row in r1_rows() if row[1] != "reserve"]
        self.assertEqual(len(read), sum(len(route["directions"]) for route in routes.values()))
        for row in read:
            direction = routes[str(number_of(row[2]))]["directions"][row[5]]
            self.assertEqual(round(direction["mapy"] / 60, 1), minutes(row[6]), row)
            self.assertEqual(round(direction["komoot"] / 60, 1), minutes(row[7]), row)
            self.assertEqual(round(riding["reference_seconds"](direction) / 60, 1), minutes(row[8]), row)


class ErrorTest(unittest.TestCase):
    def test_the_error_is_relative_to_the_reference_either_side(self):
        self.assertAlmostEqual(riding["error"](120.0, 100.0), 0.2)
        self.assertAlmostEqual(riding["error"](80.0, 100.0), 0.2)
        self.assertEqual(riding["error"](100.0, 100.0), 0.0)

    def test_the_fit_weighs_halving_and_doubling_alike(self):
        self.assertAlmostEqual(riding["log_error"](200.0, 100.0), -riding["log_error"](50.0, 100.0))

    def test_the_reference_is_the_planners_midpoint(self):
        self.assertEqual(riding["reference_seconds"]({"mapy": 1330, "komoot": 1419}), 1374.5)

    def test_the_minimiser_is_deterministic_and_finds_a_minimum(self):
        def bowl(point):
            return (point[0] - 1.0) ** 2 + 3 * (point[1] + 2.0) ** 2

        first = riding["nelder_mead"](bowl, [0.0, 0.0])
        self.assertEqual(first, riding["nelder_mead"](bowl, [0.0, 0.0]))
        self.assertAlmostEqual(first[0], 1.0, places=4)
        self.assertAlmostEqual(first[1], -2.0, places=4)


if __name__ == "__main__":
    unittest.main()
