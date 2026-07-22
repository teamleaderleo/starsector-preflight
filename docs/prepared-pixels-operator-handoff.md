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

PR #135 restored NPOT fail-open behavior and added bounded original-buffer layout evidence. It was squash-merged as:

```text
1fd63567e5834546ab5d617234f84371df9909ea
```

## Merged runtime behavior

- power-of-two prepared payloads remain eligible for the lower-seam bypass;
- NPOT prepared payloads do not use guessed padding;
- NPOT textures run Starsector's original decode and conversion path;
- the untouched original upload buffer is compared against a fixed candidate set;
- at most 16 logical-path observations are retained;
- no original texture bytes are retained in telemetry;
- original buffer position, limit, bytes, cleanup, upload, and exceptions remain authoritative.

Telemetry includes:

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

The exact installed converter-shape evidence also shows indexed `ByteBuffer.put(int, byte)` writes plus explicit position and limit changes. This confirms that the layout is deliberate rather than a simple append-zero convention.

## Preserved boundaries

The merged repair keeps:

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

## Authorized operator action

Exactly one **launcher-only original-layout probe** is authorized from current `main`.

Run from the repository root:

```bash
bash scripts/run-prepared-pixel-layout-probe.sh
```

The script:

1. requires merged commit `1fd63567e5834546ab5d617234f84371df9909ea`;
2. builds and verifies the checkout;
3. verifies the exact installed archive and class hashes;
4. reruns the offline contract check;
5. prepares the exact current profile;
6. prints a dry-run launch plan;
7. starts one explicit prepared-pixel launcher probe;
8. checks lifecycle, fallback, observation, and cleanup telemetry;
9. packages the complete run directory on the Desktop.

When the launcher appears:

```text
verify normal launcher visuals
→ take a screenshot
→ do not click Play
→ close with the launcher X
```

Stop after that one probe.

Environment overrides are available when needed:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-layout-probe.sh
```

## Probe acceptance requirements

The launcher-only probe must show:

- the exact expected transformation applied once;
- normal launcher background, text, buttons, toggles, and borders;
- power-of-two prepared hits may remain above zero;
- `npotProbeFallbacks` above zero when NPOT textures are encountered;
- `paddedUploads == 0` and `paddingBytes == 0`;
- at least one bounded `originalLayoutObservations` entry;
- `layoutObservationErrors == 0`;
- only understood original-path fallbacks;
- `internalErrors == 0` and no circuit breaker;
- active direct bytes, active buffers, and pending buffers equal zero at shutdown;
- fatal console/log evidence absent;
- clean launcher exit.

Upload the generated `.tar.gz` and launcher screenshot for review. Do not implement a new NPOT bypass until the observations are classified.

## Standing safety rules

Do not:

- infer a new NPOT layout from the screenshot alone;
- enable NPOT prepared bypass from an ambiguous or unclassified observation;
- run more than the single authorized launcher probe;
- click Play or enter gameplay during the probe;
- begin benchmarks;
- generate allowlists from probe output;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, observations, or worker pools;
- claim acceleration before a separate accepted measurement campaign;
- delete either failed pilot or the launcher-probe evidence.
