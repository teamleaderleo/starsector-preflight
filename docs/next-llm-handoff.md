# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Review and merge the main-menu comparison stability repair. Do not request another operator run before it merges.

The first automatic comparison attempt is invalid and cannot support a timing or log-equivalence conclusion. Its order was:

```text
prepared
compatibility
```

The prepared half reached the main menu. The compatibility half failed while loading the core `afistfulofcredits` mission descriptor.

The same attempt also changed the enabled-profile fingerprint between halves because GraphicsLib generated two normal-map cache files. That independently invalidates the pair.

## Merged foundations

```text
PR #101 repository hygiene and Locale.ROOT fixes:
dc5bcdc024027ccf1f19f5cc3a53ae4f98a3722c

PR #152 main-menu comparison contract and runner:
2312bfd265c087e0ddf6ec39d6398b322e9bfc7f

PR #154 automatic Starsector-log readiness detection:
434d4f7283e144879c16b735e8004c98d5209787
```

PR #154 passed CI 596, including four detector tests, shell parsing, Python compilation, and full Maven verification.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct visual failure](evidence/2026-07-22-prepared-pixel-coherent-direct-visual-failure.md)
- [dimension-axis visual failure](evidence/2026-07-22-prepared-pixel-dimension-axis-failure.md)
- [corrected-axis launcher pass](evidence/2026-07-23-prepared-pixel-axis-launcher-pass.md)
- [corrected-axis gameplay smoke pass](evidence/2026-07-23-prepared-pixel-gameplay-smoke-pass.md)
- [main-menu comparison pilot contract](evidence/2026-07-23-prepared-pixel-main-menu-comparison-contract.md)
- [invalid first main-menu comparison](evidence/2026-07-23-prepared-pixel-main-menu-comparison-failure.md)
- issue #149 — nonfatal GraphicsLib log baseline before default enablement

## Accepted prepared path

The corrected coherent-direct path passed launcher, main menu, campaign, combat, save, and clean exit on the exact reviewed installation/profile.

Required backing-dimension mapping:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

Retained gameplay archive:

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
repositoryHead: 73f56227d44ed351c16eda55583ac426ffa47c15
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

The safe default is unchanged. Coherent-direct remains opt-in, compatibility remains the rollback, and no acceleration claim has been made.

## Invalid comparison evidence

Archive:

```text
sha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
repositoryHead: ff0e7081aac9df8dc18d2d2d5c72770ce233a5d8
order: prepared,compatibility
```

Prepared half:

```text
COMPLETED
launcherReadyMs: 9788.513
gameLogStartToMainMenuMs: 85060.434
operatorAccepted: true
automatedAccepted: true
```

Compatibility half:

```text
FATAL_LOG_EVIDENCE
exitCode: 6
transformationsApplied: 0
fatal: data/missions/afistfulofcredits/descriptor.json not found
```

The dialog's mod directories are the complete resource-search path. The first listed mods are not evidence of culpability. The same working directory and classpath were used in both halves apart from the intended texture mode/property difference. Exact causality for the resource-resolution failure remains unproven.

Profile drift:

```text
initial/prepared fingerprint:
ccbc7f1aebf89c7ed8f21c886ac6a869b496fa3edd36b8943d1269cacd1a8ebe

compatibility-start fingerprint:
3c1fc13ee4b47a93d36122ee2804070dbacf43523a3d38df5cc531e35e4513fe
```

Only `shaderLib` changed:

```text
files: +2
imageFiles: +2
bytes: +136
imageBytes: +136
```

Generated files recorded by the prepared log:

```text
cache/sotf_wisp_lesser___SHIP_normal.png
cache/tpc_weaver___TURRET0_normal.png
```

## Repair contract

The repair branch is:

```text
repair/main-menu-comparison-profile-stability
```

The replacement runner must:

- use a six-second launcher quiet/safety confirmation while retaining the final launcher log activity as the measured endpoint;
- save the initial census profile and fingerprint;
- rerun deep preparation before and after each half;
- abort and retain per-mod deltas if the profile changes;
- recognize the exact GraphicsLib image-only drift shape without dismissing it;
- verify the core mission list and `afistfulofcredits` descriptor remain present and byte-identical;
- package evidence on any failure;
- preserve one randomized pair, automatic log timing, compatibility rollback, opt-in coherent-direct, and `benchmarkAccepted=false`.

## Current readiness

```text
preparedPixelsAdapter=pot-bypass-enabled-npot-coherent-direct-gameplay-accepted-opt-in
preparedPixelsBehavioralAcceptance=accepted-2026-07-23-exact-profile
preparedPixelsDefaultEnablement=blocked-pending-main-menu-comparison-and-repeat-timing
preparedPixelsComparisonPilotRequired=true
repeatTimingCampaignRequired=true
preparedPixelsNextOperatorAction=single-main-menu-comparison-pilot
launchAccelerationClaimed=false
```

The machine-readable action remains the eventual replacement pilot, but no operator action is authorized until the repair PR is merged and the handoffs are aligned.

## Decision after a valid replacement pair

- Equivalent diagnostics plus stable profile and accurate detection: classify the messages as exact-profile baseline and design the repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate before timing or default enablement.
- Profile drift: stop; the two modes were not compared against the same profile.
- Visual, lifecycle, core-resource, or detector failure: stop and retain compatibility mode as rollback.

Do not use the failed pair's 85-second prepared measurement as a performance result, disable individual mods based on the resource-search list, or rerun the old harness.
