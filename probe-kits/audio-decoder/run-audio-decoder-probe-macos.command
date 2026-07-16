#!/bin/bash
set -u
set -o pipefail

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$HERE" || exit 2

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$HERE/audio-decoder-probe-$STAMP"
RESULTS="$HERE/audio-decoder-probe-results-$STAMP"
ARCHIVE="$RESULTS.zip"
CONSOLE="$HERE/audio-decoder-probe-console-$STAMP.log"
JAR="$HERE/preflight.jar"

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

printf '%s\n' "Starting the read-only Starsector audio and sound-loader contract probe."
printf '%s\n' "Preflight will launch the existing Starsector application selected above."
printf '%s\n' "No class transformation plan or audio-cache activation is included."
printf '%s\n' "Run data: $OUT"
printf '\n%s\n' "During the run:"
printf '%s\n' "  1. Reach the main menu and let the music play for about 30 seconds."
printf '%s\n' "  2. Load a representative save or start a campaign."
printf '%s\n' "  3. Trigger several UI or combat sound effects."
printf '%s\n' "  4. Exit Starsector normally."
printf '\n%s\n' "Resolved launch arguments: $*"
printf '\n'

java -jar "$JAR" run --adapter-probe --trace-dir "$OUT" "$@" 2>&1 | tee "$CONSOLE"
STATUS=${PIPESTATUS[0]}

mkdir -p "$RESULTS"
MISSING="$RESULTS/MISSING_FILES.txt"
: > "$MISSING"

FILES=(
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
    printf 'startupJfrIncluded=false\n'
} > "$RESULTS/probe-kit-metadata.txt"

AUDIO="$OUT/adapter-audio-decoder-signatures.json"
if [ -f "$AUDIO" ]; then
    if grep -Eq '"retainedIdentities":[[:space:]]*[1-9][0-9]*' "$AUDIO"; then
        printf '\n%s\n' "Confirmed: at least one audio-decoder identity was retained."
    else
        printf '\n%s\n' "NOTE: no matching decoder identity was retained. Upload the result ZIP anyway."
    fi
    if grep -Eq '"entriesTruncated":[[:space:]]*true' "$AUDIO"; then
        printf '%s\n' "WARNING: the decoder identity report was truncated."
    fi
else
    printf '\n%s\n' "WARNING: the audio-decoder signature report was not created."
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

if command -v open >/dev/null 2>&1; then
    open -R "$ARCHIVE" >/dev/null 2>&1 || true
fi

exit "$STATUS"
