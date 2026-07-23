# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Perform exactly one replacement two-run main-menu comparison from current `main` using the merged profile-stability repair.

The first automatic attempt was invalid. Do not use its timing or log counts as a comparison result.

The replacement pair remains:

```text
compatibility decoded-image path
accepted coherent-direct prepared path
```

It is one preliminary sample per mode, not a benchmark.

## Merged foundations

```text
PR #101 repository hygiene and Locale.ROOT fixes:
dc5bcdc024027ccf1f19f5cc3a53ae4f98a3722c

PR #152 main-menu comparison contract and runner:
2312bfd265c087e0ddf6ec39d6398b322e9bfc7f

PR #154 automatic Starsector-log readiness detection:
434d4f7283e144879c16b735e8004c98d5209787

PR #156 profile stability and launcher-settling repair:
03439b33c99b1fb3abfff9ada88aacc826c33e74
```

PR #156 passed CI 598 on head `25a0adfc5a2296cc25f08bd646c481fcaca10322`. Validation included shell parsing, automatic-log detector tests, profile-guard compilation and four tests, and full Maven verification.

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
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

Coherent-direct remains opt-in. Compatibility remains the rollback. No acceleration claim has been made.

## Invalid first comparison

```text
archiveSha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
repositoryHead: ff0e7081aac9df8dc18d2d2d5c72770ce233a5d8
order: prepared,compatibility
```

Prepared reached the main menu. Compatibility terminated while loading:

```text
data/missions/afistfulofcredits/descriptor.json
```

The dialog listed Starsector's complete resource-search roots. The first listed mod directories are not proof that those mods caused the failure. Exact resource-resolution causality remains unproven.

The pair was already invalid because the profile fingerprint changed between halves. GraphicsLib generated:

```text
cache/sotf_wisp_lesser___SHIP_normal.png
cache/tpc_weaver___TURRET0_normal.png
```

Only `shaderLib` changed:

```text
files/imageFiles: +2
bytes/imageBytes: +136
```

Do not use the prepared half's 85-second value as a performance result.

## Merged replacement-run safeguards

The runner now:

- waits for six seconds of launcher log quiet before instructing the Play click;
- records the final launcher log activity as the endpoint, excluding that safety wait;
- saves the initial census profile and fingerprint;
- performs deep preparation before and after each half;
- aborts and retains exact per-mod deltas on any profile drift;
- uses a tested guard to identify—but still reject—the observed GraphicsLib image-cache drift shape;
- requires and hashes the core mission list and `afistfulofcredits` descriptor;
- verifies both core files remain present and byte-identical;
- packages evidence on failures;
- preserves automatic main-menu detection and `benchmarkAccepted=false`.

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

Type `COMPARE`. For each randomized half:

```text
press Enter once to launch
→ wait for automatic launcher-ready notification after the safety confirmation
→ click Play when instructed
→ wait for automatic main-menu-ready notification
→ confirm the menu is fully visible and responsive
→ exit from main menu
→ close launcher if it reappears
```

Do not enter a campaign or combat. Upload the generated Desktop archive after the script ends, including when it stops because of profile drift or another failure. Do not rerun it.

## Decision after the replacement pair

- Equivalent diagnostics, stable profile, unchanged core resources, and accurate detection: classify the messages as exact-profile baseline and design the repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate before timing or default enablement.
- Profile drift: stop; the modes were not compared against the same profile.
- Visual, lifecycle, core-resource, or detector failure: stop and retain compatibility mode as rollback.

Preserve exact identity gates, cleanup behavior, memory limits, compatibility rollback, opt-in coherent-direct, and the no-acceleration-claim boundary.
