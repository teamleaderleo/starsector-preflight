# Prepared-pixel coherent-direct gameplay smoke pass

Date: 2026-07-23

## Result

The single authorized coherent-direct gameplay smoke completed successfully on the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

Operator route:

```text
normal launcher
→ Play Starsector
→ normal main menu
→ disposable campaign
→ campaign visual inspection
→ one combat visual inspection
→ save
→ return to menu
→ clean exit
```

The operator answered every required acceptance question positively and reported no black, sliced, repeated, stretched, missing, flipped, or progressively corrupt textures.

## Retained identity

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
repositoryHead: 73f56227d44ed351c16eda55583ac426ffa47c15
jarSha256: 4e62577b98f28894322bb9e86f8cdeda4be4c6a3373632352c2c113078c3689a
installedArchiveSha256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
installedClassSha256: d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
dimensionReplay: reviewed-converter-height-first-width-second
```

## Automated lifecycle evidence

```text
runtimeSeconds: 624.640
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
postprocessingFailures: 0
```

The wrapper remained attached for the complete gameplay route and exceeded the 120-second attachment guard.

## Prepared-pixel telemetry

```text
transformationsApplied: 1
prepared hits: 5015
coherent-direct NPOT hits: 4450
padded uploads: 4450
bytes bypassed: 555507671
upload bytes supplied: 909844328
padding bytes: 354336657
fallbacks: 0
NPOT probe fallbacks: 0
coherent original-converter fallbacks: 0
internal errors: 0
releases: 5015
active direct bytes at shutdown: 0
active buffers at shutdown: 0
pending buffers at shutdown: 0
peak direct bytes: 16777216
circuit breaker: inactive
```

## Operator result

`operator-gameplay-result.json` records:

```text
launcherVisualsNormal: true
mainMenuNormal: true
campaignVisualsNormal: true
combatVisualsNormal: true
saveCompleted: true
commandAttachedUntilGameExit: true
cleanExitObserved: true
visualCorruptionObserved: false
operatorAccepted: true
automatedAccepted: true
```

## Nonfatal log caveat

The bounded console capture contains nonfatal third-party GraphicsLib/ShaderLib diagnostics, including 12 normal-map load failures with an LWJGL buffer-size message, shader compilation diagnostics, and two music-source initialization warnings. Preflight found no fatal lifecycle evidence and the operator saw no related visual corruption during the required route.

This single run cannot establish whether those messages are pre-existing profile noise or prepared-path-related. Preserve them as a follow-up comparison item before default enablement. They do not invalidate the completed gameplay visual/lifecycle smoke.

## Decision

The corrected coherent-direct NPOT path is behaviorally accepted for the exact reviewed installation/profile at launcher, main-menu, campaign, combat, save, and clean-exit scope.

Still blocked:

- enabling coherent-direct NPOT by default;
- acceleration or performance claims;
- benchmarks;
- broad compatibility claims beyond the exact reviewed target/profile;
- attributing the nonfatal GraphicsLib/ShaderLib diagnostics without a controlled baseline.

Compatibility mode remains the accepted rollback and the ordinary safe default remains unchanged.
