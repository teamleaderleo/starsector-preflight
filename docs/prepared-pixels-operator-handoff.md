# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

This document defines who acts next and where each person must stop. The current goal is one real prepared-pixel lifecycle acceptance run, followed only then by repeated startup measurement.

## Current decision

The offline installed-class contract gate passed on 2026-07-22. See [the retained evidence](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md).

Do not add another optimization subsystem. Do not optimize CI further. Do not begin repeated benchmarks yet.

The immediate sequence is now:

1. operator prepares or verifies the exact current profile cache;
2. operator returns the preparation report and stops;
3. reviewing LLM extracts the exact cache, manifest, and resource-index paths;
4. reviewing LLM supplies one explicit prepared-pixel launch command;
5. operator performs one bounded lifecycle route and stops;
6. reviewing LLM accepts or rejects the retained evidence;
7. repeated measurement begins only after behavioral acceptance.

## Current project state

The repository contains:

- the exact-gated `texture-compatibility-v2` consumer with one bounded real-install behavioral acceptance run;
- the lower `prepared-pixels` consumer;
- support for both direct texture-object color fields and the installed-style staged `TextureLoader` color flow into three distinct texture-object setters;
- `PreparedPixelContractCheck` for an extracted class or containing JAR;
- bounded direct-buffer ownership and fail-open fallback behavior;
- benchmark collection and comparison tooling for repeated OFF-versus-ENABLED campaigns.

The exact installed archive passed the offline contract check:

- archive SHA-256: `10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708`;
- class SHA-256: `d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50`;
- all nine required methods present;
- staged loader-field-to-setter model exact and unambiguous;
- complete transformation successful;
- no problems reported.

Prepared pixels are still not live-accepted.

## Responsibilities

### Operator

The operator owns actions requiring the real installation and normal gameplay. The operator must:

- run only the command authorized for the current phase;
- retain console output, reports, and generated run directories;
- stop at every documented review boundary;
- avoid editing the game, mods, launcher, saves, or VM parameter files;
- report crashes, visual corruption, missing assets, or unusual counters exactly as observed;
- keep proprietary game archives local.

### Reviewing LLM

The reviewing LLM must:

- validate exact identities and report fields before authorizing the next phase;
- provide one copy-paste launch command using actual generated paths;
- review adapter, lifecycle, buffer-accounting, and failure telemetry after the live run;
- update evidence documents and controlling issues before benchmarks;
- keep audio and Janino reuse disabled while texture acceptance is unresolved.

### Implementation LLM

No implementation PR is currently required. Code changes are justified only when new evidence exposes a concrete mismatch, missing bound, telemetry gap, or unsafe lifecycle behavior.

Any implementation PR must state:

- exact identity boundary;
- original fallback behavior;
- resource bounds and circuit breaker;
- tests added;
- evidence or counters added;
- what remains disabled.

## Completed Phase 1 — offline installed-class check

The read-only checker passed against:

```text
/Applications/Starsector.app/Contents/Resources/Java/fs.common_obf.jar
```

The operator attempted to save the pipeline result in `status`, which is a read-only special parameter in zsh. Future shell examples must use `checker_status` or another ordinary variable.

No prepared-pixel launch occurred during Phase 1.

## Phase 2 — operator prepares the exact current profile cache

Sync the merged repository explicitly and build the current packaged JAR:

```bash
git switch main
git fetch --prune origin
git merge --ff-only origin/main

mvn --batch-mode --no-transfer-progress \
  -pl preflight-cli -am clean package
```

Use the confirmed installation path and a stable explicit cache/report location:

```bash
export STARSECTOR_HOME="/Applications/Starsector.app"
export PREFLIGHT_CACHE="$HOME/.starsector-preflight/cache"
export PREFLIGHT_PREP_REPORT="$PREFLIGHT_CACHE/reports/prepared-pixel-lifecycle.json"

mkdir -p "$PREFLIGHT_CACHE/reports"

java -jar preflight-cli/target/preflight.jar prepare \
  --game "$STARSECTOR_HOME" \
  --cache-dir "$PREFLIGHT_CACHE" \
  --report "$PREFLIGHT_PREP_REPORT"

prepare_status=$?
echo "prepare exit status: $prepare_status"
ls -lh "$PREFLIGHT_PREP_REPORT"
```

The command may take time because it inventories the profile, builds or validates resource and classpath indexes, and builds or reuses the texture cache.

### Operator stop point

Stop after preparation. Return:

- the console output;
- `prepare exit status`;
- the contents of `$PREFLIGHT_PREP_REPORT`.

Do not run `--texture-mode prepared-pixels` yet.

### Preparation review gate

The reviewing LLM must confirm:

- top-level `successful` is `true`;
- the installation and launcher are the expected current ones;
- the resource-index stage succeeded and its `valid` field is `true`;
- the texture stage succeeded and its `valid` field is `true`;
- `failedBlobs` is zero;
- top-level diagnostics contain no unresolved failure;
- the top-level `cacheDirectory`, `resourceIndex`, and texture-stage `manifest` paths are exact and internally consistent;
- the profile fingerprint represented by the index and manifest is the current profile.

A failed or stale preparation report ends this phase safely. Do not guess artifact paths.

## Phase 3 — reviewing LLM prepares the real lifecycle command

Only after Phase 2 passes, the reviewing LLM must produce one explicit command using the exact paths from the preparation report.

The command shape is:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --game "/Applications/Starsector.app" \
  --adapter \
  --texture-mode prepared-pixels \
  --texture-cache-dir "/exact/cache/path" \
  --texture-manifest "/exact/manifest/path.spfm" \
  --texture-index "/exact/resource-index/path.spfi"
```

Do not substitute guessed fingerprints or stale artifacts.

Before authorizing the launch, the reviewing LLM must state:

- expected adapter mode;
- expected exact transformation count;
- expected prepared-pixel counters;
- acceptable fallback categories;
- run-directory location to retain afterward.

## Phase 4 — operator performs one behavioral acceptance route

The operator runs the approved command and completes:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

The operator must note any visual, audio, loading, campaign, combat, save, or shutdown anomaly.

Retain locally:

- `run.json`;
- `profile.json`;
- `summary.json`;
- `adapter.json`;
- `adapter-analysis.json` when present;
- `startup.jfr`;
- console output and the exact command used.

Share sanitized JSON reports and console notes first. Keep proprietary archives local.

### Operator stop point

Stop after the one lifecycle run. Do not begin a five-run benchmark campaign until the reviewing LLM accepts the behavioral evidence.

## Phase 5 — reviewing LLM accepts or rejects the lifecycle

Acceptance requires:

- expected exact transformation count;
- prepared-pixel hits and bypassed bytes;
- only understood original-path fallbacks;
- zero internal errors and no circuit-breaker activation;
- active and peak direct-buffer ownership within bounds;
- release accounting consistent with acquired buffers, including exceptional paths;
- completed lifecycle and clean process exit;
- no reported visual, loading, campaign, combat, save, or shutdown regression.

The reviewing LLM then records sanitized evidence and updates issues #51, #78, #102, and #80 as applicable.

## Phase 6 — repeated measurement campaign

Only after behavioral acceptance passes, collect comparable runs on the same machine, installation, Java runtime, enabled mod order, save action, and cache state.

Minimum campaign:

- five successful `off-warm` runs;
- five successful `compatibility-v2` warm-hit runs;
- five successful `prepared-pixels` warm-hit runs;
- one enabled build/miss run;
- one controlled corrupt-artifact fallback or repair run;
- one changed-profile rejection run reported separately.

Retain failed runs and classify them. Report every run plus median, minimum, and maximum. Include main-menu and campaign readiness, adapter counters, remaining image/decode/conversion attribution, peak heap/direct-buffer use, and cache size.

No acceleration claim is allowed before this campaign is reviewed.

## Decision after measurement

- A meaningful prepared-pixel improvement advances one exact-profile prepare-and-launch command and an alpha texture-pilot release.
- A weak texture result redirects effort using same-run JFR evidence, with prepared audio preferred before deeper Janino context work when supported by evidence.
- A correctness failure returns to one narrow repair PR; it does not justify broader identities or weaker fallbacks.

## Standing safety rules

Do not:

- generate allowlists automatically from probe output;
- weaken exact identity gates;
- patch the Starsector installation or launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, or worker pools;
- claim acceleration from sampled CPU percentages or a single run;
- delete failed evidence runs to improve campaign results.
