# Prepared-pixel main-menu comparison pilot contract

Date: 2026-07-23

## Purpose

The accepted coherent-direct prepared-pixel path completed launcher and gameplay smoke testing on the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile. The retained gameplay console also contained nonfatal GraphicsLib/ShaderLib diagnostics whose attribution is unresolved.

This contract authorizes one bounded two-run comparison pilot:

```text
compatibility decoded-image path
versus
accepted coherent-direct prepared path
```

Compatibility mode uses the same verified texture cache context while retaining Starsector's original converter and upload path. This isolates the lower prepared-pixel seam; it is not a raw uninstrumented vanilla run.

The pilot has two goals:

1. capture full appended Starsector log deltas for both modes and compare known diagnostic counts;
2. collect one preliminary automatically detected launcher-readiness and game-start-to-main-menu timing sample per mode.

A single pair is not a benchmark and cannot support an acceleration claim.

## Exact scope

Each half uses the same repository build, Starsector installation, enabled mod profile, preparation artifacts, and wrapper/agent identities.

Required route:

```text
launch wrapper
→ automatic launcher-ready detection
→ operator clicks Play Starsector
→ automatic first post-click log detection
→ automatic main-menu-ready detection
→ operator visually confirms the detector fired at the correct time
→ exit from main menu
→ close launcher if it reappears
→ clean wrapper exit
```

Do not enter a campaign or combat.

The order is randomized between:

```text
compatibility,prepared
prepared,compatibility
```

The selected order is retained in the archive. `ORDER` may be set explicitly only for troubleshooting before the run; do not repeat the pilot in the opposite order.

## Automatic timing method

The runner tails `logs/starsector.log*` and tracks files by inode so rotation or renaming does not lose or duplicate events.

Launcher readiness requires:

```text
graphics/fonts/orbitron12_0.png
→ at least 1.5 seconds without another appended log line
```

The recorded launcher time is wrapper process start to the timestamp of the final log activity before the quiet confirmation. The 1.5-second confirmation wait is not added to the result.

After the launcher detector fires, the runner snapshots the logs again and tells the operator to click **Play Starsector**. The first newly appended timestamped Starsector log line becomes the automatic game-start boundary.

Main-menu readiness requires all of:

```text
CampaignGameManager reading a save descriptor
GraphicsLib TextureData reporting VRAM after unload/preload
at least 6 seconds without another appended log line
```

The recorded end boundary is the final appended log activity before the six-second quiet confirmation. The confirmation wait is not added to the result. The longer quiet period prevents a known deferred texture-load burst from being mistaken for main-menu completion.

The result is named:

```text
gameLogStartToMainMenuMs
```

It deliberately does not claim to measure the physical mouse click. It removes operator reaction and focus-switch timing noise while retaining the exact profile's observable game-start and main-menu phases.

The operator still answers whether the automatic notification occurred only after the menu was fully visible and responsive. A negative answer rejects that half of the pilot.

A later repeat-timing campaign must use multiple alternating or randomized samples and report median plus variability.

## Log capture

Before each half, the runner records inode and byte size for every `logs/starsector.log*` file. After clean exit it copies only bytes appended during that half. File identity is matched by inode so log rotation or renaming does not reinclude pre-run bytes; replaced files are treated as new.

The archive retains:

```text
log-snapshot-before.json
log-snapshot-before-play.json
launcher-ready-detection.json
main-menu-ready-detection.json
starsector-log-delta.txt
log-classification.json
```

Bounded classifications include:

```text
normalMapBufferFailures
shaderCreationErrors
musicSourceWarnings
graphicsLibErrors
graphicsLibWarnings
```

The exact retained gameplay archive prerequisite is:

```text
cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
```

## Automated acceptance

Both halves must record:

- the intended texture mode in `run.json`;
- successful launcher and main-menu automatic detector results;
- launcher marker, save-descriptor marker, and GraphicsLib preload marker evidence;
- positive automatically measured intervals;
- clean wrapper and launcher exit;
- no fatal lifecycle evidence;
- at least 30 seconds of attached runtime;
- a nonempty captured Starsector log delta;
- normal launcher and main-menu operator classification;
- operator confirmation that the detector timing matched visible readiness;
- attached wrapper lifetime through exit;
- no visible corruption.

The prepared half additionally requires:

- exact transformation applied once;
- coherent-direct enabled with prepared hits and coherent-direct hits above zero;
- zero prepared fallbacks and internal errors;
- zero active direct bytes, active buffers, and pending buffers at shutdown.

The helper's unit tests cover:

- launcher marker plus quiet detection;
- both main-menu markers plus a deferred line that resets the quiet timer;
- inode-based extraction after log rotation/rename.

## Output

The Desktop archive contains both complete run directories plus:

```text
operator-comparison-identity.txt
operator-contract.json
operator-preparation.json
comparison-result.json
```

`comparison-result.json` records:

```text
samplesPerMode: 1
preliminaryOnly: true
benchmarkAccepted: false
timingMethod: automatic-starsector-log-phase-detection
preparedMinusCompatibilityMs.launcherReady
preparedMinusCompatibilityMs.gameLogStartToMainMenu
preparedMinusCompatibilityLogCounts
logPatternCountsEqual
automaticDetectionVisuallyAccepted
```

## Decision after the pilot

Review the retained logs and detector evidence before authorizing more runs.

- If the nonfatal diagnostics are equivalent and both automatic detections are visually accepted, classify the messages as compatibility-profile baseline for this scope and design the repeated timing campaign.
- If the diagnostics appear only or more often in prepared mode, investigate the prepared carrier path before timing or default enablement.
- If the detector fires early or cannot establish the exact phase, adjust the reviewed marker contract before collecting timing samples.
- If either half fails visually or technically, stop and retain compatibility mode as rollback.

Do not repeat the pilot, enable coherent-direct by default, or claim acceleration from one pair.
