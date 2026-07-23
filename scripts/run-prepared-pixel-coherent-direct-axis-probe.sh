#!/usr/bin/env bash
set -euo pipefail

GAME="${GAME:-/Applications/Starsector.app}"
CACHE="${CACHE:-$HOME/.starsector-preflight/cache}"
JAR="$PWD/preflight-cli/target/preflight.jar"
DIAGNOSTIC_PROPERTY="-Dpreflight.preparedPixels.coherentDirect=true"
EXPECTED_ARCHIVE_SHA="10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708"
EXPECTED_CLASS_SHA="d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50"
AXIS_CONTRACT='return new DimensionSetters(setters.get(1), setters.get(0));'

for command in git java mvn jq shasum unzip tar grep; do
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

STAMP="$(date -u +%Y%m%d-%H%M%S)"
REPORT_DIR="$CACHE/reports"
CONTRACT_REPORT="$REPORT_DIR/prepared-pixel-coherent-direct-axis-contract-$STAMP.json"
PREP_REPORT="$REPORT_DIR/prepared-pixel-coherent-direct-axis-preparation-$STAMP.json"
RUN_DIR="$HOME/.starsector-preflight/runs/prepared-pixel-coherent-direct-axis-probe-$STAMP"
mkdir -p "$REPORT_DIR"

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

echo
echo "== Verifying exact installed identities =="
echo "Archive SHA-256: $ARCHIVE_SHA"
echo "Class SHA-256:   $CLASS_SHA"

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
java -cp "$JAR" \
    dev.starsector.preflight.agent.PreparedPixelContractCheck \
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
    and .readiness.preparedPixelsAdapter == "pot-bypass-enabled-npot-coherent-direct-axis-diagnostic"
    and .readiness.preparedPixelsNextOperatorAction == "launcher-only-coherent-direct-axis-probe"
    and .readiness.launchAccelerationClaimed == false
' "$PREP_REPORT" >/dev/null

INSTALL_ROOT="$(jq -er '.installRoot' "$PREP_REPORT")"
LAUNCHER="$(jq -er '.launcher' "$PREP_REPORT")"
CACHE_DIRECTORY="$(jq -er '.cacheDirectory' "$PREP_REPORT")"
RESOURCE_INDEX="$(jq -er '.resourceIndex' "$PREP_REPORT")"
TEXTURE_MANIFEST="$(jq -er '.stages.textures.details.manifest' "$PREP_REPORT")"

for file in "$RESOURCE_INDEX" "$TEXTURE_MANIFEST"; do
    if [[ ! -f "$file" ]]; then
        echo "Preparation artifact does not exist: $file" >&2
        exit 1
    fi
done

RUN_ARGS=(
    run
    --game "$INSTALL_ROOT"
    --launcher "$LAUNCHER"
    --trace-dir "$RUN_DIR"
    --adapter
    --texture-mode prepared-pixels
    --texture-cache-dir "$CACHE_DIRECTORY"
    --texture-manifest "$TEXTURE_MANIFEST"
    --texture-index "$RESOURCE_INDEX"
)

DIAGNOSTIC_JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }$DIAGNOSTIC_PROPERTY"

echo
echo "== Exact launcher-only axis diagnostic plan =="
JAVA_TOOL_OPTIONS="$DIAGNOSTIC_JAVA_TOOL_OPTIONS" \
    java -jar "$JAR" "${RUN_ARGS[@]}" --dry-run

echo
echo "This run uses coherent source-sized cached images, direct cached upload"
echo "buffers, cached colors, and the reviewed texture-object dimension mapping:"
echo "  first obfuscated setter  <- power-of-two upload height"
echo "  second obfuscated setter <- power-of-two upload width"
echo
echo "When the launcher appears:"
echo "  1. Decide whether every launcher visual looks normal or broken."
echo "  2. DO NOT click Play or start Starsector."
echo "  3. Close the launcher with its X button."
echo
read -r -p "Press Enter to start the single coherent-direct axis probe, or Ctrl-C to stop: " _

mkdir -p "$RUN_DIR"
{
    printf 'repositoryHead=%s\n' "$REPOSITORY_HEAD"
    printf 'jarSha256=%s\n' "$JAR_SHA"
    printf 'archiveSha256=%s\n' "$ARCHIVE_SHA"
    printf 'classSha256=%s\n' "$CLASS_SHA"
    printf 'diagnosticProperty=%s\n' "$DIAGNOSTIC_PROPERTY"
    printf 'dimensionReplay=%s\n' 'reviewed-converter-height-first-width-second'
    printf 'command=JAVA_TOOL_OPTIONS=%q ' "$DIAGNOSTIC_JAVA_TOOL_OPTIONS"
    printf '%q ' java -jar "$JAR" "${RUN_ARGS[@]}"
    printf '\n'
} > "$RUN_DIR/operator-probe-identity.txt"
cp "$CONTRACT_REPORT" "$RUN_DIR/operator-contract.json"
cp "$PREP_REPORT" "$RUN_DIR/operator-preparation.json"

echo
echo "== Starting launcher-only coherent-direct axis probe =="
set +e
JAVA_TOOL_OPTIONS="$DIAGNOSTIC_JAVA_TOOL_OPTIONS" \
    java -jar "$JAR" "${RUN_ARGS[@]}"
probe_exit=$?
set -e

echo
echo "Preflight exit: $probe_exit"
echo "Run directory: $RUN_DIR"

if [[ ! -f "$RUN_DIR/run.json" ]]; then
    echo "The probe did not produce run.json." >&2
    exit 1
fi
if [[ ! -f "$RUN_DIR/adapter.json" ]]; then
    echo "The probe did not produce adapter.json." >&2
    exit 1
fi

echo
echo "== Lifecycle result =="
jq '{
    outcome,
    exitCode,
    launcherExitCode,
    lifecycleEvidence,
    launcherConsole,
    launcherConsoleCapture
}' "$RUN_DIR/run.json"

echo
echo "== Coherent-direct axis telemetry =="
jq '.textureCompatibility.preparedPixels' "$RUN_DIR/adapter.json"

jq -e '
    .outcome == "COMPLETED"
    and .exitCode == 0
    and .launcherExitCode == 0
    and .lifecycleEvidence.fatalDetected == false
' "$RUN_DIR/run.json" >/dev/null

jq -e '
    .transformationsApplied == 1
    and .containedFailures == 0
    and .textureCompatibility.preparedPixels.coherentDirectEnabled == true
    and .textureCompatibility.preparedPixels.coherentDirectCarriers > 0
    and .textureCompatibility.preparedPixels.coherentDirectHits > 0
    and .textureCompatibility.preparedPixels.coherentCarriers >= .textureCompatibility.preparedPixels.coherentDirectCarriers
    and .textureCompatibility.preparedPixels.coherentCarrierBytes > 0
    and .textureCompatibility.preparedPixels.coherentOriginalConvertFallbacks == 0
    and .textureCompatibility.preparedPixels.coherentOriginalDecodeBypasses == 0
    and .textureCompatibility.preparedPixels.npotProbeFallbacks == 0
    and .textureCompatibility.preparedPixels.paddedUploads == .textureCompatibility.preparedPixels.coherentDirectHits
    and .textureCompatibility.preparedPixels.paddingBytes > 0
    and .textureCompatibility.preparedPixels.uploadBytesSupplied > .textureCompatibility.preparedPixels.bytesBypassed
    and .textureCompatibility.preparedPixels.internalErrors == 0
    and .textureCompatibility.preparedPixels.activeDirectBytes == 0
    and .textureCompatibility.preparedPixels.activeBuffers == 0
    and .textureCompatibility.preparedPixels.pendingBuffers == 0
' "$RUN_DIR/adapter.json" >/dev/null

grep -Fq 'dimensionReplay=reviewed-converter-height-first-width-second' \
    "$RUN_DIR/operator-probe-identity.txt"

ARCHIVE_OUT="$HOME/Desktop/$(basename "$RUN_DIR").tar.gz"
tar -czf "$ARCHIVE_OUT" -C "$(dirname "$RUN_DIR")" "$(basename "$RUN_DIR")"

if [[ "$probe_exit" -ne 0 ]]; then
    echo "The probe returned a nonzero exit after evidence was retained." >&2
    echo "Upload: $ARCHIVE_OUT" >&2
    exit "$probe_exit"
fi

echo
echo "Automated lifecycle, axis-mapping, direct-buffer, and cleanup checks passed."
echo "Report whether the launcher looked normal or broken, and upload:"
echo "  $ARCHIVE_OUT"
echo
echo "Do not run another probe, start gameplay, or benchmark yet."
