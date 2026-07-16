# Starsector Preflight — LLM handoff

Snapshot date: 2026-07-16

This is an orientation note, not a replacement for reading the repository, open pull requests, test reports, and current branch history. Verify every SHA and PR state before changing code.

## Mission

Reduce repeated work during heavily modded Starsector startup while preserving the original game, launcher, mods, saves, and behavior whenever evidence or cache reuse is incomplete.

The project is measurement-first and fail-open. Startup-time improvement remains unmeasured. Do not claim a speedup until a real Starsector installation and representative mod profile have been benchmarked with a documented cold/warm protocol.

## Current repository state

Main was at:

`8f857e88be6e32bfb818632473097b5afeb6c38f`

That commit merged PR #67, the bounded extended synthetic provider-index extraction.

### Open PR #68

Title: `M6: run extended corpus through production SPAU and SPJB caches`

Head:

`8e0928c773af83e38528c34589c197638d0589f1`

PR #68 is unfinished and should remain draft until it compiles. The latest observed run failed before tests because `SyntheticProductionCacheWorker` was written against APIs that do not exist in `preflight-core`.

The CI artifact reports 14 compilation errors. The central repair is to use the actual static APIs already merged:

- `PreparedAudioCache` has no instance constructor.
- Use `PreparedAudioCache.lookup(cacheRoot, sourceSha256, decoderPolicySha256, policy)`.
- Use `PreparedAudioCache.write(cacheRoot, audio)`.
- Its result type is `PreparedAudioCache.Lookup` and its enum is `PreparedAudioCache.Status`.
- `GeneratedBytecodeCache` has no instance constructor.
- `GeneratedBytecodeContext` is constructed directly as a record; there is no `builder()`.
- Its identity method is `keySha256()`, not `cacheKeySha256()`.
- Use `GeneratedBytecodeCacheWrapper.generate(cacheRoot, context, requestedClassName, generator)`.
- The wrapper returns `GeneratedBytecodeCacheWrapper.Result`.
- The wrapper exposes `source()`, `classes()`, `lookupStatus()`, `cacheUsable()`, and `detail()`.
- There is no wrapper `Outcome`, `SourceDisposition`, or `execute(...)` API.
- `PreparedAudio` exposes `encoding()` and `sampleRateHz()`, not `pcmEncoding()` and `sampleRate()`.

Repair all compile errors before rerunning CI. Do not weaken cold/warm/corrupt assertions to get the PR green.

### Open PR #60

PR #60 contains useful historical work, but it also contains duplicate experimental audio, generated-bytecode, and generic prepared-store implementations. Its useful profile and provider-index portions were extracted and merged safely in PR #67.

Keep PR #60 draft and unmerged. Known problems in its duplicate store design include:

1. logical paths participating in blob identity, defeating content deduplication;
2. insufficient embedded identity checks, allowing valid payload substitution across exact keys; and
3. allocation before complete serialized-size or memory-limit rejection.

Close or replace PR #60 only after confirming no remaining unique test corpus or JAR-layer work is worth extracting.

### PR #50 and PR #55

Leave PR #50 and PR #55 untouched until real evidence justifies work there. In particular, the direct-pixel/live-adapter path requires exact real-install signatures and behavior evidence. Do not infer Starsector internals from synthetic fixtures.

## Non-negotiable engineering rules

1. **Fail open to original behavior.** Cache miss, corruption, read failure, validation failure, and write failure must preserve the original source path or original generator result.
2. **Exact identity.** Reuse keys must include every input capable of changing output. Persisted payloads must embed and authenticate the same identities used by their paths.
3. **Reject substitution.** Copying a valid cache object under another exact key must be detected.
4. **Bound everything.** Bound files, paths, strings, entries, payloads, decoded bytes, compiler diagnostics, process output, and child-JVM time.
5. **No path escapes.** Normalize paths, reject symbolic links where applicable, verify real paths stay under approved roots, and distinguish missing files from unsafe non-file targets.
6. **Atomic persistence.** Write a temporary sibling, then replace atomically when supported; clean up on failure.
7. **Separate-JVM proof.** Important reuse paths need cold, warm, and corrupt-cache runs in fresh JVMs. Warm runs should prove source work was skipped with counters, not elapsed-time assumptions.
8. **Preserve Java semantics.** Wrappers must preserve original map identity where promised and preserve original checked exception type and object identity.
9. **No unsupported compatibility claims.** Synthetic JDK compilation is not Janino compatibility. FFmpeg/libvorbis PCM is not evidence of Starsector decoder equivalence.
10. **No startup-speed claim yet.** Report work elimination and exact output equivalence only.

## Merged foundation

Recent merged work, in dependency order:

- PR #56 — exact generated-bytecode bundle class names. Merge `90abdde204a4099d5e3a4a4c02be213643dec57e`.
- PR #57 — corrected JFR ClassDefine metadata assumptions. Merge `727981782db8337c6c8aee209fd10dfda28657ba`.
- PR #58 — bounded Janino loader-signature reporting and typed identity key. Merge `b3c023062ed34e481b085f8598d6173ae9208db0`.
- PR #61 — synthetic startup laboratory v1 and prepared-image reuse proof. Merge `79c3d45b2ff08705d20cee42ac5f41c0d29e33f7`.
- PR #62 — production prepared-audio cache and manifest substrate. Merge `8fd275bbd98efd9ad3a6e01741c340facf8e4106`.
- PR #63 — generic complete-map generated-bytecode cache wrapper. Merge `0be1688c563fec489cf7a2b42cfff99cd5a6e96c`.
- PR #64 — separate-JVM production prepared-audio consumption proof. Merge `b85d4b373bf796a0f6632ea17b292d15b655c31f`.
- PR #65 — exact-JVM AppCDS capability detector. Merge `a8c12377a87caf6f9dfdf8ec9786a88b27571650`.
- PR #66 — bounded Ogg/Vorbis identification and PCM golden-fixture gate. Merge `70a51bc92cc41da025bd06f020f641d72430dabb`.
- PR #67 — deterministic tiny/medium/large corpus and authenticated loose/JAR provider index. Merge `8f857e88be6e32bfb818632473097b5afeb6c38f`.

## Persisted formats and proof layers

- `SPXI` — synthetic prepared-image payload used by the startup laboratory.
- `SPAU` — exact prepared PCM audio payload.
- `SPAM` — deterministic prepared-audio profile manifest.
- `SPJB` — complete generated-class map for one exact compiler context and requested class.
- `SPXR` — authenticated synthetic loose/JAR provider index with embedded profile fingerprint.

Do not create another cache format when an existing production format can express the proof.

## Suggested next work

### 1. Repair PR #68 against actual APIs

First make the branch compile without adding compatibility shims to core merely to accommodate the incorrect worker API.

Then preserve the intended proof:

- generated effects use production SPAU blobs;
- generated music remains `STREAMED` and ineligible;
- all generated Java sources compile into one complete SPJB map;
- the final generated class is loaded and executed from original and cached maps;
- cold run performs source work;
- warm run performs zero eligible WAV decodes and zero Java compiler calls;
- one corrupted SPAU and one corrupted SPJB each trigger exactly one source fallback;
- source, provider, PCM, class-map, context, and combined hashes agree across runs;
- tiny runs cover Linux, macOS, and Windows;
- the 2,516-file medium profile runs on Ubuntu.

### 2. Real decoder-equivalence evidence

The Ogg fixtures are a reference oracle only. A Starsector/JOrbis-facing adapter remains blocked until the exact shipped decoder implementation and dependency behavior are known.

When real evidence exists:

- hash exact decoder implementation bytes and defining loader identity;
- decode the committed mono/stereo fixtures through that exact path;
- compare PCM bytes, encoding, byte order, sample rate, channel count, frame count, and malformed/unsupported behavior;
- only enable SPAU writes after exact or explicitly reviewed equivalence is established.

### 3. Real Janino integration evidence

Review the exact signatures and loader/dependency observations produced by the PR #58 work. A live adapter must bind to exact implementation bytes, method descriptors, source graph, ordered classpath, compiler options, parent-loader identity, and protection-domain policy.

Do not adapt the synthetic JDK compiler seam directly to Starsector.

### 4. AppCDS launch integration

The capability detector is merged, but launch integration should remain conservative:

- use only archives created and consumed successfully by the exact Java executable that will launch the game;
- revalidate executable identity and archive path immediately before adding flags;
- add no archive flag for unsupported, stale, moved, timed-out, or inconclusive results;
- retain a global disable switch and diagnostic reason.

### 5. Real measurement campaign

Once a safe live path exists, define a reproducible benchmark:

- exact Starsector build and launcher;
- exact Java executable;
- exact enabled mod order and profile fingerprint;
- fixed hardware and power state;
- documented filesystem-cache policy;
- multiple cold and warm repetitions;
- median and tail latency;
- JFR and work counters retained;
- comparison of outputs and observable behavior.

Treat synthetic elapsed time as test diagnostics, not product evidence.

### 6. Repository cleanup

After PR #68 is resolved:

- close or supersede PR #60 with a comment pointing to PRs #61, #62, #63, #64, #67, and #68;
- remove stale experimental branches only after confirming nothing unique remains;
- keep PR #50 and #55 unchanged until evidence arrives.

## CI and notification policy

The repository is being changed to reduce notification volume:

- draft PRs skip CI jobs;
- marking a PR ready for review triggers validation;
- superseded runs cancel automatically;
- ordinary PR CI uses Ubuntu only;
- pushes to `main` retain Linux/macOS/Windows full verification;
- focused cross-platform workflows use path filters and run only for their subsystem;
- focused log artifacts expire after seven days.

Working convention for future agents:

1. Create a branch.
2. Open the PR as **draft** while iterating.
3. Run the narrowest applicable Maven tests before pushing broad changes.
4. Do not create empty or cosmetic commits just to trigger CI.
5. Inspect retained focused logs after a failure.
6. Fix the branch, then mark the PR ready once it compiles.
7. Rerun only failed jobs when the failure is demonstrably transient.

## Useful commands

Full local verification:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Prepared audio:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-core,preflight-synthetic-startup -am \
  -Dtest=PreparedAudioCacheTest,SyntheticPreparedAudioCrossProcessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Generated bytecode:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-core,preflight-synthetic-startup -am \
  -Dtest=GeneratedBytecodeCacheWrapperTest,GeneratedBytecodeCacheSafetyTest,SyntheticGeneratedBytecodeCrossProcessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Extended provider index:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-synthetic-startup -am \
  -Dtest=SyntheticExtendedProfileTest,SyntheticExtendedResourceIndexTest,SyntheticExtendedIndexCrossProcessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Medium extended profile:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-synthetic-startup -am \
  -Dpreflight.synthetic.medium=true \
  -Dtest=SyntheticExtendedMediumProfileTest,SyntheticExtendedIndexCrossProcessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Final caution

The repository now has strong cache-format and synthetic-work-elimination foundations. The remaining difficult work is live compatibility and measurement. Prefer a smaller verified integration over a broad adapter based on guessed APIs or guessed Starsector internals.
