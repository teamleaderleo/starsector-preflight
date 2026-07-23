# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

The corrected coherent-direct NPOT prepared-pixel path has passed launcher and gameplay visual/lifecycle acceptance for the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

It remains opt-in and is not enabled by default.

The first two-run main-menu comparison attempt is invalid. Do not run the comparison again until the stability repair is merged.

## Accepted implementation

```text
PR #145 — corrected height-first/width-second dimension axes
PR #147 — gameplay-smoke runner
PR #151 — exact-profile gameplay acceptance
PR #152 — main-menu comparison foundation
PR #154 — automatic Starsector-log readiness detector
```

The required texture backing-dimension mapping is:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

With that mapping, coherent carriers, cached colors, and direct cached NPOT buffers rendered correctly through launcher, main menu, campaign, combat, save, and clean exit.

## Retained gameplay acceptance

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active direct bytes at shutdown: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

## What happened in the first comparison

Retained failed archive:

```text
sha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
repositoryHead: ff0e7081aac9df8dc18d2d2d5c72770ce233a5d8
order: prepared,compatibility
```

The prepared half ran first and reached the main menu normally:

```text
launcherReadyMs: 9788.513
gameLogStartToMainMenuMs: 85060.434
operatorAccepted: true
automatedAccepted: true
```

The compatibility half ran second and Starsector stopped while loading the core mission:

```text
data/missions/afistfulofcredits/descriptor.json
```

The dialog printed every resource search directory. AI Tweaks, Arma Armatura, and the other early entries are not identified as causes merely because they appear first in that list. Do not disable mods based on this dialog.

The same `null/data/missions/mission_list.csv` source representation appeared in the successful prepared half. The archive did not show a meaningful working-directory or classpath difference between modes. The precise resource-resolution cause is not yet proven.

## Why the pair is invalid regardless of the fatal

The first half generated two GraphicsLib normal-map cache files:

```text
cache/sotf_wisp_lesser___SHIP_normal.png
cache/tpc_weaver___TURRET0_normal.png
```

That changed the enabled-profile fingerprint before the compatibility half:

```text
before/prepared:
ccbc7f1aebf89c7ed8f21c886ac6a869b496fa3edd36b8943d1269cacd1a8ebe

after/pre-compatibility:
3c1fc13ee4b47a93d36122ee2804070dbacf43523a3d38df5cc531e35e4513fe
```

The only census delta was `shaderLib` / GraphicsLib:

```text
files: +2
imageFiles: +2
bytes: +136
imageBytes: +136
```

The two halves therefore did not use an identical profile. The prepared timing is not a benchmark result.

Full evidence is recorded in:

- [invalid first main-menu comparison](evidence/2026-07-23-prepared-pixel-main-menu-comparison-failure.md)
- issue #149

## Repair in progress

Branch:

```text
repair/main-menu-comparison-profile-stability
```

The repaired runner will:

- wait for six seconds of launcher log quiet before telling you to click Play;
- keep the final launcher log activity as the measured endpoint, so the safety wait is not added to the timing;
- capture the exact initial profile fingerprint;
- rerun deep preparation before and after each half;
- stop and package exact mod deltas if the profile changes;
- verify the core mission list and descriptor remain present and byte-identical;
- preserve automatic main-menu detection and the single-pair/preliminary boundary.

## Safe default

Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

- NPOT textures use Starsector's original decode/conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited by Preflight.

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

## Operator action

None until the repair PR is reviewed and merged.

Do not rerun the current comparison script, enter a campaign for timing, average the failed pair, enable coherent-direct by default, or claim acceleration.

## Preserved boundaries

- exact archive, class, method, source, and classloader identity gates;
- SPFT version 1;
- original asynchronous preloader handoff;
- original upload caller, cleanup wrapper, and exception behavior;
- current circuit breaker;
- 32 MiB maximum direct bytes per texture;
- 64 MiB maximum active prepared direct memory;
- 1,024 maximum active and pending buffers;
- compatibility mode as rollback;
- no automatic allowlist generation;
- no acceleration claim.
