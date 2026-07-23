# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

The corrected coherent-direct NPOT prepared-pixel path has passed launcher and gameplay visual/lifecycle acceptance for the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

It remains opt-in and is not enabled by default. No further operator run is currently authorized. Benchmarks and acceleration claims remain blocked.

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
- issue #149 — nonfatal GraphicsLib log baseline before default enablement

## Corrected technical conclusion

The prepared NPOT upload bytes match Starsector's original row-padded layout. Cached pixels form coherent source-sized images. The two backing-dimension writes are required and use:

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
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
prepared hits: 5015
coherent-direct NPOT hits: 4450
padded uploads: 4450
fallbacks/internal errors: 0
releases: 5015
active direct bytes at shutdown: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

The operator confirmed normal launcher, main-menu, campaign, and combat visuals; a successful disposable-save write; attached command lifetime through game exit; clean exit; and no visible corruption.

## Nonfatal log caveat

The bounded console capture contains nonfatal third-party GraphicsLib/ShaderLib diagnostics:

- 12 normal-map load failures with an LWJGL buffer-size message;
- shader compilation diagnostics;
- music-source initialization warnings.

Preflight found no fatal lifecycle evidence and the operator saw no related visual corruption. A single prepared-path run cannot prove whether these are pre-existing profile noise or prepared-path-related.

Do not repeat the gameplay smoke merely to investigate this. Engineering must first review issue #149 and design a controlled baseline or bounded diagnostic.

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
preparedPixelsDefaultEnablement=blocked-pending-log-baseline-and-timing
realInstallPilotRequired=false
preparedPixelsNextOperatorAction=none-engineering-review-required
launchAccelerationClaimed=false
```

## Operator action

None.

Do not rerun the launcher probe, gameplay smoke, or benchmarks unless a reviewed follow-up explicitly authorizes a new bounded route.

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
