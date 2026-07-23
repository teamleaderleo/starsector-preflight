#!/usr/bin/env bash
set -euo pipefail

GAME="${GAME:-/Applications/Starsector.app}"
CACHE="${CACHE:-$HOME/.starsector-preflight/cache}"
ORDER="${ORDER:-}"
JAR="$PWD/preflight-cli/target/preflight.jar"
DETECTOR="$PWD/scripts/starsector_log_ready_detector.py"
DIAGNOSTIC_PROPERTY="-Dpreflight.preparedPixels.coherentDirect=true"
EXPECTED_ARCHIVE_SHA="10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708"
EXPECTED_CLASS_SHA="d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50"
ACCEPTED_GAMEPLAY_ARCHIVE_SHA="cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf"
AXIS_CONTRACT='return new DimensionSetters(setters.get(1), setters.get(0));'
MIN_RUNTIME_SECONDS=30
LAUNCHER_TIMEOUT_SECONDS=60
LAUNCHER_QUIET_SECONDS=6
MAIN_MENU_TIMEOUT_SECONDS=300
MAIN_MENU_QUIET_SECONDS=6
CORE_RESOURCE_ROOT="$GAME/Contents/Resources/starfarer.res/res"
CORE_MISSION_LIST="$CORE_RESOURCE_ROOT/data/missions/mission_list.csv"
CORE_MISSION_DESCRIPTOR="$CORE_RESOURCE_ROOT/data/missions/afistfulofcredits/descriptor.json"

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
if [[ ! -f "$DETECTOR" ]]; then
    echo "Automatic log detector not found: $DETECTOR" >&2
    exit 1
fi
if [[ ! -d "$GAME" ]]; then
    echo "Starsector installation not found: $GAME" >&2
    exit 1
fi
for core_resource in "$CORE_MISSION_LIST" "$CORE_MISSION_DESCRIPTOR"; do
    if [[ ! -f "$core_resource" ]]; then
        echo "Reviewed core mission resource is missing: $core_resource" >&2
        exit 1
    fi
done

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
BASE_PROFILE="$ROOT_DIR/operator-preparation-profile.json"
jq '.stages.census.details.profile' "$PREP_REPORT" > "$BASE_PROFILE"
EXPECTED_PROFILE_FINGERPRINT="$(jq -er '.profileFingerprint' "$BASE_PROFILE")"
CORE_MISSION_LIST_SHA="$(shasum -a 256 "$CORE_MISSION_LIST" | awk '{print $1}')"
CORE_MISSION_DESCRIPTOR_SHA="$(shasum -a 256 "$CORE_MISSION_DESCRIPTOR" | awk '{print $1}')"
LOG_DIR="$INSTALL_ROOT/logs"
if [[ ! -d "$LOG_DIR" ]]; then
    echo "Starsector log directory not found: $LOG_DIR" >&2
    exit 1
fi

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
timingMethod=automatic-starsector-log-phase-detection
expectedProfileFingerprint=$EXPECTED_PROFILE_FINGERPRINT
coreMissionList=$CORE_MISSION_LIST
coreMissionListSha256=$CORE_MISSION_LIST_SHA
coreMissionDescriptor=$CORE_MISSION_DESCRIPTOR
coreMissionDescriptorSha256=$CORE_MISSION_DESCRIPTOR_SHA
launcherQuietConfirmationSeconds=$LAUNCHER_QUIET_SECONDS
benchmarkAccepted=false
IDENTITY

now_ns() {
    python3 - <<'PY'
import time
print(time.monotonic_ns())
PY
}

snapshot_logs() {
    python3 "$DETECTOR" snapshot --log-dir "$LOG_DIR" --output "$1"
}

extract_log_delta() {
    python3 "$DETECTOR" extract --log-dir "$LOG_DIR" --snapshot "$1" --output "$2"
}

classify_log_delta() {
    python3 "$DETECTOR" classify --input "$1" --output "$2"
}

write_profile_drift() {
    local current_report="$1"
    local output="$2"
    python3 - "$BASE_PROFILE" "$current_report" "$output" <<'PYPROFILE'
import json
import sys
from pathlib import Path

expected = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
report = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
actual = report["stages"]["census"]["details"]["profile"]

expected_mods = {entry["id"]: entry for entry in expected.get("mods", [])}
actual_mods = {entry["id"]: entry for entry in actual.get("mods", [])}
numeric_fields = (
    "files", "bytes", "imageFiles", "imageBytes", "soundFiles", "soundBytes",
    "looseJavaFiles", "looseJavaBytes", "jarFiles", "jarBytes", "dataFiles", "dataBytes",
)
mod_deltas = []
for mod_id in sorted(set(expected_mods) | set(actual_mods)):
    before = expected_mods.get(mod_id)
    after = actual_mods.get(mod_id)
    if before is None or after is None:
        mod_deltas.append({"id": mod_id, "before": before, "after": after})
        continue
    deltas = {
        field: after.get(field, 0) - before.get(field, 0)
        for field in numeric_fields
        if after.get(field, 0) != before.get(field, 0)
    }
    if deltas:
        mod_deltas.append({
            "id": mod_id,
            "directory": after.get("directory"),
            "deltas": deltas,
        })

graphicslib_deltas = mod_deltas[0].get("deltas", {}) if len(mod_deltas) == 1 else {}
graphicslib_only = (
    len(mod_deltas) == 1
    and mod_deltas[0].get("id") == "shaderLib"
    and set(graphicslib_deltas).issubset({"files", "bytes", "imageFiles", "imageBytes"})
    and graphicslib_deltas.get("files", 0) > 0
    and graphicslib_deltas.get("files") == graphicslib_deltas.get("imageFiles")
    and graphicslib_deltas.get("bytes", 0) > 0
    and graphicslib_deltas.get("bytes") == graphicslib_deltas.get("imageBytes")
)

result = {
    "stable": expected.get("profileFingerprint") == actual.get("profileFingerprint"),
    "expectedProfileFingerprint": expected.get("profileFingerprint"),
    "actualProfileFingerprint": actual.get("profileFingerprint"),
    "fingerprintKind": actual.get("fingerprintKind"),
    "modDeltas": mod_deltas,
    "graphicsLibRuntimeCacheCandidate": graphicslib_only,
}
Path(sys.argv[3]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PYPROFILE
}

check_profile_stability() {
    local label="$1"
    local report="$2"
    local drift="$3"

    java -jar "$JAR" prepare \
        --game "$GAME" \
        --cache-dir "$CACHE" \
        --report "$report" \
        --deep \
        --verify-lookups >/dev/null

    local actual_fingerprint mission_list_sha mission_descriptor_sha
    actual_fingerprint="$(jq -er '.stages.census.details.profile.profileFingerprint' "$report")"
    mission_list_sha="$(shasum -a 256 "$CORE_MISSION_LIST" | awk '{print $1}')"
    mission_descriptor_sha="$(shasum -a 256 "$CORE_MISSION_DESCRIPTOR" | awk '{print $1}')"
    if [[ "$mission_list_sha" != "$CORE_MISSION_LIST_SHA" \
            || "$mission_descriptor_sha" != "$CORE_MISSION_DESCRIPTOR_SHA" ]]; then
        echo "Reviewed core mission resources changed during the comparison ($label)." >&2
        return 1
    fi
    if [[ "$actual_fingerprint" != "$EXPECTED_PROFILE_FINGERPRINT" ]]; then
        write_profile_drift "$report" "$drift"
        echo "The enabled profile changed during the comparison ($label)." >&2
        jq '{expectedProfileFingerprint, actualProfileFingerprint, modDeltas, graphicsLibRuntimeCacheCandidate}' \
            "$drift" >&2
        return 1
    fi
}

terminate_wrapper() {
    local pid="$1"
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
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
    local game_snapshot="$run_dir/log-snapshot-before-play.json"
    local launcher_detection="$run_dir/launcher-ready-detection.json"
    local main_menu_detection="$run_dir/main-menu-ready-detection.json"
    local log_delta="$run_dir/starsector-log-delta.txt"
    local log_classification="$run_dir/log-classification.json"
    local wrapper_output="$run_dir/operator-wrapper-output.txt"
    local result="$run_dir/operator-main-menu-result.json"
    local profile_before_report="$run_dir/profile-check-before.json"
    local profile_before_drift="$run_dir/profile-drift-before.json"
    local profile_after_report="$run_dir/profile-check-after.json"
    local profile_after_drift="$run_dir/profile-drift-after.json"
    local -a run_args
    local java_tool_options="${JAVA_TOOL_OPTIONS:-}"

    mkdir -p "$run_dir"
    if ! check_profile_stability "before-$mode" "$profile_before_report" "$profile_before_drift"; then
        return 1
    fi
    snapshot_logs "$before_logs"

    run_args=(
        run
        --game "$INSTALL_ROOT"
        --launcher "$LAUNCHER"
        --trace-dir "$run_dir"
        --adapter
        --texture-mode "$([[ "$mode" == compatibility ]] && printf compatibility || printf prepared-pixels)"
        --texture-cache-dir "$CACHE_DIRECTORY"
        --texture-manifest "$TEXTURE_MANIFEST"
        --texture-index "$RESOURCE_INDEX"
    )
    if [[ "$mode" == prepared ]]; then
        java_tool_options="${java_tool_options:+$java_tool_options }$DIAGNOSTIC_PROPERTY"
    fi

    echo
    echo "== $mode dry-run plan =="
    JAVA_TOOL_OPTIONS="$java_tool_options" java -jar "$JAR" "${run_args[@]}" --dry-run
    echo
    echo "This is the $mode half of a two-run comparison pilot."
    echo "The script will detect launcher readiness and main-menu readiness from starsector.log."
    echo "Do not load a campaign. Exit from the main menu when instructed."
    read -r -p "Press Enter to launch the $mode run."

    local process_start_ns
    process_start_ns="$(now_ns)"
    set +e
    JAVA_TOOL_OPTIONS="$java_tool_options" java -jar "$JAR" "${run_args[@]}" >"$wrapper_output" 2>&1 &
    local wrapper_pid=$!
    set -e

    echo "Waiting for the launcher readiness marker..."
    if ! python3 "$DETECTOR" watch-launcher \
            --log-dir "$LOG_DIR" \
            --snapshot "$before_logs" \
            --output "$launcher_detection" \
            --pid "$wrapper_pid" \
            --process-start-ns "$process_start_ns" \
            --timeout-seconds "$LAUNCHER_TIMEOUT_SECONDS" \
            --quiet-seconds "$LAUNCHER_QUIET_SECONDS"; then
        echo "Automatic launcher readiness detection failed." >&2
        terminate_wrapper "$wrapper_pid"
        extract_log_delta "$before_logs" "$log_delta"
        classify_log_delta "$log_delta" "$log_classification"
        check_profile_stability "after-$mode-launcher-failure" \
            "$profile_after_report" "$profile_after_drift" || true
        return 1
    fi

    echo
    printf '\a'
    echo "Launcher readiness detected automatically after the fixed safety confirmation."
    jq '{launcherReadyMs, launcherReadyLogMillis, launcherMarker, quietConfirmationMillis}' "$launcher_detection"
    snapshot_logs "$game_snapshot"
    echo
    echo "Click Play Starsector now. Do not press Enter; the log watcher starts timing on the first new game log line."

    if ! python3 "$DETECTOR" watch-main-menu \
            --log-dir "$LOG_DIR" \
            --snapshot "$game_snapshot" \
            --output "$main_menu_detection" \
            --pid "$wrapper_pid" \
            --timeout-seconds "$MAIN_MENU_TIMEOUT_SECONDS" \
            --quiet-seconds "$MAIN_MENU_QUIET_SECONDS"; then
        echo "Automatic main-menu readiness detection failed." >&2
        terminate_wrapper "$wrapper_pid"
        extract_log_delta "$before_logs" "$log_delta"
        classify_log_delta "$log_delta" "$log_classification"
        check_profile_stability "after-$mode-main-menu-failure" \
            "$profile_after_report" "$profile_after_drift" || true
        return 1
    fi

    echo
    printf '\a'
    echo "Main-menu readiness detected automatically."
    jq '{gameLogStartToMainMenuMs, gameLogMillisDelta, saveDescriptorSeen, graphicsPreloadSeen, quietConfirmationMillis}' \
        "$main_menu_detection"
    echo "Confirm the main menu is fully visible and responsive, then exit Starsector from the main menu."
    echo "Close the launcher if it reappears."

    set +e
    wait "$wrapper_pid"
    local preflight_exit=$?
    set -e

    extract_log_delta "$before_logs" "$log_delta"
    classify_log_delta "$log_delta" "$log_classification"

    local profile_stable=true
    if ! check_profile_stability "after-$mode" "$profile_after_report" "$profile_after_drift"; then
        profile_stable=false
    fi

    local launcher_ok main_menu_ok detector_ok attached_ok clean_exit_ok corruption_seen
    launcher_ok="$(ask_yes_no "Did the $mode launcher look normal?")"
    main_menu_ok="$(ask_yes_no "Did the $mode main menu look normal?")"
    detector_ok="$(ask_yes_no "Did the automatic main-menu notification occur only after the menu was fully visible and responsive?")"
    attached_ok="$(ask_yes_no "Did the terminal command remain running until Starsector exited?")"
    clean_exit_ok="$(ask_yes_no "Did Starsector exit cleanly without a crash dialog?")"
    corruption_seen="$(ask_yes_no "Did you see black, sliced, repeated, stretched, missing, flipped, or worsening textures?")"

    local lifecycle_check=1 telemetry_check=0 log_check=1 launcher_detector_check=1 menu_detector_check=1
    local expected_texture_mode
    if [[ "$mode" == compatibility ]]; then
        expected_texture_mode="COMPATIBILITY"
    else
        expected_texture_mode="PREPARED_PIXELS"
    fi
    if [[ -f "$run_dir/run.json" ]]; then
        set +e
        jq -e '
            .outcome == "COMPLETED"
            and .exitCode == 0
            and .launcherExitCode == 0
            and .lifecycleEvidence.fatalDetected == false
            and .textureAdapterMode == $expectedTextureMode
            and ((.ended | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)
                - (.started | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)) >= $minRuntime
        ' --arg expectedTextureMode "$expected_texture_mode" --argjson minRuntime "$MIN_RUNTIME_SECONDS" "$run_dir/run.json" >/dev/null
        lifecycle_check=$?
        set -e
    fi

    set +e
    jq -e '.bytes > 0' "$log_classification" >/dev/null
    log_check=$?
    jq -e '.detected == true and .launcherReadyMs > 0 and .observedLines > 0' "$launcher_detection" >/dev/null
    launcher_detector_check=$?
    jq -e '
        .detected == true
        and .gameLogStartToMainMenuMs > 0
        and .saveDescriptorSeen == true
        and .graphicsPreloadSeen == true
        and .observedLines > 0
    ' "$main_menu_detection" >/dev/null
    menu_detector_check=$?
    set -e

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
    if [[ "$launcher_ok" == true && "$main_menu_ok" == true && "$detector_ok" == true \
            && "$attached_ok" == true && "$clean_exit_ok" == true && "$corruption_seen" == false ]]; then
        operator_accepted=true
    fi
    if [[ "$preflight_exit" -eq 0 && "$lifecycle_check" -eq 0 && "$telemetry_check" -eq 0 \
            && "$log_check" -eq 0 && "$launcher_detector_check" -eq 0 && "$menu_detector_check" -eq 0 \
            && "$profile_stable" == true ]]; then
        automated_accepted=true
    fi

    jq -n \
        --arg mode "$mode" \
        --argjson preflightExit "$preflight_exit" \
        --argjson launcherVisualsNormal "$launcher_ok" \
        --argjson mainMenuNormal "$main_menu_ok" \
        --argjson automaticDetectionVisuallyAccurate "$detector_ok" \
        --argjson commandAttachedUntilExit "$attached_ok" \
        --argjson cleanExitObserved "$clean_exit_ok" \
        --argjson visualCorruptionObserved "$corruption_seen" \
        --argjson operatorAccepted "$operator_accepted" \
        --argjson automatedAccepted "$automated_accepted" \
        --argjson profileStable "$profile_stable" \
        --arg expectedProfileFingerprint "$EXPECTED_PROFILE_FINGERPRINT" \
        --slurpfile launcherDetection "$launcher_detection" \
        --slurpfile mainMenuDetection "$main_menu_detection" \
        --slurpfile logClassification "$log_classification" \
        '{
            mode: $mode,
            launcherReadyMs: $launcherDetection[0].launcherReadyMs,
            gameLogStartToMainMenuMs: $mainMenuDetection[0].gameLogStartToMainMenuMs,
            gameLogMillisDelta: $mainMenuDetection[0].gameLogMillisDelta,
            timingMethod: "automatic-starsector-log-phase-detection",
            preflightExit: $preflightExit,
            launcherVisualsNormal: $launcherVisualsNormal,
            mainMenuNormal: $mainMenuNormal,
            automaticDetectionVisuallyAccurate: $automaticDetectionVisuallyAccurate,
            commandAttachedUntilExit: $commandAttachedUntilExit,
            cleanExitObserved: $cleanExitObserved,
            visualCorruptionObserved: $visualCorruptionObserved,
            operatorAccepted: $operatorAccepted,
            automatedAccepted: $automatedAccepted,
            profileStable: $profileStable,
            expectedProfileFingerprint: $expectedProfileFingerprint,
            launcherDetection: $launcherDetection[0],
            mainMenuDetection: $mainMenuDetection[0],
            logClassification: $logClassification[0]
        }' > "$result"

    if [[ "$operator_accepted" != true || "$automated_accepted" != true ]]; then
        echo "$mode comparison half failed its acceptance checks." >&2
        return 1
    fi
}

cat <<'NOTICE'

This pilot runs the same exact profile twice:
  compatibility decoded-image path
  accepted coherent-direct prepared path

The runner automatically detects launcher readiness and main-menu readiness from starsector.log.
It uses a fixed six-second launcher safety confirmation without adding that wait to the measured endpoint.
It also verifies that the enabled profile does not change between halves.
You only need to click Play when instructed, visually confirm the result, and exit from the main menu.
A single pair is preliminary evidence only. It cannot support an acceleration claim.
Do not enter a campaign or combat.
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
    "timingMethod": "automatic-starsector-log-phase-detection",
    "compatibility": compatibility,
    "prepared": prepared,
    "preparedMinusCompatibilityMs": {
        "launcherReady": round(prepared["launcherReadyMs"] - compatibility["launcherReadyMs"], 3),
        "gameLogStartToMainMenu": round(
            prepared["gameLogStartToMainMenuMs"] - compatibility["gameLogStartToMainMenuMs"], 3
        ),
    },
    "preparedMinusCompatibilityLogCounts": {
        key: counts_b.get(key, 0) - counts_a.get(key, 0) for key in keys
    },
    "logPatternCountsEqual": all(counts_a.get(key, 0) == counts_b.get(key, 0) for key in keys),
    "automaticDetectionVisuallyAccepted": (
        compatibility["automaticDetectionVisuallyAccurate"]
        and prepared["automaticDetectionVisuallyAccurate"]
    ),
    "profileStableAcrossBothHalves": (
        compatibility.get("profileStable") is True
        and prepared.get("profileStable") is True
        and compatibility.get("expectedProfileFingerprint")
            == prepared.get("expectedProfileFingerprint")
    ),
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
    timingMethod,
    preparedMinusCompatibilityMs,
    preparedMinusCompatibilityLogCounts,
    logPatternCountsEqual,
    automaticDetectionVisuallyAccepted,
    profileStableAcrossBothHalves,
    nextDecision
}' "$ROOT_DIR/comparison-result.json"
echo
echo "Upload this retained evidence archive:"
echo "  $ARCHIVE_OUT"
echo
echo "Do not repeat the pilot or claim a benchmark result without reviewing the retained profile-stability evidence."
