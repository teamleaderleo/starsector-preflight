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
2. collect one preliminary launcher-readiness and Play-to-main-menu timing sample per mode.

A single pair is not a benchmark and cannot support an acceleration claim.

## Exact scope

Each half uses the same repository build, Starsector installation, enabled mod profile, preparation artifacts, and wrapper/agent identities.

Required route:

```text
launch wrapper
→ mark launcher fully visible and stable
→ mark immediately before clicking Play Starsector
→ mark main menu fully visible and responsive
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

## Timing method

The runner uses Python's monotonic nanosecond clock. The operator supplies visual readiness markers by pressing Enter.

Recorded intervals:

```text
wrapper process start → launcher visually ready
operator mark immediately before Play click → main menu visually ready
```

These measurements include operator reaction/focus-switch noise. They are useful for validating the comparison harness and estimating effect size, but not for final statistics.

A later repeat-timing campaign must use multiple alternating or randomized samples and report median plus variability.

## Log capture

Before each half, the runner records inode and byte size for every `logs/starsector.log*` file. After clean exit it copies only bytes appended during that half. File identity is matched by inode so log rotation or renaming does not reinclude pre-run bytes; replaced files are treated as new.

The archive retains:

```text
log-snapshot-before.json
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
- clean wrapper and launcher exit;
- no fatal lifecycle evidence;
- at least 30 seconds of attached runtime;
- a nonempty captured Starsector log delta;
- normal launcher and main-menu operator classification;
- attached wrapper lifetime through exit;
- no visible corruption.

The prepared half additionally requires:

- exact transformation applied once;
- coherent-direct enabled with prepared hits and coherent-direct hits above zero;
- zero prepared fallbacks and internal errors;
- zero active direct bytes, active buffers, and pending buffers at shutdown.

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
preparedMinusCompatibilityMs
preparedMinusCompatibilityLogCounts
logPatternCountsEqual
```

## Decision after the pilot

Review the retained logs before authorizing more runs.

- If the nonfatal diagnostics are equivalent, classify them as compatibility-profile baseline for this scope and design the repeated timing campaign.
- If they appear only or more often in prepared mode, investigate the prepared carrier path before timing or default enablement.
- If either half fails visually or technically, stop and retain compatibility mode as rollback.

Do not repeat the pilot, enable coherent-direct by default, or claim acceleration from one pair.
