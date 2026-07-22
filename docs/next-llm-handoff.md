# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Perform and review exactly one real installed **launcher-only original-layout probe** using current `main` and the repository runner.

Do not run another prepared-pixel gameplay lifecycle, begin repeated measurement, or make acceleration claims.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- issue #129 — NPOT upload dimensions and visual layout acceptance

## State as of 2026-07-22

Merged repair baseline:

```text
PR #132: 4f3b79c6d7683242d16cb7b34081cd7800f20017
```

Merged guessed-padding implementation:

```text
PR #133: 68ece81782b54022d58d41634dd88491fca13601
```

Merged NPOT fail-open and original-layout probe repair:

```text
PR #135: 1fd63567e5834546ab5d617234f84371df9909ea
```

The post-padding installed pilot reached the launcher and exited cleanly, but launcher textures rendered incorrectly. Retained telemetry showed:

```text
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

This isolates the failure to the guessed NPOT byte arrangement. It does not prove upper placement or another substitute layout.

The retained installed texture-loader contract shows the original converter creates a ByteBuffer, performs indexed `ByteBuffer.put(int, byte)` writes, and explicitly sets buffer position and limit. The layout is deliberate and should be observed rather than replaced with another append/placement guess.

## Merged runtime behavior

Current `main`:

1. keeps the exact prepared-pixel transformation and power-of-two bypass;
2. returns NPOT carriers to Starsector's original decode/conversion path before direct allocation;
3. observes the original buffer after conversion without changing it;
4. compares it with a fixed candidate set;
5. retains at most 16 deduplicated logical-path observations;
6. records no original texture payload bytes;
7. preserves original upload, cleanup, and exception behavior.

New telemetry:

```text
npotProbeFallbacks
originalLayoutObservations
layoutObservationErrors
```

For schema continuity during the probe:

```text
paddedUploads: 0
paddingBytes: 0
```

Power-of-two hits still use the bounded direct-buffer path and retain the existing ownership telemetry.

## Automated validation

Validated implementation and readiness head before documentation-only alignment:

```text
6ad76b6964c91649d71bcd7e8b944cd4fe49ff65
```

Successful workflows:

```text
CI run 516 — full Maven verification
Vanilla adapter gate tests run 368
Texture cache tests run 363
Prepare command tests run 93
```

PR #135 was squash-merged after documentation-only evidence alignment.

## Exact identities

```text
TextureLoader class:
com/fs/graphics/TextureLoader

class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50

archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
```

Keep these identities exact. Automatic allowlist generation remains disabled.

## Authorized operator action

From a checkout containing PR #135 and the runner follow-up, use:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-layout-probe.sh
```

The runner verifies the merged commit, build, exact installed identities, offline contract, current preparation artifacts, lifecycle result, NPOT fallback telemetry, layout observations, and shutdown accounting.

When the launcher appears:

```text
inspect normal launcher visuals
→ take a screenshot
→ do not click Play
→ close with the launcher X
```

The runner packages the complete run directory on the Desktop. Upload that archive and the screenshot.

Expected evidence:

- normal launcher visuals;
- exact transformation applied;
- NPOT original-path fallbacks above zero;
- guessed padded uploads equal zero;
- bounded original-layout observations present;
- observation errors zero;
- active prepared buffers/direct bytes/pending buffers zero at shutdown;
- no fatal console or log evidence;
- clean launcher exit.

Stop after the one launcher probe. Do not implement a new NPOT bypass until the retained original-layout observations are reviewed.

## Definition of a good handback

Leave:

1. the exact command, repository head, and merged JAR SHA-256;
2. the complete retained launcher-probe directory and screenshot;
3. a dated evidence document classifying each observed layout result;
4. issue #129 updated with pass or failure details;
5. readiness and operator handoff aligned with the result;
6. no gameplay lifecycle, repeated benchmark, or acceleration claim.
