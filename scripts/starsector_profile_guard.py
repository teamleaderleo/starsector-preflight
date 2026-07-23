#!/usr/bin/env python3
"""Compare exact-profile census snapshots for the main-menu A/B runner."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

NUMERIC_FIELDS = (
    "files",
    "bytes",
    "imageFiles",
    "imageBytes",
    "soundFiles",
    "soundBytes",
    "looseJavaFiles",
    "looseJavaBytes",
    "jarFiles",
    "jarBytes",
    "dataFiles",
    "dataBytes",
)
GRAPHICSLIB_CACHE_FIELDS = {"files", "bytes", "imageFiles", "imageBytes"}


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path}")
    return value


def extract_profile(value: dict[str, Any]) -> dict[str, Any]:
    if "profileFingerprint" in value and "mods" in value:
        return value
    try:
        profile = value["stages"]["census"]["details"]["profile"]
    except (KeyError, TypeError) as exc:
        raise ValueError("JSON does not contain a census profile") from exc
    if not isinstance(profile, dict):
        raise ValueError("Census profile is not a JSON object")
    return profile


def compare_profiles(expected: dict[str, Any], actual: dict[str, Any]) -> dict[str, Any]:
    expected_mods = {entry["id"]: entry for entry in expected.get("mods", [])}
    actual_mods = {entry["id"]: entry for entry in actual.get("mods", [])}
    mod_deltas: list[dict[str, Any]] = []

    for mod_id in sorted(set(expected_mods) | set(actual_mods)):
        before = expected_mods.get(mod_id)
        after = actual_mods.get(mod_id)
        if before is None or after is None:
            mod_deltas.append({"id": mod_id, "before": before, "after": after})
            continue
        deltas = {
            field: after.get(field, 0) - before.get(field, 0)
            for field in NUMERIC_FIELDS
            if after.get(field, 0) != before.get(field, 0)
        }
        if deltas:
            mod_deltas.append(
                {"id": mod_id, "directory": after.get("directory"), "deltas": deltas}
            )

    graphicslib_deltas = mod_deltas[0].get("deltas", {}) if len(mod_deltas) == 1 else {}
    graphicslib_runtime_cache_candidate = (
        len(mod_deltas) == 1
        and mod_deltas[0].get("id") == "shaderLib"
        and set(graphicslib_deltas).issubset(GRAPHICSLIB_CACHE_FIELDS)
        and graphicslib_deltas.get("files", 0) > 0
        and graphicslib_deltas.get("files") == graphicslib_deltas.get("imageFiles")
        and graphicslib_deltas.get("bytes", 0) > 0
        and graphicslib_deltas.get("bytes") == graphicslib_deltas.get("imageBytes")
    )

    return {
        "stable": expected.get("profileFingerprint") == actual.get("profileFingerprint"),
        "expectedProfileFingerprint": expected.get("profileFingerprint"),
        "actualProfileFingerprint": actual.get("profileFingerprint"),
        "fingerprintKind": actual.get("fingerprintKind"),
        "modDeltas": mod_deltas,
        "graphicsLibRuntimeCacheCandidate": graphicslib_runtime_cache_candidate,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-profile", required=True, type=Path)
    parser.add_argument("--current-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    expected = extract_profile(read_json(args.expected_profile))
    actual = extract_profile(read_json(args.current_report))
    result = compare_profiles(expected, actual)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["stable"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
