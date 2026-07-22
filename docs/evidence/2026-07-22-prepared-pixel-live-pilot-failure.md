# Prepared-pixel live pilot failure

Date: 2026-07-22

Status: behavioral acceptance failed; do not retry or benchmark prepared-pixels until repaired.

## Environment

- Platform: macOS on Apple silicon
- Starsector: 0.98a-RC8
- Installation: `/Applications/Starsector.app`
- Enabled profile: 71 resolved mods, no missing enabled mod IDs
- Wrapper Java: 21.0.10
- Adapter mode: `ENABLED`
- Texture mode: `PREPARED_PIXELS`
- Explicit manifest/index fingerprint: `93a8728b522f0779c2c1ca131e89bf3ac8afda050b2aae3d622c0938d6a6bb77`

The offline installed-class gate and exact-profile preparation had already passed.

## Fatal failure

The game failed during launcher texture upload before reaching the main menu:

```text
java.lang.IllegalArgumentException: Number of remaining buffer elements is 668043, must be at least 1572864
    at org.lwjgl.BufferChecks.checkBuffer(...)
    at org.lwjgl.opengl.GL11.glTexImage2D(...)
    at com.fs.graphics.TextureLoader.o00000(Unknown Source)
```

The element counts identify an RGB non-power-of-two upload mismatch:

```text
668043   = 597 * 373 * 3
1572864  = 1024 * 512 * 3
```

The prepared bridge supplied source-sized RGB bytes while Starsector's GL upload call required the next-power-of-two upload allocation.

## Adapter evidence

The retained adapter report shows:

```text
exactMatches: 1
transformationsApplied: 1
containedFailures: 0
plan: texture-prepared-pixels-v2
```

Prepared-pixel telemetry at shutdown:

```text
carriers: 1
directAttempts: 1
hits: 1
bytesBypassed: 668043
peakDirectBytes: 668043
activeBuffers: 1
activeDirectBytes: 668043
releases: 0
releasedBytes: 0
internalErrors: 0
fallbacks: 0
```

This proves that the exact target transformed and served one prepared payload, then failed before direct-buffer release accounting completed.

## Lifecycle-reporting false negative

Despite the fatal console evidence, `run.json` recorded:

```text
exitCode: 0
launcherExitCode: 0
outcome: COMPLETED
lifecycleEvidence.fatalDetected: false
```

The launcher script returned zero and the current post-run log inspection did not capture the fatal visible in inherited stdout/stderr. Process exit alone is therefore not valid acceptance evidence for this route.

## Decision

Prepared pixels failed behavioral acceptance. Repeated benchmarks are blocked. Compatibility mode remains the accepted rollback path.

Required repairs before another real pilot:

1. Correct or safely decline non-power-of-two upload-dimension cases before a prepared buffer reaches `glTexImage2D`.
2. Prove direct-buffer release/accounting on exceptional and fallback paths.
3. Capture bounded child console output or otherwise classify fatal console evidence even when the launcher exits zero.
4. Extend fixtures and contract evidence to cover upload dimensions, RGB/RGBA padding semantics, exact buffer remaining size, and fatal zero-exit classification.

Tracking issues:

- #129 — NPOT prepared-pixel upload dimensions and exceptional-path buffer accounting
- #130 — fatal console evidence missed when the launcher exits zero
- #128 — prepare progress and stale readiness output

Do not weaken exact identity gates, patch the game installation, or claim acceleration from this failed run.