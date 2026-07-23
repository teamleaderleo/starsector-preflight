# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

The corrected coherent-direct NPOT prepared-pixel path has passed launcher and gameplay visual/lifecycle acceptance for the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

It remains opt-in and is not enabled by default.

The stability repair is merged. Exactly one replacement two-run main-menu comparison is now authorized. It is not a benchmark.

## Accepted implementation

```text
PR #145 — corrected height-first/width-second dimension axes
PR #147 — gameplay-smoke runner
PR #151 — exact-profile gameplay acceptance
PR #152 — main-menu comparison foundation
PR #154 — automatic Starsector-log readiness detector
PR #156 — profile-stability and launcher-settling repair
```

PR #156 merged as:

```text
03439b33c99b1fb3abfff9ada88aacc826c33e74
```

It passed CI 598, including shell parsing, detector tests, profile-guard compilation and four tests, and full Maven verification.

The required texture backing-dimension mapping is:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

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

## Invalid first comparison

```text
archiveSha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
order: prepared,compatibility
```

The prepared half reached the main menu. The compatibility half stopped while loading:

```text
data/missions/afistfulofcredits/descriptor.json
```

The dialog's long list contained all resource-search directories. AI Tweaks, Arma Armatura, and the first other entries are not proven causes merely because they appeared first. Do not disable mods from that list.

The pair was invalid independently because GraphicsLib generated two normal-map cache files during the first half and changed the profile fingerprint before the second half:

```text
cache/sotf_wisp_lesser___SHIP_normal.png
cache/tpc_weaver___TURRET0_normal.png

shaderLib files/imageFiles: +2
shaderLib bytes/imageBytes: +136
```

Do not use the prepared half's 85-second measurement as a performance result.

## Replacement-run safeguards

The merged runner now:

- waits for six seconds of launcher log quiet before instructing the Play click;
- keeps the final launcher log activity as the measured endpoint, so the safety wait is excluded;
- records the initial exact profile fingerprint;
- performs deep preparation before and after each half;
- stops and packages exact per-mod deltas on any profile drift;
- uses a tested guard to identify, but still reject, the observed GraphicsLib image-cache drift shape;
- hashes the core mission list and `afistfulofcredits` descriptor;
- verifies both core resources remain present and byte-identical;
- preserves automatic main-menu detection and `benchmarkAccepted=false`.

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

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-main-menu-comparison-pilot.sh
```

Type:

```text
COMPARE
```

For each randomized half:

1. Press Enter once to launch.
2. Wait for the automatic launcher-ready notification after the six-second safety confirmation.
3. Click **Play Starsector** when instructed; do not press Enter for timing.
4. Wait for the automatic main-menu-ready notification.
5. Confirm the menu is fully visible and responsive.
6. Exit Starsector from the main menu.
7. Close the launcher if it reappears.
8. Answer the visual, detector, attachment, and clean-exit questions accurately.

Do not load a campaign or enter combat. The runner may deliberately stop after one half if the profile changes. Upload the Desktop `.tar.gz` whether it completes both halves or stops with retained drift/failure evidence. Do not run it again.

## Interpretation boundary

```text
samplesPerMode=1
preliminaryOnly=true
benchmarkAccepted=false
timingMethod=automatic-starsector-log-phase-detection
```

A valid pair additionally requires:

```text
profileStableAcrossBothHalves=true
unchanged core mission resources
accurate automatic readiness detection
clean lifecycle and visuals in both modes
```

Do not average the invalid first attempt, enable coherent-direct by default, or claim acceleration.

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
