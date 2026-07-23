# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

The corrected coherent-direct NPOT prepared-pixel path has passed launcher and gameplay visual/lifecycle acceptance for the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

It remains opt-in and is not enabled by default. The bounded two-run main-menu comparison runner is merged and exactly one pilot is now authorized. The pilot is not a benchmark.

Merged runner:

```text
PR #152
2312bfd265c087e0ddf6ec39d6398b322e9bfc7f
```

It passed CI 591, texture tests 420, and preparation tests 124 before merge.

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

## Corrected technical conclusion

The prepared NPOT upload bytes match Starsector's original row-padded layout. Cached pixels form coherent source-sized images. The two backing-dimension writes use:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

With that mapping, coherent carriers, cached colors, and direct cached NPOT buffers rendered correctly through launcher, main menu, campaign, combat, save, and clean exit.

## Retained gameplay evidence

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
repositoryHead: 73f56227d44ed351c16eda55583ac426ffa47c15
jarSha256: 4e62577b98f28894322bb9e86f8cdeda4be4c6a3373632352c2c113078c3689a
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active direct bytes at shutdown: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

## Why another bounded run is needed

The bounded gameplay console contained nonfatal third-party diagnostics:

- 12 GraphicsLib normal-map load failures with a 4-element-versus-16-element LWJGL buffer message;
- ShaderLib shader creation diagnostics;
- music-source initialization warnings.

No related visual corruption or fatal evidence was observed. The console capture was truncated, and a prepared-only run cannot establish whether these messages are compatibility-profile baseline or prepared-path-related.

The comparison pilot captures complete appended log bytes for compatibility decoded-image and prepared-pixel modes. Both receive the same verified cache context. Compatibility retains Starsector's original converter/upload path; this is not raw uninstrumented vanilla.

## Safe default

Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

- power-of-two prepared hits may use the existing direct seam;
- NPOT textures use Starsector's original decode/conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited.

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

The runner randomizes which mode runs first. For each half:

1. Press Enter to launch it.
2. Press Enter when the launcher is fully visible and stable.
3. Return to the terminal and press Enter immediately before clicking **Play Starsector**.
4. Press Enter when the main menu is fully visible and responsive.
5. Exit Starsector from the main menu.
6. Close the launcher if it reappears.
7. Answer the visual and clean-exit questions accurately.

Do not load a campaign or enter combat. Stop if either mode has visible corruption or a crash.

The runner packages both run directories and `comparison-result.json` on the Desktop. Upload that one archive.

## Interpretation boundary

The timing output is one sample per mode and includes operator reaction/focus-switch noise. It is preliminary only:

```text
samplesPerMode=1
preliminaryOnly=true
benchmarkAccepted=false
```

Do not repeat the pilot, average the two numbers as a benchmark, enable coherent-direct by default, or make an acceleration claim.

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
