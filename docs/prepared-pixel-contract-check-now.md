# Prepared-pixel contract check: current operator command

This is the copy-paste-safe command sequence for the current installed-class review gate.

The previous handoff used a literal `/path/to/Starsector.app` placeholder and did not force a clean rebuild or verify that the checker class was present in the packaged JAR. Do not copy that placeholder unchanged.

## 1. Sync and rebuild the packaged JAR

```bash
git switch main
git pull --ff-only
mvn --batch-mode --no-transfer-progress -pl preflight-cli -am clean package
```

Confirm that the newly built shaded JAR contains the checker before continuing:

```bash
jar tf preflight-cli/target/preflight.jar \
  | grep 'dev/starsector/preflight/agent/PreparedPixelContractCheck.class'
```

The command must print exactly one matching class path. Stop if it prints nothing.

## 2. Locate the real macOS installation

Check the usual locations:

```bash
find /Applications "$HOME/Applications" \
  -maxdepth 2 -name 'Starsector.app' -print 2>/dev/null
```

Set `STARSECTOR_HOME` to the path that command actually prints. Example only:

```bash
export STARSECTOR_HOME="/Applications/Starsector.app"
export PREFLIGHT_CORE_JAR="$STARSECTOR_HOME/Contents/Resources/Java/fs.common_obf.jar"
```

Validate both paths:

```bash
test -d "$STARSECTOR_HOME" || { echo "Starsector app not found: $STARSECTOR_HOME"; exit 1; }
test -f "$PREFLIGHT_CORE_JAR" || { echo "Core JAR not found: $PREFLIGHT_CORE_JAR"; exit 1; }
```

## 3. Run only the read-only checker

In zsh, enable pipeline failure propagation so `tee` cannot hide the Java exit status:

```bash
set -o pipefail
rm -f prepared-pixel-contract.txt

java -cp preflight-cli/target/preflight.jar \
  dev.starsector.preflight.agent.PreparedPixelContractCheck \
  "$PREFLIGHT_CORE_JAR" \
  | tee prepared-pixel-contract.txt

status=$?
echo "checker exit status: $status"
exit "$status"
```

Exit status `0` means the exact offline transformation completed. Exit status `6` means the contract safely declined. Other nonzero values indicate an invocation, I/O, or unexpected execution failure.

## Stop point

Stop after producing `prepared-pixel-contract.txt`. Do not launch with `--texture-mode prepared-pixels` until a reviewing LLM has inspected the report.
