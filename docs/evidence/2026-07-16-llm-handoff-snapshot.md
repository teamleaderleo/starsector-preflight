# Starsector Preflight — LLM handoff

Snapshot date: 2026-07-16

This is an orientation note, not a replacement for reading the repository, open pull requests, tests, workflow results, and current branch history. Verify every SHA and PR state before changing code.

## Mission

Reduce repeated work during heavily modded Starsector startup while preserving the original game, launcher, mods, saves, and behavior whenever evidence or cache reuse is incomplete.

The project is measurement-first and fail-open. Startup-time improvement remains unmeasured. Do not claim a speedup until a real Starsector installation and representative mod profile have been benchmarked with a documented cold/warm protocol.

## Current functional baseline

The latest functional merge at this snapshot is:

`2fa650426997276f7f44954d918eb5ff940a0d2c`

That commit squash-merged PR #70, the bounded read-only audio-decoder runtime signature collector.

Immediately before it:

- `029ff8517d6b28480e3e1f65aa66af164b565d28` — PR #68, the extended corpus running through production SPAU and SPJB caches.
- `11a213511594d215529be508761754a715f596f0` — PR #69, the prior handoff and reduced CI-notification policy.

### PR #68 — merged

PR #68 was repaired against the actual merged core APIs, safety-reviewed, validated, and squash-merged as `029ff8517d6b28480e3e1f65aa66af164b565d28`.

Its proof now includes:

- production `PreparedAudioCache` SPAU reuse for generated fully decoded effects;
- generated streamed music remaining ineligible and carrying no PCM payload;
- production `GeneratedBytecodeCacheWrapper` SPJB reuse for the complete generated class map;
- final generated-class loading and execution from original and cached maps;
- bounded JDK compiler source, class-output, class-count, and diagnostic behavior;
- separate-JVM cold, warm, and independently corrupted SPAU/SPJB passes;
- exact source, provider, audio, bytecode, context-key, and combined output hashes;
- tiny profile coverage on Linux, macOS, and Windows;
- the 2,516-file medium profile on Ubuntu.

This remains a generated compatibility and work-elimination proof. It does not establish Starsector decoder equivalence, Janino compatibility, or a startup-time improvement.

### PR #60 — closed unmerged

PR #60 was audited after M6 and closed unmerged.

No unique merge-worthy implementation remained:

- its generated scales and corpus assertions already exist on `main` in a stricter v2 profile implementation;
- its loose/JAR provider indexing and work counters were superseded by the merged exact provider index;
- its cross-process proof was superseded by PR #68 using the production SPAU/SPJB formats;
- its prepared-audio and generated-bytecode classes were duplicate experimental stores and must remain unmerged;
- its generic Java Sound WAV decoder did not establish Starsector/JOrbis behavior.

The closed branch remains available only as history.

### PR #70 — merged

PR #70 was validated and squash-merged as `2fa650426997276f7f44954d918eb5ff940a0d2c`.

A normal `run --adapter-probe` now writes a separate bounded:

`adapter-audio-decoder-signatures.json`

The report observes loaded classes under:

- `com/jcraft/jogg/`
- `com/jcraft/jorbis/`
- `org/newdawn/slick/openal/`

For each retained source-and-loader identity it records exact class bytes, classfile version, bounded complete method descriptors/access flags, code source, bounded source-archive SHA-256 or failure detail, and defining-loader identity.

It remains read-only and explicitly reports:

- original class bytes retained;
- no automatic adapter generated;
- decoder equivalence not established;
- Ogg/Vorbis prepared-audio writes not eligible;
- human review required.

The focused packaged-agent matrix passed on Linux, macOS, and Windows. Full Ubuntu `mvn verify` passed.

### PR #50 and PR #55

Leave PR #50 and PR #55 untouched until real evidence justifies work there. The direct-pixel/live-adapter path requires exact real-install signatures and behavior evidence. Do not infer Starsector internals from synthetic fixtures.

## Current blocker: real-install evidence

The next substantive decoder step requires one manual, read-only launch against the exact Starsector installation and launcher intended for later testing.

Build current `main` with JDK 17 and Maven 3.9 or newer:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Then run:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --game "/path/to/Starsector" \
  --adapter-probe
```

Or use the existing launcher explicitly:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --launcher "/path/to/the/existing/launcher" \
  --adapter-probe
```

Reach the main menu, allow menu music or another known Ogg/Vorbis sound to begin playing, and exit normally.

Collect these text files from the generated run directory:

- `adapter-audio-decoder-signatures.json`
- `adapter-code-loader-signatures.json`
- `adapter.json`
- `adapter-analysis.json`
- `summary.json`
- `run.json`
- `profile.json`

`startup.jfr` is optional and may be substantially larger. Do not collect or upload Starsector JARs, game assets, mod binaries, saves, credentials, or other proprietary files. Inspect JSON for local paths or mod names before sharing when those are sensitive.

See `docs/audio-decoder-evidence.md` for the full protocol.

## Review gate after evidence collection

Do not implement a live decoder adapter merely because candidate classes appear. First establish all of the following:

1. The actual shipped decoder entry class and exact method descriptors are present.
2. Required JOrbis/Jogg/Slick dependencies, source archives, and defining loaders are identified without unexplained truncation.
3. Source archive hashes succeeded or every failure is understood and safely resolved.
4. A target-specific harness can invoke the exact shipped path without guessing constructors, stream ownership, buffer semantics, or exception behavior.
5. The exact path is compared with the committed mono and stereo Ogg fixtures for PCM bytes, encoding, byte order, sample rate, channel count, frame count, and malformed/unsupported behavior.

Only a later reviewed exact-identity gate may enable Ogg/Vorbis SPAU writes. Unknown, absent, changed, ambiguous, or partially observed identities must continue through the original decoder path.

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
- PR #68 — production SPAU/SPJB extended-corpus workload. Merge `029ff8517d6b28480e3e1f65aa66af164b565d28`.
- PR #70 — exact loaded audio-decoder runtime signature report. Merge `2fa650426997276f7f44954d918eb5ff940a0d2c`.

## Persisted formats and proof layers

- `SPXI` — synthetic prepared-image payload used by the startup laboratory.
- `SPAU` — exact prepared PCM audio payload.
- `SPAM` — deterministic prepared-audio profile manifest.
- `SPJB` — complete generated-class map for one exact compiler context and requested class.
- `SPXR` — authenticated synthetic loose/JAR provider index with embedded profile fingerprint.

Do not create another cache format when an existing production format can express the proof.

## Other future work

### Real Janino integration evidence

Review exact signatures and loader/dependency observations produced by the merged PR #58 collector. A live adapter must bind to exact implementation bytes, method descriptors, source graph, ordered classpath, compiler options, parent-loader identity, and protection-domain policy.

Do not adapt the synthetic JDK compiler seam directly to Starsector.

### AppCDS launch integration

The capability detector is merged, but launch integration must remain conservative:

- use only archives created and consumed successfully by the exact Java executable that will launch the game;
- revalidate executable identity and archive path immediately before adding flags;
- add no archive flag for unsupported, stale, moved, timed-out, or inconclusive results;
- retain a global disable switch and diagnostic reason.

### Real measurement campaign

Once a safe live path exists, define a reproducible benchmark with the exact Starsector build, launcher, Java executable, enabled mod order, profile fingerprint, hardware state, filesystem-cache policy, repeated cold/warm runs, median/tail latency, JFR, work counters, and observable-output comparison.

Treat synthetic elapsed time as test diagnostics, not product evidence.

## CI and working convention

- Draft PRs skip CI jobs.
- Marking a PR ready triggers validation.
- Superseded runs cancel automatically.
- Ordinary PR CI uses Ubuntu only.
- Pushes to `main` retain broader verification.
- Focused cross-platform workflows use path filters and seven-day artifacts.

Working convention:

1. Create a branch.
2. Open the PR as draft while iterating.
3. Run the narrowest applicable Maven tests.
4. Do not create empty or cosmetic commits merely to trigger CI.
5. Inspect retained focused logs after failures.
6. Fix the branch, then mark ready once it compiles.
7. Rerun only failed jobs when a failure is demonstrably transient.

## Useful commands

Full verification:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Focused adapter and evidence tests:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-agent,preflight-cli -am \
  -Dtest=AdapterSignatureGateTest,AdapterSourceIdentityTest,AudioDecoderSignatureReportTest,CodeLoaderSignatureReportTest,AdapterCandidateScorerTest,AgentOptionsTest,AgentInjectionTest,CommandLineAdapterTest \
  -Dit.test=AdapterAgentIT \
  -Dsurefire.failIfNoSpecifiedTests=false verify
```

Production-cache synthetic workload:

```bash
mvn --batch-mode --no-transfer-progress \
  -pl preflight-synthetic-startup -am \
  -Dtest=SyntheticJdkSourceCompilerTest,SyntheticWavePreparedAudioTest,SyntheticProductionCacheCrossProcessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Final caution

The repository has strong cache-format, synthetic-work-elimination, and exact runtime-signature foundations. The remaining difficult work is live compatibility and measurement. Prefer a smaller verified integration over a broad adapter based on guessed APIs or guessed Starsector internals.
