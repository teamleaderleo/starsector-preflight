# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel handoff documents. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Move Starsector Preflight from extensive exact-gated implementation and synthetic proof to one defensible real-world result.

The next milestone is not another subsystem. It is:

1. prepare the exact current profile cache;
2. run one real prepared-pixel lifecycle acceptance route;
3. run repeated OFF, compatibility-v2, and prepared-pixel measurement;
4. make a documented continue, repair, or redirect decision.

Use [Prepared-pixel acceptance: operator and LLM handoff](prepared-pixels-operator-handoff.md) as the phase-by-phase protocol.

## State as of 2026-07-22

### Texture path

- `texture-compatibility-v2` passed bounded real-install behavioral acceptance on 2026-07-19.
- The accepted run applied one exact transformation, served 4,926 prepared decoded-image hits, used the original path three times, reached the main screen, and exited normally.
- That result is behavioral acceptance only, not repeated timing evidence.
- The lower `prepared-pixels` consumer is implemented with fail-open fallback and bounded direct-buffer ownership.
- PR #117 added the installed-style staged color model: three non-static `TextureLoader` color fields must each flow into one distinct, exactly typed texture-object setter. Direct texture-object color fields remain supported.
- PR #119 added `PreparedPixelContractCheck` for an extracted class or the containing JAR.
- The exact reviewed installation passed that offline checker on 2026-07-22. See [the retained contract evidence](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md).
- The pass included exact archive and class identities, all nine required methods, exact staged setter flow, successful transformation, and no problems.
- Prepared pixels remain opt-in and not live-accepted until one bounded gameplay lifecycle passes.

### Measurement path

- Comparison-ready run collection and identity validation are implemented.
- Campaign comparison preserves failed runs and reports every run.
- OFF-warm versus ENABLED-warm-hit median deltas and improvement percentages are implemented.
- The campaign threshold is five successful runs per compared mode.
- The real repeated campaign has not been run.

### Audio path

- Exact Jogg/JOrbis identities and an installed-decoder equivalence command exist.
- A bounded Starsector sound-wrapper observation command exists and uses the installation's bundled Java when required.
- Prepared-audio writes, cache reads, and live transforms remain disabled.
- Do not start live audio reuse until texture acceptance is resolved and the real installed audio reports are reviewed.

### Janino path

- Exact installed Janino, commons-compiler, class, archive, and complete-map seam identities are captured.
- The evidence-aware wrapper requires complete ordered context evidence before any SPJB lookup or write.
- Incomplete evidence executes the untouched original generator exactly once.
- Live Janino reuse remains disabled pending real provider behavior and cold/warm/corrupt/duplicate/inner-class/error proofs.

### CI and infrastructure

- Ordinary hosted CI is Linux-first, with scheduled/manual multi-platform coverage.
- A self-hosted Linux VPS verification workflow is available for owner-triggered trusted refs.
- Rootless Podman uses delegated CPU, memory, and PID controllers and selects `cgroupfs` for containers launched by the runner system service.
- A full Maven verification completes successfully on the 1 GiB Linode without active swap thrashing or measured host pressure.
- PR #126 fixed a macOS-only test assertion that compared unresolved `/var` paths against canonical `/private/var` paths.
- Do not spend the next session optimizing CI or adding another runner.

## Immediate ordering

The exact order is now:

1. operator runs only the exact-profile `prepare` command from the operator handoff;
2. operator returns the preparation report and stops;
3. reviewing LLM validates the report and extracts exact cache, resource-index, and manifest paths;
4. reviewing LLM supplies one explicit prepared-pixel launch command;
5. operator performs one bounded main-menu, campaign, first-combat, save, and clean-exit route;
6. reviewing LLM records and accepts or rejects behavioral evidence;
7. repeated measurement begins only after acceptance;
8. choose release/productization, a narrow repair, or the next measured domain.

Do not skip directly to repeated benchmarks. Do not guess manifest or index paths.

## Division of responsibility

### Repository/implementation LLM

The implementation LLM owns:

- repository state reconciliation;
- code review and narrow repairs;
- test and CI verification;
- exact operator command generation based on real paths and artifact names;
- evidence-document and issue updates;
- keeping unsupported targets disabled.

No implementation PR is currently required. Open one only if new evidence exposes a specific defect or missing gate.

### Operator

The operator owns actions requiring the real installation:

- exact-profile cache preparation;
- approved gameplay lifecycle routes;
- retaining run directories, JFR, JSON reports, and console notes;
- reporting visual or behavioral regressions.

The operator must stop at each review boundary. Raw proprietary game archives remain local.

### Reviewing LLM

The reviewing LLM must verify reports before authorizing the next phase. A process exit of zero is not sufficient by itself. Review exact identities, stage success, validation fields, transformation status, fallbacks, direct-buffer accounting, lifecycle outcome, and bounded error telemetry.

## First actions for the next session

1. Read:
   - `README.md`;
   - `docs/prepared-pixels-operator-handoff.md`;
   - `docs/evidence/2026-07-22-prepared-pixel-installed-contract-pass.md`;
   - `docs/optimization-north-star.md`;
   - `docs/architecture.md`;
   - `docs/benchmarking.md`;
   - `docs/vanilla-adapter.md`;
   - `docs/prepared-textures.md`;
   - issues #48, #51, #75, #77, #78, #80, and #102.
2. Confirm `main` contains PRs #117–#126 and the latest evidence-document PR.
3. Determine the current phase from the operator handoff.
4. Do not open an implementation PR unless the evidence exposes a concrete defect.

## Current operator action

The operator may run Phase 2 from `docs/prepared-pixels-operator-handoff.md`:

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

The operator stops after preparation and returns the console output plus the report contents. Do not authorize a prepared-pixel launch until the preparation report passes review and exact artifact paths are extracted.

## Reviewed identities

### TextureLoader

```text
class:       com/fs/graphics/TextureLoader
class SHA:   d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
archive SHA: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
archive:     fs.common_obf.jar
loader:      jdk/internal/loader/ClassLoaders$AppClassLoader
loader name: app
```

Offline prepared-pixel transform SHA-256:

```text
b32700195f5837c42dba0f2d202cc0a95af75bfd8b7725d8a2878f59d9e01527
```

### Janino complete-map target

```text
class:       org/codehaus/janino/JavaSourceClassLoader
class SHA:   6b0eea7994ab4c314f1bc7cdefaa99b66897d500c2cad6fd2d97cd08b134c4b8
archive SHA: 60f05562c22b6de06641a1f76148692ef336ad1f6712fe6a76f9e2611f766344
commons-compiler archive SHA: 69094456b227ec07d908938c8f90eb57e51ca6d0e82f96475770af7224b508b2
loader:      jdk/internal/loader/ClassLoaders$AppClassLoader
loader name: app
```

Preferred seam:

```text
generateBytecodes(Ljava/lang/String;)Ljava/util/Map;
```

### Audio decoder and wrapper

```text
jogg-0.0.7.jar:
ed7946260897d97c468a4749b3d9d5e436a268fa948bc32e75a7487130e89379

jorbis-0.0.15.jar:
d049b2a1c6ddefde3a5cbff320c96fdd5aefa09b0d3bbea3fe44839f7e6713f9

primary wrapper seam:
sound/J.o00000(Ljava/io/InputStream;)Lsound/F;
```

## Decision tree after texture evidence

### Prepared pixels pass and materially improve startup

- publish a sanitized repeated-campaign evidence report;
- advance issue #76 toward one exact-profile prepare-and-launch command;
- prepare an alpha texture-pilot release;
- keep compatibility mode as an independent rollback path.

### Prepared pixels pass behaviorally but show little gain

- use same-run JFR evidence to choose the next domain;
- prefer audio before deeper Janino work when audio remains dominant and its equivalence gates pass;
- do not continue texture optimization based only on sunk cost.

### Prepared pixels fail the lifecycle

- open one narrow repair PR tied to the exact failure;
- preserve the original path and current exact gates;
- add a fixture reproducing the mismatch;
- repeat only the failed phase after review.

## Prohibited shortcuts

Do not:

- generate allowlists automatically from probe output;
- weaken exact identity gates;
- patch the Starsector installation or launcher;
- swallow original exceptions on fallback;
- return partial Janino class maps;
- key Janino only by source size or modification time;
- cache streaming music as fully decoded PCM without policy proof;
- perform OpenGL work on background threads;
- add unbounded maps, logs, artifacts, buffers, or worker pools;
- claim acceleration from sampled CPU percentages or a single run;
- delete failed campaign runs.

## Required verification before merge

For code changes, run:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Run relevant focused platform workflows. Review the packaged JAR to ensure ASM remains relocated and no unshaded `org/objectweb/asm/*` classes leak into the artifact.

Every implementation PR must state:

- exact identity boundary;
- original fallback behavior;
- bounds and circuit breaker;
- tests added;
- reports or counters added;
- what remains disabled;
- whether any real A/B claim is supported.

## Definition of a good handback

At the end of a session, leave:

1. a narrow merged or review-ready PR when code changes were required;
2. all relevant CI results checked;
3. a comment on the controlling issue with commit SHA, evidence state, and next blocker;
4. no speculative target enabled by default;
5. one exact next operator or implementation action.
