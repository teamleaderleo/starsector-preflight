#!/usr/bin/env python3
"""Guard immutable Starsector profile inputs and bounded GraphicsLib runtime state."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 2
GRAPHICSLIB_MOD_ID = "shaderLib"
GRAPHICSLIB_CACHE_DIRECTORY = "cache"
GRAPHICSLIB_HASH_RELATIVE_PATH = Path("saves/common/shaderlib_cache_hash.data")
GRAPHICSLIB_CACHE_FILE = re.compile(
    r".+___(?:"
    r"SHIP|MISSILE|(?:TURRET|HARDPOINT)\d+|"
    r"(?:TURRET|HARDPOINT)_(?:BARREL|COVER_LARGE|COVER_MEDIUM|COVER_SMALL|UNDER)"
    r")_normal\.png"
)


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


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


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fingerprint(value: Any) -> str:
    payload = json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def require_regular_file(path: Path, label: str) -> os.stat_result:
    try:
        info = path.lstat()
    except FileNotFoundError as exc:
        raise ValueError(f"{label} is missing: {path}") from exc
    if not stat.S_ISREG(info.st_mode) or path.is_symlink():
        raise ValueError(f"{label} is not a plain regular file: {path}")
    return info


def file_record(path: Path, relative_path: str, include_hash: bool) -> dict[str, Any]:
    info = require_regular_file(path, "Profile input")
    result: dict[str, Any] = {
        "relativePath": relative_path,
        "size": info.st_size,
        "mtimeNs": info.st_mtime_ns,
        "mode": stat.S_IMODE(info.st_mode),
    }
    if include_hash:
        result["sha256"] = sha256_file(path)
    return result


def resolve_contract(profile: dict[str, Any]) -> dict[str, Any]:
    try:
        install_root = Path(profile["installRoot"]).resolve()
        mods_directory = Path(profile["modsDirectory"]).resolve()
        enabled_mods_file = Path(profile["enabledModsFile"]).resolve()
        mods = profile["mods"]
        enabled_mod_ids = profile["enabledModIds"]
    except (KeyError, TypeError) as exc:
        raise ValueError("Census profile lacks exact installation/mod identity") from exc
    if not isinstance(mods, list) or not isinstance(enabled_mod_ids, list):
        raise ValueError("Census mod identity is not a list")
    if mods_directory != install_root / "mods":
        raise ValueError(
            "The resolved mods directory must be the actual macOS installation root "
            f"({install_root / 'mods'}), not a guessed bundle resource path: {mods_directory}"
        )
    if enabled_mods_file != mods_directory / "enabled_mods.json":
        raise ValueError(f"Unexpected enabled-mods file: {enabled_mods_file}")
    require_regular_file(enabled_mods_file, "Enabled-mods file")

    normalized_mods: list[dict[str, str]] = []
    seen_ids: set[str] = set()
    for entry in mods:
        if not isinstance(entry, dict):
            raise ValueError("Census mod entry is not an object")
        mod_id = entry.get("id")
        directory_value = entry.get("directory")
        if not isinstance(mod_id, str) or not isinstance(directory_value, str):
            raise ValueError("Census mod entry lacks id/directory")
        if mod_id in seen_ids:
            raise ValueError(f"Multiple mutable mods or duplicate mod id are not allowed: {mod_id}")
        seen_ids.add(mod_id)
        directory = Path(directory_value).resolve()
        try:
            directory.relative_to(mods_directory)
        except ValueError as exc:
            raise ValueError(f"Enabled mod is outside the resolved mods directory: {directory}") from exc
        if not directory.is_dir() or directory.is_symlink():
            raise ValueError(f"Enabled mod directory is not a plain directory: {directory}")
        normalized_mods.append({"id": mod_id, "directory": str(directory)})

    if [entry["id"] for entry in normalized_mods] != enabled_mod_ids:
        raise ValueError("Census mod order does not match enabledModIds")
    graphicslib = [entry for entry in normalized_mods if entry["id"] == GRAPHICSLIB_MOD_ID]
    if len(graphicslib) != 1:
        raise ValueError(
            "Exactly one shaderLib mod is required for the bounded mutable-cache contract"
        )
    graphicslib_directory = Path(graphicslib[0]["directory"])
    cache_directory = graphicslib_directory / GRAPHICSLIB_CACHE_DIRECTORY
    if not cache_directory.is_dir() or cache_directory.is_symlink():
        raise ValueError(f"GraphicsLib cache is not a plain directory: {cache_directory}")
    hash_control_file = install_root / GRAPHICSLIB_HASH_RELATIVE_PATH
    require_regular_file(hash_control_file, "GraphicsLib cache hash control file")

    return {
        "installRoot": str(install_root),
        "modsDirectory": str(mods_directory),
        "enabledModsFile": str(enabled_mods_file),
        "enabledModIds": enabled_mod_ids,
        "mods": normalized_mods,
        "graphicsLibDirectory": str(graphicslib_directory),
        "cacheDirectory": str(cache_directory),
        "hashControlFile": str(hash_control_file),
    }


def scan_immutable(contract: dict[str, Any]) -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    graphicslib_directory = Path(contract["graphicsLibDirectory"])
    cache_directory = Path(contract["cacheDirectory"])

    enabled_record = file_record(
        Path(contract["enabledModsFile"]), "enabled_mods.json", include_hash=True
    )
    enabled_record["scope"] = "mod-configuration"
    entries.append(enabled_record)

    for mod in contract["mods"]:
        mod_id = mod["id"]
        mod_directory = Path(mod["directory"])
        for root_text, directory_names, file_names in os.walk(
            mod_directory, topdown=True, followlinks=False
        ):
            root = Path(root_text)
            if mod_directory == graphicslib_directory and root == graphicslib_directory:
                directory_names[:] = [
                    name
                    for name in directory_names
                    if (root / name).resolve() != cache_directory
                ]
            directory_names.sort()
            file_names.sort()
            for name in directory_names:
                path = root / name
                if path.is_symlink():
                    raise ValueError(f"Symlinked immutable directory is not accepted: {path}")
            for name in file_names:
                path = root / name
                relative = path.relative_to(mod_directory).as_posix()
                entry = file_record(path, relative, include_hash=False)
                entry["scope"] = "enabled-mod"
                entry["modId"] = mod_id
                entries.append(entry)

    identity = {
        "installRoot": contract["installRoot"],
        "modsDirectory": contract["modsDirectory"],
        "enabledModsFile": contract["enabledModsFile"],
        "enabledModIds": contract["enabledModIds"],
        "mods": contract["mods"],
    }
    material = {"identity": identity, "entries": entries}
    return {
        "fingerprintKind": "ordered-enabled-mod-path-size-mtime-ns-mode-v1",
        "fingerprint": fingerprint(material),
        "entryCount": len(entries),
        "entries": entries,
    }


def scan_mutable_cache(contract: dict[str, Any]) -> dict[str, Any]:
    cache_directory = Path(contract["cacheDirectory"])
    entries: list[dict[str, Any]] = []
    for child in sorted(cache_directory.iterdir(), key=lambda path: path.name):
        if child.is_symlink() or not child.is_file():
            raise ValueError(f"Unexpected non-file in GraphicsLib cache: {child}")
        if not GRAPHICSLIB_CACHE_FILE.fullmatch(child.name):
            raise ValueError(f"Unexpected file in GraphicsLib cache: {child}")
        entries.append(file_record(child, child.name, include_hash=True))
    if not entries:
        raise ValueError("GraphicsLib cache is empty; a pre-warmed cache is required")

    hash_control_path = Path(contract["hashControlFile"])
    hash_control = file_record(
        hash_control_path,
        GRAPHICSLIB_HASH_RELATIVE_PATH.as_posix(),
        include_hash=True,
    )
    exact_material = {"entries": entries, "hashControl": hash_control}
    content_material = {
        "entries": [
            {
                "relativePath": entry["relativePath"],
                "size": entry["size"],
                "mode": entry["mode"],
                "sha256": entry["sha256"],
            }
            for entry in entries
        ],
        "hashControl": {
            "relativePath": hash_control["relativePath"],
            "size": hash_control["size"],
            "mode": hash_control["mode"],
            "sha256": hash_control["sha256"],
        },
    }
    return {
        "modId": GRAPHICSLIB_MOD_ID,
        "modDirectory": contract["graphicsLibDirectory"],
        "cacheDirectory": contract["cacheDirectory"],
        "hashControlFile": contract["hashControlFile"],
        "acceptedFilePattern": GRAPHICSLIB_CACHE_FILE.pattern,
        "entryCount": len(entries),
        "totalBytes": sum(entry["size"] for entry in entries),
        "fingerprintKind": "ordered-path-size-mtime-ns-mode-sha256-v1",
        "fingerprint": fingerprint(exact_material),
        "contentFingerprint": fingerprint(content_material),
        "entries": entries,
        "hashControl": hash_control,
    }


def capture_state(
    profile: dict[str, Any], snapshot_directory: Path | None = None
) -> dict[str, Any]:
    contract = resolve_contract(profile)
    state = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "sourceCensusFingerprint": profile.get("profileFingerprint"),
        "sourceCensusFingerprintKind": profile.get("fingerprintKind"),
        "contract": contract,
        "immutableProfile": scan_immutable(contract),
        "mutableCaches": [scan_mutable_cache(contract)],
    }
    if snapshot_directory is not None:
        create_snapshot(state, snapshot_directory)
        state["snapshotDirectory"] = str(snapshot_directory.resolve())
    return state


def entry_map(entries: list[dict[str, Any]], scope: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if scope == "immutable":
            key = f"{entry.get('scope')}:{entry.get('modId', '')}:{entry['relativePath']}"
        else:
            key = entry["relativePath"]
        result[key] = entry
    return result


def diff_entries(
    expected_entries: list[dict[str, Any]],
    actual_entries: list[dict[str, Any]],
    scope: str,
) -> dict[str, Any]:
    expected = entry_map(expected_entries, scope)
    actual = entry_map(actual_entries, scope)
    added = [actual[key] for key in sorted(actual.keys() - expected.keys())]
    removed = [expected[key] for key in sorted(expected.keys() - actual.keys())]
    changed = [
        {"before": expected[key], "after": actual[key]}
        for key in sorted(expected.keys() & actual.keys())
        if expected[key] != actual[key]
    ]
    return {"added": added, "removed": removed, "changed": changed}


def compare_states(expected: dict[str, Any], actual: dict[str, Any]) -> dict[str, Any]:
    if expected.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported baseline profile-state schema")
    immutable_expected = expected["immutableProfile"]
    immutable_actual = actual["immutableProfile"]
    immutable_stable = (
        expected["contract"]["enabledModIds"] == actual["contract"]["enabledModIds"]
        and expected["contract"]["mods"] == actual["contract"]["mods"]
        and immutable_expected["fingerprint"] == immutable_actual["fingerprint"]
    )
    immutable_drift = (
        {"added": [], "removed": [], "changed": []}
        if immutable_stable
        else diff_entries(
            immutable_expected["entries"], immutable_actual["entries"], "immutable"
        )
    )

    expected_mutable = expected.get("mutableCaches", [])
    actual_mutable = actual.get("mutableCaches", [])
    if len(expected_mutable) != 1 or len(actual_mutable) != 1:
        raise ValueError("Exactly one bounded mutable mod is required")
    before = expected_mutable[0]
    after = actual_mutable[0]
    if before["modId"] != GRAPHICSLIB_MOD_ID or after["modId"] != GRAPHICSLIB_MOD_ID:
        raise ValueError("Only shaderLib may supply bounded mutable runtime state")
    mutable_drift = diff_entries(before["entries"], after["entries"], "mutable")
    if before["hashControl"] != after["hashControl"]:
        mutable_drift["hashControlChanged"] = {
            "before": before["hashControl"],
            "after": after["hashControl"],
        }
    else:
        mutable_drift["hashControlChanged"] = None
    exact_equivalent = before["fingerprint"] == after["fingerprint"]
    content_equivalent = before["contentFingerprint"] == after["contentFingerprint"]

    return {
        "stable": immutable_stable,
        "immutableProfileStable": immutable_stable,
        "expectedImmutableFingerprint": immutable_expected["fingerprint"],
        "actualImmutableFingerprint": immutable_actual["fingerprint"],
        "immutableDrift": immutable_drift,
        "mutableCache": {
            "modId": GRAPHICSLIB_MOD_ID,
            "validRuntimeShape": True,
            "exactEquivalentToBaseline": exact_equivalent,
            "contentEquivalentToBaseline": content_equivalent,
            "changedDuringRun": not exact_equivalent,
            "expectedFingerprint": before["fingerprint"],
            "actualFingerprint": after["fingerprint"],
            "expectedContentFingerprint": before["contentFingerprint"],
            "actualContentFingerprint": after["contentFingerprint"],
            "drift": mutable_drift,
        },
        "sourceCensusFingerprintChanged": (
            expected.get("sourceCensusFingerprint")
            != actual.get("sourceCensusFingerprint")
        ),
        "expectedSourceCensusFingerprint": expected.get("sourceCensusFingerprint"),
        "actualSourceCensusFingerprint": actual.get("sourceCensusFingerprint"),
    }


def snapshot_cache_path(snapshot_directory: Path, relative_path: str) -> Path:
    return snapshot_directory / "cache" / relative_path


def snapshot_hash_path(snapshot_directory: Path) -> Path:
    return snapshot_directory / "control" / "shaderlib_cache_hash.data"


def copy_exact(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        dir=destination.parent, prefix=f".{destination.name}.", delete=False
    ) as handle:
        temporary = Path(handle.name)
    try:
        shutil.copy2(source, temporary)
        os.replace(temporary, destination)
    finally:
        if temporary.exists():
            temporary.unlink()


def create_snapshot(state: dict[str, Any], snapshot_directory: Path) -> None:
    snapshot_directory = snapshot_directory.resolve()
    install_root = Path(state["contract"]["installRoot"])
    try:
        snapshot_directory.relative_to(install_root)
    except ValueError:
        pass
    else:
        raise ValueError("Mutable-cache snapshot must be outside the Starsector installation")
    if snapshot_directory.exists() and any(snapshot_directory.iterdir()):
        raise ValueError(f"Snapshot directory is not empty: {snapshot_directory}")
    snapshot_directory.mkdir(parents=True, exist_ok=True)
    mutable = state["mutableCaches"][0]
    cache_directory = Path(mutable["cacheDirectory"])
    for entry in mutable["entries"]:
        copy_exact(
            cache_directory / entry["relativePath"],
            snapshot_cache_path(snapshot_directory, entry["relativePath"]),
        )
    copy_exact(
        Path(mutable["hashControlFile"]),
        snapshot_hash_path(snapshot_directory),
    )
    write_json(snapshot_directory / "baseline-state.json", state)


def verify_snapshot(baseline: dict[str, Any], snapshot_directory: Path) -> None:
    mutable = baseline["mutableCaches"][0]
    for entry in mutable["entries"]:
        path = snapshot_cache_path(snapshot_directory, entry["relativePath"])
        record = file_record(path, entry["relativePath"], include_hash=True)
        if record != entry:
            raise ValueError(f"Snapshot entry does not match its manifest: {path}")
    hash_path = snapshot_hash_path(snapshot_directory)
    record = file_record(
        hash_path, GRAPHICSLIB_HASH_RELATIVE_PATH.as_posix(), include_hash=True
    )
    if record != mutable["hashControl"]:
        raise ValueError(f"Snapshot hash control does not match its manifest: {hash_path}")


def restore_file(source: Path, destination: Path, expected: dict[str, Any]) -> bool:
    needs_copy = True
    current: dict[str, Any] | None = None
    if destination.exists() and destination.is_file() and not destination.is_symlink():
        current = file_record(destination, expected["relativePath"], include_hash=True)
        needs_copy = (
            current["size"] != expected["size"]
            or current["sha256"] != expected["sha256"]
        )
    if needs_copy:
        copy_exact(source, destination)
        current = None
    mode_changed = current is None or current["mode"] != expected["mode"]
    mtime_changed = current is None or current["mtimeNs"] != expected["mtimeNs"]
    if mode_changed:
        os.chmod(destination, expected["mode"])
    if mtime_changed:
        os.utime(destination, ns=(expected["mtimeNs"], expected["mtimeNs"]))
    return needs_copy or mode_changed or mtime_changed


def restore_state(
    baseline: dict[str, Any], snapshot_directory: Path
) -> dict[str, Any]:
    if baseline.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported baseline profile-state schema")
    snapshot_directory = snapshot_directory.resolve()
    verify_snapshot(baseline, snapshot_directory)
    contract = baseline["contract"]
    # This strict scan happens before mutation. Unknown paths are retained for evidence.
    current_mutable = scan_mutable_cache(contract)
    expected_mutable = baseline["mutableCaches"][0]
    current_names = {entry["relativePath"] for entry in current_mutable["entries"]}
    expected_names = {entry["relativePath"] for entry in expected_mutable["entries"]}

    removed_added_files: list[str] = []
    cache_directory = Path(expected_mutable["cacheDirectory"])
    for relative_path in sorted(current_names - expected_names):
        path = cache_directory / relative_path
        require_regular_file(path, "Generated GraphicsLib cache file")
        path.unlink()
        removed_added_files.append(relative_path)

    restored_files: list[str] = []
    for entry in expected_mutable["entries"]:
        relative_path = entry["relativePath"]
        if restore_file(
            snapshot_cache_path(snapshot_directory, relative_path),
            cache_directory / relative_path,
            entry,
        ):
            restored_files.append(relative_path)
    hash_restored = restore_file(
        snapshot_hash_path(snapshot_directory),
        Path(expected_mutable["hashControlFile"]),
        expected_mutable["hashControl"],
    )

    profile_stub = {
        "installRoot": contract["installRoot"],
        "modsDirectory": contract["modsDirectory"],
        "enabledModsFile": contract["enabledModsFile"],
        "enabledModIds": contract["enabledModIds"],
        "mods": contract["mods"],
        "profileFingerprint": baseline.get("sourceCensusFingerprint"),
        "fingerprintKind": baseline.get("sourceCensusFingerprintKind"),
    }
    restored = capture_state(profile_stub)
    comparison = compare_states(baseline, restored)
    if not comparison["immutableProfileStable"]:
        raise ValueError("Immutable profile changed while restoring mutable state")
    if not comparison["mutableCache"]["exactEquivalentToBaseline"]:
        raise ValueError("Mutable state did not restore exactly to the baseline manifest")
    return {
        "restored": True,
        "removedAddedFiles": removed_added_files,
        "restoredFiles": restored_files,
        "hashControlRestored": hash_restored,
        "mutableCache": comparison["mutableCache"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    capture = subparsers.add_parser("capture")
    capture.add_argument("--profile-report", required=True, type=Path)
    capture.add_argument("--output", required=True, type=Path)
    capture.add_argument("--snapshot-dir", type=Path)

    check = subparsers.add_parser("check")
    check.add_argument("--baseline", required=True, type=Path)
    check.add_argument("--current-report", required=True, type=Path)
    check.add_argument("--output", required=True, type=Path)

    restore = subparsers.add_parser("restore")
    restore.add_argument("--baseline", required=True, type=Path)
    restore.add_argument("--snapshot-dir", required=True, type=Path)
    restore.add_argument("--output", required=True, type=Path)

    args = parser.parse_args()
    try:
        if args.command == "capture":
            profile = extract_profile(read_json(args.profile_report))
            result = capture_state(profile, args.snapshot_dir)
            write_json(args.output, result)
            return 0
        if args.command == "check":
            expected = read_json(args.baseline)
            profile = extract_profile(read_json(args.current_report))
            actual = capture_state(profile)
            result = compare_states(expected, actual)
            write_json(args.output, result)
            return 0 if result["immutableProfileStable"] else 1
        if args.command == "restore":
            result = restore_state(
                read_json(args.baseline), args.snapshot_dir
            )
            write_json(args.output, result)
            return 0
    except (OSError, ValueError, KeyError, TypeError) as exc:
        error = {"error": str(exc), "command": args.command}
        write_json(args.output, error)
        print(f"profile guard: {exc}", file=os.sys.stderr)
        return 2
    raise AssertionError("Unreachable command")


if __name__ == "__main__":
    raise SystemExit(main())
