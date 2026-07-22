# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

This document defines who acts next and where each person must stop. The goal is to complete exact installed-class validation, one real prepared-pixel lifecycle acceptance run, and only then a repeated startup benchmark campaign.

## Current decision

Do not add another optimization subsystem yet. Do not optimize CI further. Do not run a prepared-pixel launch until the offline installed-class report has been reviewed.

The immediate sequence is:

1. repository and documentation cleanup;
2. operator runs the offline installed-class contract check;
3. an LLM reviews that report;
4. operator performs one approved real lifecycle run;
5. an LLM reviews the retained evidence;
6. operator runs the repeated benchmark campaign only after behavioral acceptance passes.

## Current project state

The repository already contains:

- the exact-gated `texture-compatibility-v2` consumer with one bounded real-install behavioral acceptance run;
- the lower `prepared-pixels` consumer;
- support for both direct texture-object color fields and the installed-style staged `TextureLoader` color flow into three distinct texture-object setters;
- `PreparedPixelContractCheck` for an extracted class or the containing JAR;
- bounded direct-buffer ownership and fail-open fallback behavior;
- benchmark collection and comparison tooling for repeated OFF-versus-ENABLED campaigns.

Prepared pixels are not yet live-accepted. The remaining gate is evidence from the exact installed Starsector class followed by one successful opt-in lifecycle route.

## Responsibilities

### Current cleanup session

The current repository-maintenance session must:

1. reconcile this handoff, the roadmap, and prepared-texture documentation with merged PRs #117–#123;
2. remove instructions that still tell the next session to reimplement the prepared-pixel color model or CI runner;
3. leave one exact operator command for the offline contract check;
4. avoid enabling a new live target or changing the exact allowlist;
5. merge a documentation-only PR and comment on the controlling issue with the next operator action.

The operator should wait until that cleanup is merged.

### Operator

The operator owns actions that require the real Starsector installation and normal gameplay.

The operator must:

- run only the command authorized for the current phase;
- retain console output and generated run directories;
- stop immediately at the documented review boundary;
- avoid editing the game, mods, launcher, saves, or VM parameter files;
- report crashes, visual corruption, missing assets, or unusual fallback/error counters exactly as observed;
- keep raw proprietary game archives local.

### Reviewing LLM

The reviewing LLM must:

- validate exact identities and bounded report fields before allowing the next phase;
- never infer acceptance from a successful process exit alone;
- provide one copy-paste operator command using the operator's actual paths and generated artifact names;
- review adapter, lifecycle, buffer-accounting, and failure telemetry after the real run;
- update evidence documents and controlling issues before starting benchmarks;
- keep audio and Janino reuse disabled while texture acceptance is unresolved.

### Next implementation LLM

No implementation PR is automatically required after the offline check.

The next implementation LLM writes code only when the installed report exposes a concrete mismatch, missing bound, telemetry gap, or unsafe lifecycle behavior. Any such PR must remain narrow and must state:

- exact identity boundary;
- original fallback behavior;
- resource bounds and circuit breaker;
- tests added;
- evidence or counters added;
- what remains disabled.

## Phase 1 — operator runs the offline installed-class check

After the cleanup PR is merged, sync and build the current packaged JAR:

```bash
git switch main
git pull --ff-only
mvn --batch-mode --no-transfer-progress -pl preflight-cli -am package
```

Set the real installation path. On the reviewed macOS installation, the core archive is below `Contents/Resources/Java`:

```bash
export STARSECTOR_HOME="/path/to/Starsector.app"
export PREFLIGHT_CORE_JAR="$STARSECTOR_HOME/Contents/Resources/Java/fs.common_obf.jar"
```

Run the read-only contract checker and retain its text report:

```bash
java -cp preflight-cli/target/preflight.jar \
  dev.starsector.preflight.agent.PreparedPixelContractCheck \
  "$PREFLIGHT_CORE_JAR" \
  | tee prepared-pixel-contract.txt
```

This command must not launch Starsector or alter the installation.

### Operator stop point

Stop after this command. Send `prepared-pixel-contract.txt` to the reviewing LLM. Do not run `--texture-mode prepared-pixels` yet.

### Review gate

The reviewing LLM must confirm all of the following from the report:

- input and class identities match the manually reviewed installation;
- the expected `TextureLoader` class and all nine required methods are present;
- the installed staged color-sink model is recognized without ambiguity;
- the complete prepared-pixel transform succeeds;
- decline reasons are absent;
- output is bounded and contains no proprietary class bytes.

A mismatch, ambiguity, incomplete transform, or unexpected identity ends the phase as a safe decline. It does not justify weakening the target gate.

## Phase 2 — reviewing LLM prepares the real lifecycle command

Only after Phase 1 passes, the reviewing LLM must:

1. verify or build the exact current profile cache;
2. obtain the actual generated manifest and index paths from the preparation result;
3. produce one explicit `run` command using those exact paths;
4. state the expected adapter mode and counters before the operator starts;
5. identify the run-directory location to retain afterward.

The lower consumer requires explicit artifacts. The command shape is:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --game "$STARSECTOR_HOME" \
  --adapter \
  --texture-mode prepared-pixels \
  --texture-cache-dir "/exact/cache/path" \
  --texture-manifest "/exact/cache/path/manifests/<fingerprint>.spfm" \
  --texture-index "/exact/cache/path/indexes/<fingerprint>.spfi"
```

Do not substitute guessed fingerprints or stale artifacts.

## Phase 3 — operator performs one behavioral acceptance route

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

Share sanitized JSON reports and console notes first. Keep proprietary archives and unrelated private paths local unless a reviewer specifically needs a bounded redacted field.

### Operator stop point

Stop after the one lifecycle run. Do not begin a five-run benchmark campaign until the reviewing LLM accepts the behavioral evidence.

## Phase 4 — reviewing LLM accepts or rejects the lifecycle

Acceptance requires:

- expected exact transformation count;
- prepared-pixel hits and bypassed bytes;
- only understood original-path fallbacks;
- zero internal errors and no circuit-breaker activation;
- active and peak direct-buffer ownership within bounds;
- release accounting consistent with acquired buffers, including exceptional paths;
- completed lifecycle and clean process exit;
- no reported visual, loading, campaign, combat, save, or shutdown regression.

The reviewing LLM then records a sanitized evidence document and updates issues #51, #78, #102, and #80 as applicable.

## Phase 5 — repeated measurement campaign

Only after behavioral acceptance passes, collect comparable runs on the same machine, installation, Java runtime, enabled mod order, save action, and cache state.

Minimum campaign:

- five successful `off-warm` runs;
- five successful `compatibility-v2` warm-hit runs;
- five successful `prepared-pixels` warm-hit runs;
- one enabled build/miss run;
- one controlled corrupt-artifact fallback or repair run;
- one changed-profile rejection run reported separately.

Retain failed runs and classify them. Report median, minimum, and maximum, along with main-menu and campaign readiness, adapter counters, remaining image/decode/conversion attribution, peak heap/direct-buffer use, and cache size.

No acceleration claim is allowed before this campaign is reviewed.

## Decision after measurement

- A meaningful prepared-pixel improvement moves the project toward one exact-profile prepare-and-launch command and an alpha texture-pilot release.
- A weak texture result should redirect effort using the same-run JFR evidence, with prepared audio preferred before deeper Janino context work when the evidence supports it.
- A correctness failure returns to a narrow repair PR; it does not justify broadening target identities or weakening fallback rules.

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
