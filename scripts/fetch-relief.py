#!/usr/bin/env python3
"""Puts the pinned relief into the `relief` pack, per docs/phase-4.md § 3.

    python3 scripts/fetch-relief.py

Downloads relief.pmtiles and contours.pmtiles from the GitHub release scripts/relief.pin names and
fails unless each matches its pinned sha256. The relief is built once by build-relief.py (ADR-0009);
every release packs this same copy.

The pin is one entry per line, `#` lines being comments: `tag relief-YYYY-MM-DD`, then
`relief.pmtiles <sha256>` and `contours.pmtiles <sha256>`.
"""

from __future__ import annotations

import hashlib
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PIN = ROOT / "scripts" / "relief.pin"
TARGET = ROOT / "relief" / "src" / "main" / "assets"
RELEASES = "https://github.com/maks-sredniawa/kozica/releases/download"


def sha256(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(1 << 20):
            hasher.update(chunk)
    return hasher.hexdigest()


def main() -> None:
    if not PIN.exists():
        sys.exit("fetch-relief: scripts/relief.pin is missing; pin the relief release per docs/phase-4.md § 3")
    pin = dict(line.split(None, 1) for line in PIN.read_text().splitlines() if line.strip() and not line.startswith("#"))
    tag = pin.pop("tag").strip()
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, expected in sorted(pin.items()):
        target = TARGET / name
        if target.exists() and sha256(target) == expected.strip():
            print(f"{name}: already pinned")
            continue
        partial = target.with_name(target.name + ".part")
        print(f"downloading {RELEASES}/{tag}/{name}", file=sys.stderr)
        with urllib.request.urlopen(f"{RELEASES}/{tag}/{name}", timeout=120) as response, partial.open("wb") as out:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        if sha256(partial) != expected.strip():
            partial.unlink()
            sys.exit(f"fetch-relief: {name} from {tag} does not match scripts/relief.pin")
        partial.rename(target)
        print(f"{name}: {target.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
