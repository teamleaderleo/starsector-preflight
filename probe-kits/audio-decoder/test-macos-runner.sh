#!/bin/bash
set -euo pipefail

SOURCE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
RUNNER="$SOURCE_ROOT/probe-kits/audio-decoder/run-audio-decoder-probe-macos.command"

bash -n "$RUNNER"

TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT

KIT="$TEMP/kit"
FAKE_HOME="$TEMP/home"
FAKE_BIN="$TEMP/bin"
FAKE_APP="$FAKE_HOME/Applications/Starsector.app"
CAPTURE_PWD="$TEMP/java-pwd.txt"
CAPTURE_ARGS="$TEMP/java-args.txt"

mkdir -p "$KIT" "$FAKE_HOME/Library/Daemon Containers" "$FAKE_BIN" "$FAKE_APP"
cp "$RUNNER" "$KIT/run-audio-decoder-probe-macos.command"
chmod +x "$KIT/run-audio-decoder-probe-macos.command"
printf 'not-a-real-jar\n' > "$KIT/preflight.jar"
printf 'test-source\n' > "$KIT/SOURCE_COMMIT.txt"

cat > "$FAKE_BIN/java" <<'JAVA'
#!/bin/bash
if [ "${1:-}" = "-version" ]; then
    printf '%s\n' 'openjdk version "17-test"' >&2
    exit 0
fi
printf '%s\n' "$PWD" > "$CAPTURE_PWD"
printf '%s\n' "$@" > "$CAPTURE_ARGS"
exit 0
JAVA
chmod +x "$FAKE_BIN/java"

PATH="$FAKE_BIN:$PATH" \
HOME="$FAKE_HOME" \
CAPTURE_PWD="$CAPTURE_PWD" \
CAPTURE_ARGS="$CAPTURE_ARGS" \
bash -c 'cd "$HOME" && "$1"' _ \
    "$KIT/run-audio-decoder-probe-macos.command"

[ "$(cat "$CAPTURE_PWD")" = "$KIT" ]
grep -Fx -- '--game' "$CAPTURE_ARGS" >/dev/null
grep -Fx -- "$FAKE_APP" "$CAPTURE_ARGS" >/dev/null
find "$KIT" -maxdepth 1 -name 'audio-decoder-probe-results-*.zip' -type f | grep . >/dev/null
