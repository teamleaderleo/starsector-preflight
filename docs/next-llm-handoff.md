# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Review and merge PR #135, then perform and review exactly one real installed **launcher-only original-layout probe**.

Do not run another prepared-pixel gameplay lifecycle, begin repeated measurement, or make acceleration claims.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- issue #129 — NPOT upload dimensions and visual layout acceptance
- PR #135 — NPOT fail-open restoration and original-layout evidence

## State as of 2026-07-22

Merged repair baseline:

```text
PR #132: 4f3b79c6d7683242d16cb7b34081cd7800f20017
```

Merged guessed-padding implementation:

```text
PR #133: 68ece81782b54022d58d41634dd88491fca13601
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

## PR #135 behavior

PR #135:

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

Validated implementation and readiness head:

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

Commits after that head, when present, are documentation-only alignment and must not be represented as additional implementation validation.

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

## Review checklist

1. Confirm NPOT `prepare()` returns `null` before direct allocation.
2. Confirm power-of-two prepared hits remain unchanged.
3. Confirm fallback invokes original decode and conversion once.
4. Confirm the returned original buffer is the exact buffer sent onward.
5. Confirm observation uses a duplicate and does not change position, limit, capacity, bytes, cleanup, or exceptions.
6. Confirm observations and retained fields are bounded and contain no pixel payload.
7. Confirm failed or ambiguous classification never enables a layout.
8. Confirm exact identity gates, cache format, circuit breaker, and memory limits remain unchanged.
9. Confirm compatibility mode remains the accepted rollback.
10. Confirm no benchmark or acceleration claim.

## Operator action after merge

Use a newly built merged JAR and exact artifacts from a current successful preparation report.

Perform one route only:

```text
launch in prepared-pixels mode
→ inspect normal launcher visuals
→ do not start Starsector
→ close from the launcher
→ retain the complete run directory and a screenshot
```

Expected evidence:

- normal launcher visuals;
- exact transformation applied;
- NPOT original-path fallbacks above zero;
- guessed padded uploads equal zero;
- bounded original-layout observations present;
- observation errors zero;
- active prepared buffers/direct bytes/pending buffers zero at shutdown;
- no fatal console or log evidence;
- clean exit.

Stop after the one launcher probe. Do not implement a new NPOT bypass until the retained original-layout observations are reviewed.

## Definition of a good handback

Leave:

1. PR #135 merged or exact review findings recorded;
2. final workflow results and validated commit SHA;
3. the exact probe command and merged JAR SHA-256 when a probe is authorized;
4. the complete retained launcher-probe directory and screenshot;
5. a dated evidence document classifying each observed layout result;
6. issue #129 updated with pass or failure details;
7. readiness and operator handoff aligned with the result;
8. no gameplay lifecycle, repeated benchmark, or acceleration claim.
