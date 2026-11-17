#!/usr/bin/env python3
"""docs/phase-7c.md § 1, R1: replays every Bike route as the recorder would sample it and matches it back.

Usage: python3 scripts/ride-gate.py <base-assets dir>   (e.g. map-data/pin-4965/base-assets)
Writes <dir>/../ride-gate/bike-routes.tsv for the replay and ride-gate.tsv from it.
"""
import os
import sqlite3
import subprocess
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    assets = Path(sys.argv[1]).resolve()
    out = assets.parent / "ride-gate"
    out.mkdir(exist_ok=True)
    db = sqlite3.connect(f"file:{assets / 'places.sqlite'}?mode=ro", uri=True)
    rows = db.execute("SELECT id, kind, one_way FROM bike_routes ORDER BY id").fetchall()
    with open(out / "bike-routes.tsv", "w") as f:
        f.write("id\tkind\tone_way\n")
        for id_, kind, one_way in rows:
            f.write(f"{id_}\t{kind}\t{1 if one_way else 0}\n")
    print(f"{len(rows)} Bike routes; replaying, progress below", file=sys.stderr, flush=True)
    root = Path(__file__).resolve().parent.parent
    subprocess.run(
        ["./gradlew", "--console=plain", ":app:testDebugUnitTest", "--tests", "*RideMatchReplay*",
         f"-PrideGate={assets}"],
        cwd=root, check=True, env={**os.environ, "GIT_PAGER": "cat", "PAGER": "cat"},
    )
    print(f"rows: {out / 'ride-gate.tsv'}", file=sys.stderr)


if __name__ == "__main__":
    main()
