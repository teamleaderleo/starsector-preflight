# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

The corrected coherent-direct prepared-pixel path has passed the exact-profile launcher and gameplay smoke. No further operator run is currently authorized.

Next engineering work is to review the retained nonfatal GraphicsLib/ShaderLib log diagnostics tracked in issue #149 and decide what controlled baseline, if any, is needed before default enablement or timing work.

Do not repeat the launcher/gameplay smoke, benchmark, enable coherent-direct by default, or make an acceleration claim.

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
- issue #129 — original NPOT crash and prepared-path acceptance history
- issue #149 — nonfatal GraphicsLib log baseline before default enablement

## Merged implementation milestones

```text
PR #132 lifecycle, release, and fatal-evidence repair:
4f3b79c6d7683242d16cb7b34081cd7800f20017

PR #135 NPOT fail-open and original-layout observation:
1fd63567e5834546ab5d617234f84371df9909ea

PR #137 coherent cached image with retained original converter:
fd390ff797e554101cc78ab52516273c1c06fc24

PR #139 coherent carrier plus direct cached NPOT diagnostic:
23a8ec653d9f07e5df50ff3deab04efdf4104e49

PR #141 backing-dimension replay with incorrect axis assignment:
1b4194977c0fac9a5717d05bec6e858cb2fec419

PR #145 corrected height-first/width-second dimension axes:
d2333deca1697214231b6392b944ea2992150cae

PR #147 guarded gameplay-smoke runner and launcher-pass evidence:
ab4d1abfacf81c0c27216894b56ccffe3314b0a1
```

## Established facts

The direct NPOT byte layout matches Starsector's original row-padded upload layout. Coherent source-sized cached images are valid. The two texture-object backing-dimension writes are required, with this reviewed mapping:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

The corrected direct path passed launcher, main-menu, campaign, combat, save, and clean-exit visual/lifecycle scope on the exact reviewed install and profile.

Retained gameplay identity:

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
repositoryHead: 73f56227d44ed351c16eda55583ac426ffa47c15
jarSha256: 4e62577b98f28894322bb9e86f8cdeda4be4c6a3373632352c2c113078c3689a
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
padded uploads: 4450
fallbacks/internal errors: 0
releases: 5015
active/pending buffers at shutdown: 0
fatal evidence: none
operatorAccepted: true
automatedAccepted: true
```

## Nonfatal log caveat

The bounded gameplay console contains third-party GraphicsLib/ShaderLib diagnostics, including 12 normal-map load failures with an LWJGL buffer-size message, shader compilation diagnostics, and music-source warnings. The operator saw no related corruption and Preflight found no fatal evidence.

Do not attribute these messages to Preflight or dismiss them as baseline noise without a controlled comparison. They block default enablement, not the completed gameplay smoke result.

## Current readiness

```text
preparedPixelsAdapter=pot-bypass-enabled-npot-coherent-direct-gameplay-accepted-opt-in
preparedPixelsBehavioralAcceptance=accepted-2026-07-23-exact-profile
preparedPixelsDefaultEnablement=blocked-pending-log-baseline-and-timing
realInstallPilotRequired=false
preparedPixelsNextOperatorAction=none-engineering-review-required
launchAccelerationClaimed=false
```

The safe default remains unchanged: without `-Dpreflight.preparedPixels.coherentDirect=true`, NPOT textures use Starsector's original decode/conversion path. Compatibility mode remains the accepted rollback.

## Next engineering decision

Review issue #149 and determine whether to:

1. classify the diagnostics from existing baseline evidence;
2. add a narrowly scoped compatibility/original-path log-baseline runner; or
3. add bounded diagnostic telemetry before another run.

No new operator action should be requested until that decision is implemented and reviewed. Timing campaigns remain separate and blocked.

## Definition of a good handback

Preserve the exact gameplay archive identity, acceptance scope, log caveat, safe default, identity gates, cleanup behavior, compatibility rollback, and memory limits. Do not broaden the exact target or claim acceleration.
