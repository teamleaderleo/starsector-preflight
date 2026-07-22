# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Review and merge PR #133, which adds bounded next-power-of-two upload padding to the prepared-pixel bridge after the repaired baseline from PR #132.

After merge and explicit review approval, perform exactly one real installed lifecycle route. Repeated measurement and acceleration claims remain blocked until that evidence is accepted.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [NPOT upload padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- issue #129 — NPOT upload dimensions and exceptional-path direct-buffer accounting
- issue #130 — fatal console evidence missed when the launcher exits zero
- issue #128 — silent prepare progress and stale readiness fields

## State as of 2026-07-22

PR #132 was squash-merged as:

```text
4f3b79c6d7683242d16cb7b34081cd7800f20017
```

PR #133 is review-ready on branch `feature/prepared-pixel-npot-padding`.

Validated implementation head:

```text
d97f362b56a669c7faeaacc477d8e652cacb93c3
```

Successful workflows on that implementation head:

- CI run 494 — `mvn --batch-mode --no-transfer-progress verify`
- Texture cache tests run 343
- Vanilla adapter gate tests run 346
- Prepare command tests run 78

Documentation-only commits follow that validated implementation head.

## Implemented NPOT behavior

SPFT version 1 remains source-sized and unchanged on disk. Its RGB/RGBA rows are already in bottom-up upload order.

At the prepared-pixel bridge, the runtime now:

1. calculates the next power of two for width and height;
2. allocates a direct buffer for `uploadWidth * uploadHeight * channels`;
3. copies each source row unchanged;
4. zero-fills the unused right side of each row;
5. zero-fills unused rows above the source;
6. retains source dimensions on the carrier.

The source occupies the lower-left of the backing texture. No resampling or visual upscaling occurs.

Observed fixture:

```text
597 * 373 * 3 = 668043 source bytes
1024 * 512 * 3 = 1572864 upload bytes
904821 bytes of zero padding
```

The packaged Java-agent fixture now enforces the same power-of-two minimum-buffer requirement that caused the first live failure. The prepared route passes with decode and conversion bypassed, cleanup executed once, and expanded direct-buffer accounting released to zero.

## Preserved boundaries

PR #133 preserves:

- exact installed archive, class, source, method, and loader identities;
- the original Starsector fallback path;
- the current circuit breaker;
- original upload and cleanup exceptions;
- compatibility mode as an independent accepted rollback path;
- 32 MiB maximum per expanded prepared upload;
- 64 MiB maximum active prepared direct memory;
- 1,024 maximum active buffers;
- unchanged Starsector installation and launcher;
- SPFT format version 1.

Unexpected blobs whose stored upload dimensions already differ from their source dimensions are still declined. Automatic allowlist generation remains disabled.

## Readiness output

Preparation reports:

```text
preparedPixelsAdapter: offline-contract-accepted-npot-padding-implemented
preparedPixelsBehavioralAcceptance: failed-2026-07-22-padding-revalidation-pending
realInstallPilotRequired: true
launchAccelerationClaimed: false
```

## Exact identities

```text
TextureLoader class:
com/fs/graphics/TextureLoader

class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50

archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708

offline transformed SHA-256 from the prior installed check:
b32700195f5837c42dba0f2d202cc0a95af75bfd8b7725d8a2878f59d9e01527
```

Keep these identities exact. A future installed check may produce different transformed bytes because PR #133 changes runtime support code rather than the reviewed target-class matching boundary; record any new result without replacing the historical evidence.

## Review checklist

1. Confirm SPFT files remain source-sized and format version 1 is unchanged.
2. Confirm bottom-up source rows are copied into the lower-left of the power-of-two backing.
3. Confirm right and upper padding are zero-filled.
4. Confirm the direct-memory bounds apply to expanded upload bytes.
5. Confirm original dimensions remain visible to Starsector through the carrier.
6. Confirm normal and exceptional releases return active buffers and active direct bytes to zero.
7. Confirm unexpected pre-padded blobs fail open.
8. Confirm the packaged fixture reproduces the minimum upload-buffer contract.
9. Confirm exact identities, fallback behavior, circuit breaker, cleanup, and compatibility rollback remain unchanged.
10. Confirm no benchmark or acceleration claim is present.

## Operator status

No prepared-pixel command is authorized while PR #133 remains unmerged.

After PR #133 is reviewed and merged, authorize one lifecycle route only:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

Use the exact cache, manifest, index, target file, binary, and installation paths from the current preparation report. Do not guess them. Retain the complete run directory, including `console.txt`, and stop after one run.

Acceptance requires expected prepared hits and padding telemetry, clean dimensions and visuals, only understood original-path fallbacks, zero internal errors, no circuit breaker, zero active buffers/direct bytes/pending buffers at shutdown, no fatal console/log evidence, and completion of the full route.

## Definition of a good handback

Leave:

1. PR #133 reviewed and merged, or review findings recorded against exact commits;
2. full and focused automated validation results;
3. issue #129 updated with the implementation and remaining installed-pilot requirement;
4. evidence and handoff documents aligned with merged code;
5. exactly one operator action, or an explicit statement that no operator run was possible.
