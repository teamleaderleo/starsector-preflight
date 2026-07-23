# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Review and merge the automatic Starsector-log readiness detector, then perform exactly one two-run prepared-pixel main-menu comparison from current `main`.

The two halves are:

```text
compatibility decoded-image path
accepted coherent-direct prepared path
```

Compatibility uses the same verified texture cache context while retaining Starsector's original converter/upload path. This isolates the prepared-pixel seam; it is not raw uninstrumented vanilla.

The pilot captures complete appended Starsector log deltas and one automatically detected launcher/main-menu timing sample per mode. It is preliminary evidence, not a benchmark.

Do not repeat the accepted launcher/gameplay smokes, enter campaign/combat during this pilot, enable coherent-direct by default, or claim acceleration.

## Merged maintenance and comparison work

```text
PR #101 repository hygiene and Locale.ROOT fixes:
dc5bcdc024027ccf1f19f5cc3a53ae4f98a3722c

PR #152 original main-menu comparison contract and runner:
2312bfd265c087e0ddf6ec39d6398b322e9bfc7f
```

PR #101 SHA-pinned CI actions, added the opt-in `-Panalysis` Error Prone profile, and fixed default-locale lowercase calls with `Locale.ROOT`. No further action on PR #101 is required.

PR #152 passed CI 591, texture tests 420, and preparation tests 124 before merge. Its operator-enter timing markers are being replaced before the authorized pilot runs.

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
- issue #149 — nonfatal GraphicsLib log baseline before default enablement

## Established facts

The direct NPOT byte layout matches Starsector's original row-padded upload layout. Coherent source-sized cached images are valid. The two texture-object backing-dimension writes are required, with this reviewed mapping:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

The corrected direct path passed launcher, main-menu, campaign, combat, save, and clean-exit scope on the exact reviewed installation/profile.

Retained gameplay evidence:

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
repositoryHead: 73f56227d44ed351c16eda55583ac426ffa47c15
jarSha256: 4e62577b98f28894322bb9e86f8cdeda4be4c6a3373632352c2c113078c3689a
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

## Why the comparison is required

The bounded gameplay console contained nonfatal GraphicsLib/ShaderLib diagnostics, including 12 normal-map buffer failures, shader creation errors, and music-source warnings. No related corruption or fatal evidence was observed.

The retained console was truncated to 1 MiB, so attribution requires full per-run log deltas. A single prepared-path run cannot establish whether the diagnostics are ordinary compatibility-profile baseline or prepared-path-related.

## Current readiness

```text
preparedPixelsAdapter=pot-bypass-enabled-npot-coherent-direct-gameplay-accepted-opt-in
preparedPixelsBehavioralAcceptance=accepted-2026-07-23-exact-profile
preparedPixelsDefaultEnablement=blocked-pending-main-menu-comparison-and-repeat-timing
preparedPixelsComparisonPilotRequired=true
repeatTimingCampaignRequired=true
realInstallPilotRequired=false
preparedPixelsNextOperatorAction=single-main-menu-comparison-pilot
launchAccelerationClaimed=false
```

The safe default remains unchanged: without `-Dpreflight.preparedPixels.coherentDirect=true`, NPOT textures use Starsector's original decode/conversion path. Compatibility mode remains the accepted rollback.

## Automatic detector contract

The follow-up replaces all readiness Enter markers with an inode-aware `starsector.log*` watcher.

Launcher readiness:

```text
graphics/fonts/orbitron12_0.png
→ 1.5-second quiet confirmation
```

Game/main-menu readiness:

```text
first newly appended timestamped line after the Play instruction
→ CampaignGameManager save-descriptor scan
→ GraphicsLib VRAM after unload/preload
→ 6-second quiet confirmation
```

The detector records the final log activity before each quiet confirmation, so the confirmation wait is not added to the timing. It writes:

```text
launcher-ready-detection.json
main-menu-ready-detection.json
gameLogStartToMainMenuMs
```

The operator only clicks Play, visually confirms that the automatic notification did not fire early, exits from the main menu, and answers the pass/fail questions.

The helper unit tests cover launcher readiness, deferred-line quiet reset, and inode-based log rotation/rename handling. CI must run those tests plus shell parsing and full Maven verification.

## Authorized operator action after merge

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-main-menu-comparison-pilot.sh
```

Type `COMPARE`. For each randomized half:

```text
press Enter to launch
→ wait for automatic launcher-ready notification
→ click Play when instructed
→ wait for automatic main-menu-ready notification
→ confirm the menu is actually visible/responsive
→ exit from main menu
→ close launcher if it reappears
```

Do not load a campaign or enter combat. Upload the generated Desktop archive after both halves complete.

## Decision after the pilot

- Equivalent log diagnostics plus visually accurate automatic detection: classify the messages as exact-profile compatibility baseline and build a repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate the prepared carrier path before timing or default enablement.
- Early/failed readiness detection: adjust the reviewed marker contract before collecting timing samples.
- Visual or lifecycle failure: stop and retain compatibility mode as rollback.

Do not repeat the pilot or treat one timing pair as a benchmark.

## Definition of a good handback

Retain exact repository/JAR/install identities, randomized order, both run directories, detector JSON, full log deltas, classifications, comparison result, visual detector validation, and lifecycle status. Update issue #149 and readiness. Preserve identity gates, cleanup behavior, compatibility rollback, and memory limits.
