#!/usr/bin/env python3
"""Exact-profile Starsector log snapshot, delta, classification, and readiness detection."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable

LOG_GLOB = "starsector.log*"
TIMESTAMP = re.compile(r"^\s*(\d+)\s+\[")
LAUNCHER_MARKER = "graphics/fonts/orbitron12_0.png"
SAVE_DESCRIPTOR_PARTS = ("CampaignGameManager", "Reading save data from [")
PRELOAD_PARTS = ("TextureData", "VRAM after unload/preload:")


@dataclass
class TailState:
    offsets: dict[int, int]
    partial: dict[int, bytes] = field(default_factory=dict)
    observed_lines: int = 0
    last_activity_ns: int | None = None
    last_log_ms: int | None = None


def _files(log_dir: Path) -> list[Path]:
    if not log_dir.is_dir():
        return []
    return sorted(path for path in log_dir.glob(LOG_GLOB) if path.is_file())


def snapshot(log_dir: Path) -> dict[str, dict[str, int]]:
    values: dict[str, dict[str, int]] = {}
    for path in _files(log_dir):
        stat = path.stat()
        values[path.name] = {"inode": stat.st_ino, "size": stat.st_size}
    return values


def write_snapshot(log_dir: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(snapshot(log_dir), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def load_offsets(snapshot_file: Path) -> dict[int, int]:
    raw = json.loads(snapshot_file.read_text(encoding="utf-8"))
    return {int(value["inode"]): int(value["size"]) for value in raw.values()}


def _read_new_lines(log_dir: Path, state: TailState) -> list[str]:
    lines: list[str] = []
    for path in _files(log_dir):
        stat = path.stat()
        inode = int(stat.st_ino)
        offset = state.offsets.get(inode, 0)
        if stat.st_size < offset:
            offset = 0
            state.partial.pop(inode, None)
        if stat.st_size == offset:
            continue
        with path.open("rb") as stream:
            stream.seek(offset)
            data = stream.read()
        state.offsets[inode] = stat.st_size
        if not data:
            continue
        combined = state.partial.get(inode, b"") + data
        chunks = combined.splitlines(keepends=True)
        state.partial[inode] = b""
        if chunks and not chunks[-1].endswith((b"\n", b"\r")):
            state.partial[inode] = chunks.pop()
        now = time.monotonic_ns()
        for chunk in chunks:
            line = chunk.decode("utf-8", errors="replace").rstrip("\r\n")
            lines.append(line)
            state.observed_lines += 1
            state.last_activity_ns = now
            match = TIMESTAMP.match(line)
            if match:
                state.last_log_ms = int(match.group(1))
    return lines


def _pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def _write_result(output: Path, result: dict[str, object]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def watch_launcher(
    log_dir: Path,
    snapshot_file: Path,
    output: Path,
    pid: int,
    process_start_ns: int,
    timeout_seconds: float,
    quiet_seconds: float,
    sleep_seconds: float = 0.05,
) -> bool:
    state = TailState(load_offsets(snapshot_file))
    deadline = time.monotonic() + timeout_seconds
    marker_seen = False
    marker_line: str | None = None
    while time.monotonic() < deadline:
        for line in _read_new_lines(log_dir, state):
            if LAUNCHER_MARKER in line:
                marker_seen = True
                marker_line = line[:1000]
        now_ns = time.monotonic_ns()
        quiet_ns = int(quiet_seconds * 1_000_000_000)
        if marker_seen and state.last_activity_ns is not None and now_ns - state.last_activity_ns >= quiet_ns:
            result = {
                "phase": "launcher",
                "detected": True,
                "launcherMarker": LAUNCHER_MARKER,
                "launcherMarkerLine": marker_line,
                "launcherReadyMs": round((state.last_activity_ns - process_start_ns) / 1_000_000, 3),
                "launcherReadyLogMillis": state.last_log_ms,
                "quietConfirmationMillis": round(quiet_seconds * 1000, 3),
                "observedLines": state.observed_lines,
            }
            _write_result(output, result)
            return True
        if not _pid_alive(pid):
            break
        time.sleep(sleep_seconds)
    _write_result(output, {
        "phase": "launcher",
        "detected": False,
        "launcherMarker": LAUNCHER_MARKER,
        "markerSeen": marker_seen,
        "observedLines": state.observed_lines,
        "timeoutSeconds": timeout_seconds,
        "processAlive": _pid_alive(pid),
    })
    return False


def _contains_all(line: str, parts: Iterable[str]) -> bool:
    return all(part in line for part in parts)


def watch_main_menu(
    log_dir: Path,
    snapshot_file: Path,
    output: Path,
    pid: int,
    timeout_seconds: float,
    quiet_seconds: float,
    sleep_seconds: float = 0.05,
) -> bool:
    state = TailState(load_offsets(snapshot_file))
    deadline = time.monotonic() + timeout_seconds
    game_start_ns: int | None = None
    game_start_log_ms: int | None = None
    game_start_line: str | None = None
    descriptor_seen = False
    preload_seen = False
    descriptor_line: str | None = None
    preload_line: str | None = None

    while time.monotonic() < deadline:
        lines = _read_new_lines(log_dir, state)
        for line in lines:
            match = TIMESTAMP.match(line)
            if game_start_ns is None and match:
                game_start_ns = state.last_activity_ns
                game_start_log_ms = int(match.group(1))
                game_start_line = line[:1000]
            if _contains_all(line, SAVE_DESCRIPTOR_PARTS):
                descriptor_seen = True
                descriptor_line = line[:1000]
            if _contains_all(line, PRELOAD_PARTS):
                preload_seen = True
                preload_line = line[:1000]

        now_ns = time.monotonic_ns()
        quiet_ns = int(quiet_seconds * 1_000_000_000)
        ready = (
            game_start_ns is not None
            and descriptor_seen
            and preload_seen
            and state.last_activity_ns is not None
            and now_ns - state.last_activity_ns >= quiet_ns
        )
        if ready:
            monotonic_delta_ms = round((state.last_activity_ns - game_start_ns) / 1_000_000, 3)
            log_delta_ms = None
            if game_start_log_ms is not None and state.last_log_ms is not None:
                log_delta_ms = state.last_log_ms - game_start_log_ms
            _write_result(output, {
                "phase": "main-menu",
                "detected": True,
                "timingMethod": "automatic-starsector-log-phase-detection",
                "gameStartLine": game_start_line,
                "gameStartLogMillis": game_start_log_ms,
                "mainMenuReadyLogMillis": state.last_log_ms,
                "gameLogStartToMainMenuMs": monotonic_delta_ms,
                "gameLogMillisDelta": log_delta_ms,
                "saveDescriptorSeen": descriptor_seen,
                "saveDescriptorLine": descriptor_line,
                "graphicsPreloadSeen": preload_seen,
                "graphicsPreloadLine": preload_line,
                "quietConfirmationMillis": round(quiet_seconds * 1000, 3),
                "observedLines": state.observed_lines,
            })
            return True
        if not _pid_alive(pid):
            break
        time.sleep(sleep_seconds)

    _write_result(output, {
        "phase": "main-menu",
        "detected": False,
        "gameStartSeen": game_start_ns is not None,
        "saveDescriptorSeen": descriptor_seen,
        "graphicsPreloadSeen": preload_seen,
        "observedLines": state.observed_lines,
        "timeoutSeconds": timeout_seconds,
        "processAlive": _pid_alive(pid),
    })
    return False


def extract_delta(log_dir: Path, snapshot_file: Path, output: Path) -> None:
    before = json.loads(snapshot_file.read_text(encoding="utf-8"))
    prior_by_inode = {int(value["inode"]): int(value["size"]) for value in before.values()}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as stream:
        if not log_dir.is_dir():
            stream.write(b"log directory unavailable\n")
            return
        for path in _files(log_dir):
            stat = path.stat()
            offset = prior_by_inode.get(int(stat.st_ino), 0)
            if stat.st_size < offset:
                offset = 0
            stream.write(f"\n===== {path.name} offset={offset} size={stat.st_size} =====\n".encode())
            with path.open("rb") as source:
                source.seek(offset)
                while chunk := source.read(1024 * 1024):
                    stream.write(chunk)


def classify(input_file: Path, output: Path) -> None:
    text = input_file.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    patterns: dict[str, Callable[[str], bool]] = {
        "normalMapBufferFailures": lambda line: (
            "Failed to load texture when generating normal map" in line
            and "Number of remaining buffer elements is 4, must be at least 16" in line
        ),
        "shaderCreationErrors": lambda line: "ERROR org.dark.shaders.util.ShaderLib  - Error creating shader:" in line,
        "musicSourceWarnings": lambda line: "WARN  com.fs.starfarer.D.K  - Error initializing music source" in line,
        "graphicsLibErrors": lambda line: "ERROR org.dark.shaders" in line,
        "graphicsLibWarnings": lambda line: "WARN  org.dark.shaders" in line,
    }
    counts = {name: sum(1 for line in lines if predicate(line)) for name, predicate in patterns.items()}
    matched: list[str] = []
    for line in lines:
        if any(predicate(line) for predicate in patterns.values()):
            matched.append(line[:1000])
            if len(matched) == 64:
                break
    _write_result(output, {"counts": counts, "firstMatchedLines": matched, "bytes": input_file.stat().st_size})


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    snapshot_parser = subparsers.add_parser("snapshot")
    snapshot_parser.add_argument("--log-dir", type=Path, required=True)
    snapshot_parser.add_argument("--output", type=Path, required=True)

    extract_parser = subparsers.add_parser("extract")
    extract_parser.add_argument("--log-dir", type=Path, required=True)
    extract_parser.add_argument("--snapshot", type=Path, required=True)
    extract_parser.add_argument("--output", type=Path, required=True)

    classify_parser = subparsers.add_parser("classify")
    classify_parser.add_argument("--input", type=Path, required=True)
    classify_parser.add_argument("--output", type=Path, required=True)

    for name in ("watch-launcher", "watch-main-menu"):
        watch = subparsers.add_parser(name)
        watch.add_argument("--log-dir", type=Path, required=True)
        watch.add_argument("--snapshot", type=Path, required=True)
        watch.add_argument("--output", type=Path, required=True)
        watch.add_argument("--pid", type=int, required=True)
        watch.add_argument("--timeout-seconds", type=float, required=True)
        watch.add_argument("--quiet-seconds", type=float, required=True)
        if name == "watch-launcher":
            watch.add_argument("--process-start-ns", type=int, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.command == "snapshot":
        write_snapshot(args.log_dir, args.output)
        return 0
    if args.command == "extract":
        extract_delta(args.log_dir, args.snapshot, args.output)
        return 0
    if args.command == "classify":
        classify(args.input, args.output)
        return 0
    if args.command == "watch-launcher":
        return 0 if watch_launcher(
            args.log_dir, args.snapshot, args.output, args.pid, args.process_start_ns,
            args.timeout_seconds, args.quiet_seconds,
        ) else 1
    if args.command == "watch-main-menu":
        return 0 if watch_main_menu(
            args.log_dir, args.snapshot, args.output, args.pid,
            args.timeout_seconds, args.quiet_seconds,
        ) else 1
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
