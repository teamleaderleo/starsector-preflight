#!/bin/bash
set -u
set -o pipefail

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$HERE" || exit 2

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$HERE/startup-contract-probe-$STAMP"
RESULTS="$HERE/startup-contract-probe-results-$STAMP"
ARCHIVE="$RESULTS.zip"
CONSOLE="$HERE/startup-contract-probe-console-$STAMP.log"
JAR="$HERE/preflight.jar"
MAX_JFR_BYTES=402653184
INCLUDE_JFR=${PREFLIGHT_INCLUDE_JFR:-1}

if [ ! -f "$JAR" ]; then
    printf '%s\n' "Missing bundled preflight.jar: $JAR"
    exit 2
fi
if ! command -v java >/dev/null 2>&1; then
    printf '%s\n' "Java was not found. Install or select a JDK 17 runtime, then run this script again."
    exit 2
fi

HAS_EXPLICIT_PATH=false
for ARG in "$@"; do
    case "$ARG" in
        --game|--launcher)
            HAS_EXPLICIT_PATH=true
            ;;
    esac
done

if [ "$HAS_EXPLICIT_PATH" = false ]; then
    for CANDIDATE in \
        "/Applications/Starsector.app" \
        "/Applications/starsector.app" \
        "$HOME/Applications/Starsector.app" \
        "$HOME/Applications/starsector.app" \
        "$HOME/Games/Starsector.app"; do
        if [ -d "$CANDIDATE" ]; then
            set -- --game "$CANDIDATE" "$@"
            HAS_EXPLICIT_PATH=true
            break
        fi
    done
fi

if [ "$HAS_EXPLICIT_PATH" = false ] && command -v osascript >/dev/null 2>&1; then
    SELECTED_APP=$(osascript <<'APPLESCRIPT'
try
    POSIX path of (choose application with prompt "Select your Starsector application")
on error number -128
    return ""
end try
APPLESCRIPT
)
    if [ -n "$SELECTED_APP" ]; then
        SELECTED_APP=${SELECTED_APP%/}
        set -- --game "$SELECTED_APP" "$@"
        HAS_EXPLICIT_PATH=true
    fi
fi

printf '%s\n' "Starting the unified read-only Starsector startup contract probe."
printf '%s\n' "One launch will collect texture, Janino/code-loader, audio-decoder, sound-wrapper, and startup CPU evidence."
printf '%s\n' "No class transformation plan, prepared cache read, or prepared cache write is enabled."
printf '%s\n' "Run data: $OUT"
printf '\n%s\n' "During the run:"
printf '%s\n' "  1. Reach the main menu and let its music play for about 30 seconds."
printf '%s\n' "  2. Load the representative save or campaign you normally use."
printf '%s\n' "  3. Open several UI, intel, fleet, colony, or refit screens to exercise textures and scripts."
printf '%s\n' "  4. Trigger several UI or combat sound effects."
printf '%s\n' "  5. Exit Starsector normally after the campaign is fully responsive."
printf '\n%s\n' "The result ZIP includes the startup JFR by default when all JFR files total 384 MiB or less."
printf '%s\n' "Set PREFLIGHT_INCLUDE_JFR=0 before launching this script to exclude raw JFR data."
printf '\n%s\n' "Resolved launch arguments: $*"
printf '\n'

java -jar "$JAR" run --adapter-probe --trace-dir "$OUT" "$@" 2>&1 | tee "$CONSOLE"
STATUS=${PIPESTATUS[0]}

mkdir -p "$RESULTS"
MISSING="$RESULTS/MISSING_FILES.txt"
OMITTED="$RESULTS/OMITTED_FILES.txt"
: > "$MISSING"
: > "$OMITTED"

FILES=(
    adapter-texture-loader-contract.json
    adapter-janino-loader-contract.json
    adapter-sound-loader-contract.json
    adapter-audio-decoder-signatures.json
    adapter-code-loader-signatures.json
    adapter.json
    adapter-analysis.json
    summary.json
    run.json
    profile.json
)

for NAME in "${FILES[@]}"; do
    if [ -f "$OUT/$NAME" ]; then
        cp "$OUT/$NAME" "$RESULTS/$NAME"
    else
        printf '%s\n' "$NAME" >> "$MISSING"
    fi
done
cp "$CONSOLE" "$RESULTS/probe-console.log"

file_size() {
    if stat -f%z "$1" >/dev/null 2>&1; then
        stat -f%z "$1"
    else
        stat -c%s "$1"
    fi
}

JFR_INCLUDED=false
JFR_COUNT=0
JFR_BYTES=0
JFR_LIST="$RESULTS/.jfr-list-$STAMP"
find "$OUT" -type f -name '*.jfr' -print > "$JFR_LIST" 2>/dev/null || true
if [ "$INCLUDE_JFR" = "1" ] || [ "$INCLUDE_JFR" = "true" ] || [ "$INCLUDE_JFR" = "yes" ]; then
    while IFS= read -r JFR; do
        [ -f "$JFR" ] || continue
        SIZE=$(file_size "$JFR")
        NEXT=$((JFR_BYTES + SIZE))
        if [ "$NEXT" -le "$MAX_JFR_BYTES" ]; then
            mkdir -p "$RESULTS/raw-jfr"
            JFR_COUNT=$((JFR_COUNT + 1))
            BASE=$(basename "$JFR")
            cp "$JFR" "$RESULTS/raw-jfr/$JFR_COUNT-$BASE"
            JFR_BYTES=$NEXT
            JFR_INCLUDED=true
        else
            printf '%s\n' "$JFR (omitted because the cumulative JFR size would exceed 384 MiB)" >> "$OMITTED"
        fi
    done < "$JFR_LIST"
else
    while IFS= read -r JFR; do
        [ -f "$JFR" ] || continue
        printf '%s\n' "$JFR (omitted because PREFLIGHT_INCLUDE_JFR=$INCLUDE_JFR)" >> "$OMITTED"
    done < "$JFR_LIST"
fi
rm -f "$JFR_LIST"

{
    printf 'probeKitSourceCommit='
    if [ -f "$HERE/SOURCE_COMMIT.txt" ]; then
        tr -d '\r\n' < "$HERE/SOURCE_COMMIT.txt"
    else
        printf 'unknown'
    fi
    printf '\ncreatedAt=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'java='
    java -version 2>&1 | head -n 1
    printf 'preflightExitCode=%s\n' "$STATUS"
    printf 'fullRunDirectory=%s\n' "$OUT"
    printf 'startupJfrIncluded=%s\n' "$JFR_INCLUDED"
    printf 'startupJfrCount=%s\n' "$JFR_COUNT"
    printf 'startupJfrBytes=%s\n' "$JFR_BYTES"
    printf 'probeMode=read-only\n'
    printf 'transformPlanEnabled=false\n'
    printf 'cacheReadsEnabled=false\n'
    printf 'cacheWritesEnabled=false\n'
} > "$RESULTS/probe-kit-metadata.txt"

TEXTURE="$OUT/adapter-texture-loader-contract.json"
if [ -f "$TEXTURE" ]; then
    if grep -Eq '"captured":[[:space:]]*true' "$TEXTURE"; then
        printf '\n%s\n' "Confirmed: the exact TextureLoader contract was captured."
    else
        printf '\n%s\n' "NOTE: the exact TextureLoader contract was not captured. Upload the result ZIP anyway."
    fi
else
    printf '\n%s\n' "WARNING: the texture-loader contract report was not created."
fi

JANINO="$OUT/adapter-janino-loader-contract.json"
if [ -f "$JANINO" ]; then
    if grep -Eq '"captured":[[:space:]]*true' "$JANINO"; then
        printf '%s\n' "Confirmed: the exact Janino complete-map loader contract was captured."
    else
        printf '%s\n' "NOTE: the exact Janino complete-map loader contract was not captured. Upload the result ZIP anyway."
    fi
else
    printf '%s\n' "WARNING: the Janino-loader contract report was not created."
fi

CODE="$OUT/adapter-code-loader-signatures.json"
if [ -f "$CODE" ]; then
    if grep -Eq '"retainedIdentities":[[:space:]]*[1-9][0-9]*' "$CODE"; then
        printf '%s\n' "Confirmed: at least one Janino/code-loader identity was retained."
    else
        printf '%s\n' "NOTE: no Janino/code-loader identity was retained. Upload the result ZIP anyway."
    fi
else
    printf '%s\n' "WARNING: the code-loader signature report was not created."
fi

AUDIO="$OUT/adapter-audio-decoder-signatures.json"
if [ -f "$AUDIO" ]; then
    if grep -Eq '"retainedIdentities":[[:space:]]*[1-9][0-9]*' "$AUDIO"; then
        printf '%s\n' "Confirmed: at least one audio-decoder identity was retained."
    else
        printf '%s\n' "NOTE: no matching decoder identity was retained. Upload the result ZIP anyway."
    fi
    if grep -Eq '"entriesTruncated":[[:space:]]*true' "$AUDIO"; then
        printf '%s\n' "WARNING: the decoder identity report was truncated."
    fi
else
    printf '%s\n' "WARNING: the audio-decoder signature report was not created."
fi

SOUND="$OUT/adapter-sound-loader-contract.json"
if [ -f "$SOUND" ]; then
    if grep -Eq '"retainedIdentities":[[:space:]]*[1-9][0-9]*' "$SOUND"; then
        printf '%s\n' "Confirmed: at least one exact sound-loader contract identity was retained."
    else
        printf '%s\n' "NOTE: no exact sound-loader contract identity was retained. Upload the result ZIP anyway."
    fi
    if grep -Eq '"entriesTruncated":[[:space:]]*true' "$SOUND"; then
        printf '%s\n' "WARNING: the sound-loader contract report was truncated."
    fi
else
    printf '%s\n' "WARNING: the sound-loader contract report was not created."
fi

CHECKSUMS="$RESULTS/RESULT_CHECKSUMS.sha256"
if command -v shasum >/dev/null 2>&1; then
    (
        cd "$RESULTS" || exit 1
        find . -type f ! -name 'RESULT_CHECKSUMS.sha256' -print | LC_ALL=C sort | while IFS= read -r FILE; do
            shasum -a 256 "$FILE"
        done
    ) > "$CHECKSUMS"
elif command -v sha256sum >/dev/null 2>&1; then
    (
        cd "$RESULTS" || exit 1
        find . -type f ! -name 'RESULT_CHECKSUMS.sha256' -print | LC_ALL=C sort | while IFS= read -r FILE; do
            sha256sum "$FILE"
        done
    ) > "$CHECKSUMS"
else
    printf '%s\n' "RESULT_CHECKSUMS.sha256 was not generated because no SHA-256 command was available." >> "$OMITTED"
fi

if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --sequesterRsrc --keepParent "$RESULTS" "$ARCHIVE"
elif command -v zip >/dev/null 2>&1; then
    (cd "$RESULTS" && zip -q -r "$ARCHIVE" .)
else
    printf '\n%s\n' "Results are ready at $RESULTS, but no ZIP utility was available."
    exit "$STATUS"
fi

printf '\n%s\n' "Upload this file:"
printf '%s\n' "$ARCHIVE"
if [ -s "$MISSING" ]; then
    printf '%s\n' "Some expected files were absent; they are listed inside MISSING_FILES.txt."
fi
if [ -s "$OMITTED" ]; then
    printf '%s\n' "Some optional files were omitted; they are listed inside OMITTED_FILES.txt."
fi

if command -v open >/dev/null 2>&1; then
    open -R "$ARCHIVE" >/dev/null 2>&1 || true
fi

exit "$STATUS"
