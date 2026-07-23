#!/usr/bin/env python3
"""Discover and validate the reviewed Starsector core mission resource root."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Any

REQUIRED_MISSION = "afistfulofcredits"


def candidate_roots(game: Path) -> list[Path]:
    resources = game / "Contents" / "Resources"
    return [
        resources / "Java",
        resources / "starfarer.res" / "res",
    ]


def mission_names(path: Path) -> set[str]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle)
        names: set[str] = set()
        for row in reader:
            if not row:
                continue
            value = row[0].strip()
            if value and value.lower() != "mission":
                names.add(value)
        return names


def inspect_root(root: Path) -> dict[str, Any] | None:
    mission_list = root / "data" / "missions" / "mission_list.csv"
    descriptor = root / "data" / "missions" / REQUIRED_MISSION / "descriptor.json"
    if not mission_list.is_file() or not descriptor.is_file():
        return None
    try:
        names = mission_names(mission_list)
    except (OSError, UnicodeError, csv.Error):
        return None
    if REQUIRED_MISSION not in names:
        return None
    return {
        "resourceRoot": str(root.resolve()),
        "missionList": str(mission_list.resolve()),
        "missionDescriptor": str(descriptor.resolve()),
        "requiredMission": REQUIRED_MISSION,
        "missionListContainsRequiredMission": True,
    }


def discover(game: Path) -> dict[str, Any]:
    matches = [result for root in candidate_roots(game) if (result := inspect_root(root))]
    if not matches:
        searched = ", ".join(str(path) for path in candidate_roots(game))
        raise ValueError(f"Reviewed core mission resources were not found; searched: {searched}")
    if len(matches) != 1:
        roots = ", ".join(result["resourceRoot"] for result in matches)
        raise ValueError(f"Reviewed core mission resource root is ambiguous: {roots}")
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    try:
        result = discover(args.game)
    except ValueError as exc:
        parser.error(str(exc))

    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output is None:
        print(payload, end="")
    else:
        args.output.write_text(payload, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
