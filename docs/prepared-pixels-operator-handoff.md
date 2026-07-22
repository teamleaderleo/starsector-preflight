# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

Prepared pixels are **not behaviorally accepted**.

The first installed pilot crashed because a source-sized `597x373 RGB` buffer reached a `1024x512 RGB` OpenGL upload. PR #132 repaired fail-open admission, exceptional direct-buffer release, fatal child-console classification, and preparation reporting.

PR #133 then supplied a guessed expanded layout: bottom-up source rows at the lower-left, zero padding to the right and above. The next installed pilot no longer crashed and all direct-buffer accounting cleaned up, but launcher textures rendered incorrectly. That visual pilot is recorded in:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)

The visual failure disproves the guessed lower-left zero-padding layout. It does not prove upper placement or any other replacement layout.

## Retained visual-failure evidence

Run directory:

```text
prepared-pixel-pilot-20260722-143238
```

Lifecycle result:

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
```

Prepared-pixel result:

```text
transformationsApplied: 1
hits: 20
paddedUploads: 7
paddingBytes: 1002677
fallbacks: 0
internalErrors: 0
releases: 20
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
```

The launcher screenshot showed a mostly black panel with missing or incorrectly sampled background/control textures. The operator stopped before campaign gameplay.

## PR #135 repair behavior

PR #135 restores safe fail-open behavior and adds evidence collection:

- power-of-two prepared payloads remain eligible for the lower-seam bypass;
- NPOT prepared payloads do not use guessed padding;
- NPOT textures run Starsector's original decode and conversion path;
- the untouched original upload buffer is compared against a fixed candidate set;
- at most 16 logical-path observations are retained;
- no original texture bytes are retained in telemetry;
- original buffer position, limit, bytes, cleanup, upload, and exceptions remain authoritative.

Telemetry adds:

```text
npotProbeFallbacks
originalLayoutObservations
layoutObservationErrors
```

Each observation contains only:

```text
logical path
dimensions and channels
buffer position, limit, capacity, and remaining bytes
candidate layout matches
first mismatch offset per candidate
```

Candidate layouts cover row-padded and contiguous source bytes, leading or trailing unused rows, and normal or reversed source-row order. An unclassified result is valid evidence and must not be converted into another guess.

## Preserved boundaries

The repair keeps:

- exact archive, class, method, source, and loader identity gates;
- SPFT version 1 unchanged;
- original Starsector fallback behavior;
- original cleanup and exception behavior;
- the current circuit breaker;
- compatibility mode as the accepted rollback path;
- 32 MiB maximum per prepared direct buffer;
- 64 MiB maximum active prepared direct memory;
- 1,024 maximum active and pending buffers;
- no installation, launcher, mod, or save edits.

Automatic allowlist generation remains disabled. No benchmark or acceleration claim is authorized.

## Current operator status

Do not run the old padded build again.

Do not perform another full prepared-pixel gameplay lifecycle and do not benchmark.

After PR #135 is reviewed and merged, exactly one **launcher-only original-layout probe** may be authorized. Use the exact cache, manifest, index, target file, binary, and installation paths from a current successful preparation report.

The route is:

```text
launch
→ verify the launcher renders normally
→ do not start the game
→ close from the launcher
→ retain the complete run directory
```

Stop after that one probe.

## Probe acceptance requirements

The launcher-only probe must show:

- the exact expected transformation applied once;
- normal launcher background, text, buttons, toggles, and borders;
- power-of-two prepared hits may remain above zero;
- `npotProbeFallbacks` above zero when NPOT textures are encountered;
- `paddedUploads == 0` and `paddingBytes == 0`;
- at least one bounded `originalLayoutObservations` entry, unless no NPOT texture is encountered;
- `layoutObservationErrors == 0`;
- only understood original-path fallbacks;
- `internalErrors == 0` and no circuit breaker;
- active direct bytes, active buffers, and pending buffers equal zero at shutdown;
- fatal console/log evidence absent;
- clean launcher exit.

Retain:

- `run.json`;
- `profile.json`;
- `summary.json`;
- `adapter.json`;
- `adapter-analysis.json` when present;
- `startup.jfr`;
- `console.txt`;
- the exact command and binary SHA-256;
- a screenshot of the normal launcher.

## Standing safety rules

Do not:

- infer a new NPOT layout from the screenshot alone;
- enable NPOT prepared bypass from an ambiguous or unclassified observation;
- run more than the single authorized launcher probe;
- begin benchmarks;
- generate allowlists from probe output;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, observations, or worker pools;
- claim acceleration before a separate accepted measurement campaign;
- delete either failed pilot or the next probe evidence.
