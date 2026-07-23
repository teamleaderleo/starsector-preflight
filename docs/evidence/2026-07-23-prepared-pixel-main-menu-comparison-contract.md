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

Compatibility uses the same verified texture-cache context while retaining Starsector's original converter and upload path. This isolates the lower prepared-pixel seam; it is not a raw uninstrumented vanilla run.

The pilot has two goals:

1. capture complete appended Starsector log deltas for both modes and compare known diagnostic counts;
2. collect one preliminary automatically detected launcher-readiness and game-start-to-main-menu timing sample per mode.

A single pair is not a benchmark and cannot support an acceleration claim.

## Invalid first attempt

The first attempt, retained as `2026-07-23-prepared-pixel-main-menu-comparison-failure.md`, is invalid.

The prepared half ran first and reached the main menu. The compatibility half terminated while Starsector attempted to load the core `afistfulofcredits` mission descriptor.

The prepared half also generated two GraphicsLib normal-map cache files, changing the enabled-profile fingerprint before the compatibility half. Therefore the two modes did not run against an identical profile.

The failed pair must not be used for timing, log equivalence, or default enablement. One replacement pair is authorized only after the stability repair is reviewed and merged.

## Exact scope

Each valid half must use the same repository build, Starsector installation, enabled-profile fingerprint, preparation artifacts, wrapper/agent identities, core mission resources, and launcher-settling rule.

Required route:

```text
verify profile and core resources
→ launch wrapper
→ automatic launcher-ready marker plus fixed safety confirmation
→ operator clicks Play Starsector
→ automatic first game-stream log detection
→ automatic main-menu-ready detection
→ operator confirms visible readiness
→ exit from main menu
→ close launcher if it reappears
→ clean wrapper exit
→ verify profile and core resources again
```

Do not enter a campaign or combat.

The order is randomized between:

```text
compatibility,prepared
prepared,compatibility
```

The selected order is retained. `ORDER` may be set explicitly only for troubleshooting before a run; do not repeat the pilot in the opposite order.

## Automatic timing method

The runner tails `logs/starsector.log*`, tracks files by inode, and binds game-start, save-scan, and preload markers to one log stream.

Launcher readiness requires:

```text
graphics/fonts/orbitron12_0.png
→ at least 6 seconds without another appended log line
```

The recorded launcher result is wrapper process start to the final launcher log activity before quiet confirmation. The six-second safety wait is not added to the result.

The longer safety confirmation replaces the first attempt's 1.5-second value. In that attempt, the failed compatibility half began game loading roughly three to four seconds after its final launcher marker, while the successful prepared half had substantially more settling time. This observation does not prove the resource failure's cause, but the replacement pair must remove that order-sensitive difference.

After launcher detection, the runner snapshots the logs and tells the operator to click **Play Starsector**. The first newly appended timestamped line on the game log stream becomes the game-start boundary.

Main-menu readiness requires all of:

```text
CampaignGameManager reading a save descriptor
GraphicsLib TextureData reporting VRAM after unload/preload
at least 6 seconds without another appended line on that stream
```

The recorded endpoint is the final appended game-stream activity before quiet confirmation. The confirmation wait is not added.

The result is named:

```text
gameLogStartToMainMenuMs
```

It does not claim to measure the physical mouse click. The operator still confirms that the notification occurred only after the menu was fully visible and responsive.

## Profile-stability contract

Initial deep preparation records:

```text
operator-preparation-profile.json
expectedProfileFingerprint
```

Before and after every half, the runner performs deep preparation with lookup verification and compares the current census profile fingerprint to the initial fingerprint.

If the profile changes, the runner stops before collecting or accepting another half and retains:

```text
profile-check-before.json
profile-check-after.json
profile-drift-before.json or profile-drift-after.json
```

The drift report includes exact per-mod file/byte/category deltas. It may classify the observed exact `shaderLib` image-only growth shape as a GraphicsLib runtime-cache candidate, but that classification does not make the pair valid. Any fingerprint change rejects the comparison.

## Core-resource stability

Before the pilot, the runner requires and hashes:

```text
starfarer.res/res/data/missions/mission_list.csv
starfarer.res/res/data/missions/afistfulofcredits/descriptor.json
```

Before and after every half, both files must remain present and byte-identical. This does not prove Starsector's internal resource resolver will succeed, but it separates missing/changed files from an in-process resolution failure and retains exact evidence.

The dialog's ordered mod directories are Starsector's resource-search roots. Their order alone does not identify a culpable mod.

## Log capture

Before each half, the runner records inode and byte size for every `logs/starsector.log*` file. After exit or failure it copies only appended or newly created bytes. File identity is matched by inode so rotation or renaming does not reinclude pre-run bytes.

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

## Automated acceptance

Both halves must record:

- the intended texture mode in `run.json`;
- stable profile fingerprints before and after the half;
- unchanged core mission-list and descriptor hashes;
- successful launcher and main-menu detector results;
- launcher marker, save-descriptor marker, and GraphicsLib preload evidence;
- positive automatically measured intervals;
- clean wrapper and launcher exit;
- no fatal lifecycle evidence;
- at least 30 seconds of attached runtime;
- a nonempty log delta;
- normal launcher and main-menu operator classification;
- operator confirmation that detection matched visible readiness;
- no visible corruption.

The prepared half additionally requires:

- exact transformation applied once;
- coherent-direct enabled with prepared and coherent-direct hits above zero;
- zero prepared fallbacks and internal errors;
- zero active direct bytes, active buffers, and pending buffers at shutdown.

## Output

The Desktop archive contains both complete run directories plus:

```text
operator-comparison-identity.txt
operator-contract.json
operator-preparation.json
operator-preparation-profile.json
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
profileStableAcrossBothHalves
```

## Decision after a valid replacement pair

- Equivalent diagnostics, stable profile, unchanged core resources, and accurate detection: classify the messages as exact-profile baseline and design a repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate before timing or default enablement.
- Profile drift: stop; the modes were not compared against the same profile.
- Detector, visual, lifecycle, or core-resource failure: stop and retain compatibility mode as rollback.

Do not repeat the failed harness, disable individual mods based on the resource-search list, enable coherent-direct by default, or claim acceleration from one pair.
