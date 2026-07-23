#!/usr/bin/env bash
set -euo pipefail

GAME="${GAME:-/Applications/Starsector.app}"
CACHE="${CACHE:-$HOME/.starsector-preflight/cache}"
ORDER="${ORDER:-}"
JAR="$PWD/preflight-cli/target/preflight.jar"
DIAGNOSTIC_PROPERTY="-Dpreflight.preparedPixels.coherentDirect=true"
EXPECTED_ARCHIVE_SHA="10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708"
EXPECTED_CLASS_SHA="d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50"
ACCEPTED_GAMEPLAY_ARCHIVE_SHA="cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf"
AXIS_CONTRACT='return new DimensionSetters(setters.get(1), setters.get(0));'
MIN_RUNTIME_SECONDS=30

for command in git java mvn jq shasum unzip tar grep python3; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

if [[ ! -f pom.xml ]]; then
    echo "Run this script from the starsector-preflight repository root." >&2
    exit 1
fi
if [[ ! -d "$GAME" ]]; then
    echo "Starsector installation not found: $GAME" >&2
    exit 1
fi

PLAN_SOURCE="preflight-agent/src/main/java/dev/starsector/preflight/agent/TexturePreparedPixelPlan.java"
if [[ ! -f "$PLAN_SOURCE" ]] || ! grep -Fq "$AXIS_CONTRACT" "$PLAN_SOURCE"; then
    echo "This checkout does not contain the reviewed height-first/width-second axis mapping." >&2
    echo "Run: git switch main && git pull --ff-only" >&2
    exit 1
fi

if [[ -z "$ORDER" ]]; then
    ORDER="$(python3 - <<'PYORDER'
import secrets
print(secrets.choice(("compatibility,prepared", "prepared,compatibility")))
PYORDER
)"
fi

IFS=',' read -r first_mode second_mode extra_mode <<< "$ORDER"
if [[ -n "${extra_mode:-}" \
        || "$first_mode" == "$second_mode" \
        || ( "$first_mode" != compatibility && "$first_mode" != prepared ) \
        || ( "$second_mode" != compatibility && "$second_mode" != prepared ) ]]; then
    echo "ORDER must be compatibility,prepared or prepared,compatibility." >&2
    exit 1
fi

STAMP="$(date -u +%Y%m%d-%H%M%S)"
REPORT_DIR="$CACHE/reports"
CONTRACT_REPORT="$REPORT_DIR/prepared-pixel-main-menu-comparison-contract-$STAMP.json"
PREP_REPORT="$REPORT_DIR/prepared-pixel-main-menu-comparison-preparation-$STAMP.json"
ROOT_DIR="$HOME/.starsector-preflight/runs/prepared-pixel-main-menu-comparison-pilot-$STAMP"
ARCHIVE_OUT="$HOME/Desktop/$(basename "$ROOT_DIR").tar.gz"
mkdir -p "$REPORT_DIR" "$ROOT_DIR"

REPOSITORY_HEAD="$(git rev-parse HEAD)"
echo "Repository HEAD: $REPOSITORY_HEAD"

echo
echo "== Building and verifying the checkout =="
mvn --batch-mode --no-transfer-progress verify

if [[ ! -f "$JAR" ]]; then
    echo "Runnable JAR was not produced: $JAR" >&2
    exit 1
fi
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
echo "Preflight JAR SHA-256: $JAR_SHA"

echo
echo "== Checking launcher discovery =="
java -jar "$JAR" doctor --game "$GAME"

ARCHIVE="$GAME/Contents/Resources/Java/fs.common_obf.jar"
if [[ ! -f "$ARCHIVE" ]]; then
    echo "Installed Starsector archive not found: $ARCHIVE" >&2
    exit 1
fi
ARCHIVE_SHA="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
CLASS_SHA="$(unzip -p "$ARCHIVE" com/fs/graphics/TextureLoader.class | shasum -a 256 | awk '{print $1}')"

if [[ "$ARCHIVE_SHA" != "$EXPECTED_ARCHIVE_SHA" ]]; then
    echo "Installed archive identity does not match the reviewed target." >&2
    exit 1
fi
if [[ "$CLASS_SHA" != "$EXPECTED_CLASS_SHA" ]]; then
    echo "Installed TextureLoader identity does not match the reviewed target." >&2
    exit 1
fi

echo
echo "== Running the offline prepared-pixel contract check =="
set +e
java -cp "$JAR" dev.starsector.preflight.agent.PreparedPixelContractCheck \
    "$ARCHIVE" > "$CONTRACT_REPORT"
checker_status=$?
set -e
cat "$CONTRACT_REPORT"
if [[ "$checker_status" -ne 0 ]]; then
    echo "Prepared-pixel contract check declined with exit $checker_status." >&2
    exit "$checker_status"
fi

echo
echo "== Preparing the exact current profile =="
java -jar "$JAR" prepare \
    --game "$GAME" \
    --cache-dir "$CACHE" \
    --report "$PREP_REPORT" \
    --deep \
    --verify-lookups

jq -e '
    .successful == true
    and .readiness.preparedPixelsAdapter == "pot-bypass-enabled-npot-coherent-direct-gameplay-accepted-opt-in"
    and .readiness.preparedPixelsBehavioralAcceptance == "accepted-2026-07-23-exact-profile"
    and .readiness.preparedPixelsDefaultEnablement == "blocked-pending-main-menu-comparison-and-repeat-timing"
    and .readiness.preparedPixelsComparisonPilotRequired == true
    and .readiness.preparedPixelsNextOperatorAction == "single-main-menu-comparison-pilot"
    and .readiness.repeatTimingCampaignRequired == true
    and .readiness.launchAccelerationClaimed == false
' "$PREP_REPORT" >/dev/null

INSTALL_ROOT="$(jq -er '.installRoot' "$PREP_REPORT")"
LAUNCHER="$(jq -er '.launcher' "$PREP_REPORT")"
CACHE_DIRECTORY="$(jq -er '.cacheDirectory' "$PREP_REPORT")"
RESOURCE_INDEX="$(jq -er '.resourceIndex' "$PREP_REPORT")"
TEXTURE_MANIFEST="$(jq -er '.stages.textures.details.manifest' "$PREP_REPORT")"
LOG_DIR="$INSTALL_ROOT/logs"

for file in "$RESOURCE_INDEX" "$TEXTURE_MANIFEST"; do
    if [[ ! -f "$file" ]]; then
        echo "Preparation artifact does not exist: $file" >&2
        exit 1
    fi
done

cp "$CONTRACT_REPORT" "$ROOT_DIR/operator-contract.json"
cp "$PREP_REPORT" "$ROOT_DIR/operator-preparation.json"
cat > "$ROOT_DIR/operator-comparison-identity.txt" <<IDENTITY
repositoryHead=$REPOSITORY_HEAD
jarSha256=$JAR_SHA
archiveSha256=$ARCHIVE_SHA
classSha256=$CLASS_SHA
acceptedGameplayArchiveSha256=$ACCEPTED_GAMEPLAY_ARCHIVE_SHA
dimensionReplay=reviewed-converter-height-first-width-second
scope=main-menu-comparison-pilot
order=$ORDER
samplesPerMode=1
benchmarkAccepted=false
IDENTITY

now_ns() {
    python3 - <<'PY'
import time
print(time.monotonic_ns())
PY
}

snapshot_logs() {
    local output="$1"
    python3 - "$LOG_DIR" "$output" <<'PY'
import json
import sys
from pathlib import Path

log_dir = Path(sys.argv[1])
out = Path(sys.argv[2])
values = {}
if log_dir.is_dir():
    for path in sorted(log_dir.glob("starsector.log*")):
        if path.is_file():
            stat = path.stat()
            values[path.name] = {"inode": stat.st_ino, "size": stat.st_size}
out.write_text(json.dumps(values, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

extract_log_delta() {
    local before="$1"
    local output="$2"
    python3 - "$LOG_DIR" "$before" "$output" <<'PY'
import json
import sys
from pathlib import Path

log_dir = Path(sys.argv[1])
before = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
out = Path(sys.argv[3])
with out.open("wb") as stream:
    if not log_dir.is_dir():
        stream.write(b"log directory unavailable\n")
    else:
        for path in sorted(log_dir.glob("starsector.log*")):
            if not path.is_file():
                continue
            stat = path.stat()
            prior = before.get(path.name)
            offset = 0
            if prior and prior.get("inode") == stat.st_ino and stat.st_size >= prior.get("size", 0):
                offset = int(prior["size"])
            stream.write(f"\n===== {path.name} offset={offset} size={stat.st_size} =====\n".encode())
            with path.open("rb") as source:
                source.seek(offset)
                while True:
                    chunk = source.read(1024 * 1024)
                    if not chunk:
                        break
                    stream.write(chunk)
PY
}

classify_log_delta() {
    local input="$1"
    local output="$2"
    python3 - "$input" "$output" <<'PY'
import json
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
lines = text.splitlines()
patterns = {
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
matched = []
for line in lines:
    if any(predicate(line) for predicate in patterns.values()):
        matched.append(line[:1000])
        if len(matched) == 64:
            break
result = {"counts": counts, "firstMatchedLines": matched, "bytes": Path(sys.argv[1]).stat().st_size}
Path(sys.argv[2]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

ask_yes_no() {
    local prompt="$1"
    local reply
    while true; do
        read -r -p "$prompt [y/n]: " reply
        case "$reply" in
            y|Y|yes|YES|Yes) printf 'true\n'; return 0 ;;
            n|N|no|NO|No) printf 'false\n'; return 0 ;;
            *) echo "Please answer y or n." >&2 ;;
        esac
    done
}

run_mode() {
    local mode="$1"
    local run_dir="$ROOT_DIR/$mode"
    local before_logs="$run_dir/log-snapshot-before.json"
    local log_delta="$run_dir/starsector-log-delta.txt"
    local log_classification="$run_dir/log-classification.json"
    local wrapper_output="$run_dir/operator-wrapper-output.txt"
    local result="$run_dir/operator-main-menu-result.json"
    local -a run_args
    local java_tool_options="${JAVA_TOOL_OPTIONS:-}"

    mkdir -p "$run_dir"
    snapshot_logs "$before_logs"

    if [[ "$mode" == compatibility ]]; then
        run_args=(
            run
            --game "$INSTALL_ROOT"
            --launcher "$LAUNCHER"
            --trace-dir "$run_dir"
            --adapter
            --texture-mode compatibility
        )
    else
        run_args=(
            run
            --game "$INSTALL_ROOT"
            --launcher "$LAUNCHER"
            --trace-dir "$run_dir"
            --adapter
            --texture-mode prepared-pixels
            --texture-cache-dir "$CACHE_DIRECTORY"
            --texture-manifest "$TEXTURE_MANIFEST"
            --texture-index "$RESOURCE_INDEX"
        )
        java_tool_options="${java_tool_options:+$java_tool_options }$DIAGNOSTIC_PROPERTY"
    fi

    echo
    echo "== $mode dry-run plan =="
    JAVA_TOOL_OPTIONS="$java_tool_options" java -jar "$JAR" "${run_args[@]}" --dry-run
    echo
    echo "This is the $mode half of a two-run comparison pilot."
    echo "It is one timing sample, not a benchmark."
    echo "Do not load a campaign. Stop at the main menu, then exit cleanly."
    read -r -p "Press Enter to launch the $mode run."

    local process_start_ns
    process_start_ns="$(now_ns)"
    set +e
    JAVA_TOOL_OPTIONS="$java_tool_options" java -jar "$JAR" "${run_args[@]}" >"$wrapper_output" 2>&1 &
    local wrapper_pid=$!
    set -e

    read -r -p "When the launcher is fully visible and stable, press Enter."
    local launcher_ready_ns
    launcher_ready_ns="$(now_ns)"

    echo "Return focus to this terminal. Press Enter immediately before clicking Play Starsector."
    read -r
    local play_start_ns
    play_start_ns="$(now_ns)"

    read -r -p "When the Starsector main menu is fully visible and responsive, press Enter."
    local main_menu_ns
    main_menu_ns="$(now_ns)"

    echo "Now exit Starsector from the main menu and close the launcher if it reappears."
    set +e
    wait "$wrapper_pid"
    local preflight_exit=$?
    set -e

    extract_log_delta "$before_logs" "$log_delta"
    classify_log_delta "$log_delta" "$log_classification"

    local launcher_ms play_to_menu_ms
    launcher_ms="$(python3 - "$process_start_ns" "$launcher_ready_ns" <<'PY'
import sys
print(round((int(sys.argv[2]) - int(sys.argv[1])) / 1_000_000, 3))
PY
)"
    play_to_menu_ms="$(python3 - "$play_start_ns" "$main_menu_ns" <<'PY'
import sys
print(round((int(sys.argv[2]) - int(sys.argv[1])) / 1_000_000, 3))
PY
)"

    local launcher_ok main_menu_ok attached_ok clean_exit_ok corruption_seen
    launcher_ok="$(ask_yes_no "Did the $mode launcher look normal?")"
    main_menu_ok="$(ask_yes_no "Did the $mode main menu look normal?")"
    attached_ok="$(ask_yes_no "Did the terminal command remain running until Starsector exited?")"
    clean_exit_ok="$(ask_yes_no "Did Starsector exit cleanly without a crash dialog?")"
    corruption_seen="$(ask_yes_no "Did you see black, sliced, repeated, stretched, missing, flipped, or worsening textures?")"

    local lifecycle_check=1 telemetry_check=0
    if [[ -f "$run_dir/run.json" ]]; then
        set +e
        jq -e '
            .outcome == "COMPLETED"
            and .exitCode == 0
            and .launcherExitCode == 0
            and .lifecycleEvidence.fatalDetected == false
            and ((.ended | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)
                - (.started | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)) >= $minRuntime
        ' --argjson minRuntime "$MIN_RUNTIME_SECONDS" "$run_dir/run.json" >/dev/null
        lifecycle_check=$?
        set -e
    fi

    if [[ "$mode" == prepared ]]; then
        telemetry_check=1
        if [[ -f "$run_dir/adapter.json" ]]; then
            set +e
            jq -e '
                .transformationsApplied == 1
                and .containedFailures == 0
                and .textureCompatibility.preparedPixels.coherentDirectEnabled == true
                and .textureCompatibility.preparedPixels.hits > 0
                and .textureCompatibility.preparedPixels.coherentDirectHits > 0
                and .textureCompatibility.preparedPixels.fallbacks == 0
                and .textureCompatibility.preparedPixels.internalErrors == 0
                and .textureCompatibility.preparedPixels.activeDirectBytes == 0
                and .textureCompatibility.preparedPixels.activeBuffers == 0
                and .textureCompatibility.preparedPixels.pendingBuffers == 0
            ' "$run_dir/adapter.json" >/dev/null
            telemetry_check=$?
            set -e
        fi
    fi

    local operator_accepted=false automated_accepted=false
    if [[ "$launcher_ok" == true && "$main_menu_ok" == true && "$attached_ok" == true \
            && "$clean_exit_ok" == true && "$corruption_seen" == false ]]; then
        operator_accepted=true
    fi
    if [[ "$preflight_exit" -eq 0 && "$lifecycle_check" -eq 0 && "$telemetry_check" -eq 0 ]]; then
        automated_accepted=true
    fi

    jq -n \
        --arg mode "$mode" \
        --argjson launcherReadyMs "$launcher_ms" \
        --argjson playToMainMenuMs "$play_to_menu_ms" \
        --argjson preflightExit "$preflight_exit" \
        --argjson launcherVisualsNormal "$launcher_ok" \
        --argjson mainMenuNormal "$main_menu_ok" \
        --argjson commandAttachedUntilExit "$attached_ok" \
        --argjson cleanExitObserved "$clean_exit_ok" \
        --argjson visualCorruptionObserved "$corruption_seen" \
        --argjson operatorAccepted "$operator_accepted" \
        --argjson automatedAccepted "$automated_accepted" \
        --slurpfile logClassification "$log_classification" \
        '{
            mode: $mode,
            launcherReadyMs: $launcherReadyMs,
            playToMainMenuMs: $playToMainMenuMs,
            timingMethod: "operator-marked-monotonic-clock",
            preflightExit: $preflightExit,
            launcherVisualsNormal: $launcherVisualsNormal,
            mainMenuNormal: $mainMenuNormal,
            commandAttachedUntilExit: $commandAttachedUntilExit,
            cleanExitObserved: $cleanExitObserved,
            visualCorruptionObserved: $visualCorruptionObserved,
            operatorAccepted: $operatorAccepted,
            automatedAccepted: $automatedAccepted,
            logClassification: $logClassification[0]
        }' > "$result"

    if [[ "$operator_accepted" != true || "$automated_accepted" != true ]]; then
        echo "$mode comparison half failed its acceptance checks." >&2
        return 1
    fi
}

cat <<'NOTICE'

This pilot runs the same exact profile twice:
  compatibility/original texture path
  accepted coherent-direct prepared path

For each half you will mark launcher readiness and main-menu readiness in the terminal.
A single pair is preliminary evidence only. It cannot support an acceleration claim.
Do not enter a campaign or combat. Exit from the main menu after each half.
NOTICE
read -r -p "Type COMPARE to authorize this two-run main-menu pilot: " confirmation
if [[ "$confirmation" != COMPARE ]]; then
    echo "Comparison pilot was not authorized." >&2
    exit 1
fi

comparison_failed=0
for mode in "$first_mode" "$second_mode"; do
    if ! run_mode "$mode"; then
        comparison_failed=1
        break
    fi
done

if [[ -f "$ROOT_DIR/compatibility/operator-main-menu-result.json" \
        && -f "$ROOT_DIR/prepared/operator-main-menu-result.json" ]]; then
    python3 - "$ROOT_DIR/compatibility/operator-main-menu-result.json" \
        "$ROOT_DIR/prepared/operator-main-menu-result.json" \
        "$ROOT_DIR/comparison-result.json" "$ORDER" <<'PY'
import json
import sys
from pathlib import Path

compatibility = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
prepared = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
counts_a = compatibility["logClassification"]["counts"]
counts_b = prepared["logClassification"]["counts"]
keys = sorted(set(counts_a) | set(counts_b))
result = {
    "scope": "main-menu-comparison-pilot",
    "order": sys.argv[4].split(","),
    "samplesPerMode": 1,
    "preliminaryOnly": True,
    "benchmarkAccepted": False,
    "compatibility": compatibility,
    "prepared": prepared,
    "preparedMinusCompatibilityMs": {
        "launcherReady": round(prepared["launcherReadyMs"] - compatibility["launcherReadyMs"], 3),
        "playToMainMenu": round(prepared["playToMainMenuMs"] - compatibility["playToMainMenuMs"], 3),
    },
    "preparedMinusCompatibilityLogCounts": {
        key: counts_b.get(key, 0) - counts_a.get(key, 0) for key in keys
    },
    "logPatternCountsEqual": all(counts_a.get(key, 0) == counts_b.get(key, 0) for key in keys),
    "nextDecision": "review-log-deltas-before-repeat-timing-campaign",
}
Path(sys.argv[3]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
fi

tar -czf "$ARCHIVE_OUT" -C "$(dirname "$ROOT_DIR")" "$(basename "$ROOT_DIR")"

if [[ "$comparison_failed" -ne 0 || ! -f "$ROOT_DIR/comparison-result.json" ]]; then
    echo "The comparison pilot did not complete both accepted halves." >&2
    echo "Upload: $ARCHIVE_OUT" >&2
    exit 1
fi

echo
echo "Comparison pilot completed. Preliminary result:"
jq '{
    samplesPerMode,
    preliminaryOnly,
    benchmarkAccepted,
    preparedMinusCompatibilityMs,
    preparedMinusCompatibilityLogCounts,
    logPatternCountsEqual,
    nextDecision
}' "$ROOT_DIR/comparison-result.json"
echo
echo "Upload this retained evidence archive:"
echo "  $ARCHIVE_OUT"
echo
echo "Do not repeat the pilot or claim a benchmark result."
