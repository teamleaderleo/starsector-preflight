# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Review and merge PR #132, the narrow repair for the first real prepared-pixel lifecycle failure.

Another prepared-pixel operator run, repeated measurement, and acceleration claims remain blocked.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- issue #129 — NPOT upload dimensions and exceptional-path direct-buffer accounting
- issue #130 — fatal console evidence missed when the launcher exits zero
- issue #128 — silent prepare progress and stale readiness fields

## State as of 2026-07-22

PR #132 is review-ready on branch `repair/prepared-pixel-lifecycle-129-130-128`.

Validated code head:

```text
6ae62ac627244ab1734397a94cb6460bef2d69e9
```

The standard repository workflows passed on that head:

- CI run 483 — `mvn --batch-mode --no-transfer-progress verify`
- Prepare command tests run 73
- Texture cache tests run 334
- Vanilla adapter gate tests run 335
- Adapter probe analysis tests run 169

### Repair #129 — prepared-pixel dimensions and ownership

The runtime now declines prepared payloads before carrier creation whenever the source dimensions require next-power-of-two padding. This preserves the original decode/conversion fallback for the observed unsupported contract.

Focused fixtures cover:

```text
597 * 373 * 3 = 668043 source bytes
1024 * 512 * 3 = 1572864 upload bytes
```

NPOT RGBA receives the same explicit fallback. Power-of-two payloads prove:

```text
ByteBuffer.remaining() == uploadWidth * uploadHeight * channels
```

The exact converter callers receive an exceptional-release guard. The guard releases current prepared-buffer accounting and rethrows the original exception. Tests prove active buffers and active direct bytes return to zero after normal cleanup and upload exceptions. Fallback creates no prepared direct buffer.

### Repair #130 — child console capture and lifecycle classification

`RunCommand` now drains one combined child stdout/stderr stream while forwarding it to the operator. It retains a bounded 1 MiB chronological tail at `console.txt` in the run directory.

Lifecycle evidence now inspects both Starsector log files and captured child console bytes. A fatal child marker produces a non-clean effective exit even when the launcher shell returns zero. `launcherExitCode` preserves the shell result.

Tests cover:

- fatal child console plus zero launcher exit;
- clean zero exit;
- nonzero launcher exit;
- existing log-file fatal evidence;
- output volume above the capture limit across stdout and stderr.

### Repair #128 — preparation progress and readiness

`prepare` now prints bounded stage start/completion lines to stderr while stdout remains the report path.

Prepared-pixel readiness now records:

```text
preparedPixelsAdapter: offline-contract-accepted-live-pilot-revalidation-required
preparedPixelsBehavioralAcceptance: failed-2026-07-22-revalidation-required
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

offline transformed SHA-256:
b32700195f5837c42dba0f2d202cc0a95af75bfd8b7725d8a2878f59d9e01527
```

Keep these identities exact. Keep automatic allowlist generation disabled.

## Review checklist

1. Inspect PR #132 against issues #128, #129, and #130.
2. Confirm NPOT prepared payloads decline before carrier creation.
3. Confirm the caller-level exception handler releases accounting and rethrows the original exception.
4. Confirm the combined child stream is continuously drained, operator-visible, and bounded on disk.
5. Confirm fatal console evidence overrides clean lifecycle classification while preserving `launcherExitCode`.
6. Confirm stderr progress leaves stdout stable.
7. Confirm compatibility mode remains independent and accepted.
8. Confirm exact identities, direct-memory limits, and circuit breaker remain unchanged.
9. Confirm the PR contains no benchmark or acceleration claim.

## Operator status

The operator has no prepared-pixel command to run now.

Normal Starsector launch and the accepted compatibility route remain available. A repaired prepared-pixel lifecycle route requires merge plus explicit review approval.

After approval, authorize one lifecycle route only:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

Stop after that one run and retain the complete run directory.

## Definition of a good handback

Leave:

1. PR #132 reviewed and merged, or review findings recorded with exact commit references;
2. full and focused test results;
3. issue updates for #128, #129, and #130;
4. evidence and handoff documents aligned with the merged code;
5. one explicit operator action, or an explicit statement that no operator run is authorized.
