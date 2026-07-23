# Next LLM Implementation Handoff

This is the single living implementation handoff. Archive dated evidence under `docs/evidence/`; do not create parallel handoffs.

## Mission

Perform exactly one replacement two-run main-menu comparison from current `main`.

The two halves are:

```text
compatibility decoded-image path
accepted coherent-direct prepared path
```

This remains one preliminary sample per mode, not a benchmark.

## Merged comparison chain

```text
PR #152 comparison contract and runner
2312bfd265c087e0ddf6ec39d6398b322e9bfc7f

PR #154 automatic Starsector-log readiness detector
434d4f7283e144879c16b735e8004c98d5209787

PR #156 profile-stability and launcher-settling repair
03439b33c99b1fb3abfff9ada88aacc826c33e74

PR #158 installed core-resource discovery
 e37ad314413335f5565f8dadee37525c98b089e4
```

PR #158 passed CI 604. Validation included shell parsing, five core-resource discovery tests, the existing log-detector/profile-guard tests, and full Maven verification.

## Accepted prepared path

The corrected coherent-direct NPOT path passed launcher, main menu, campaign, combat, save, and clean exit on the exact reviewed installation/profile.

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
order: prepared,compatibility
```

Prepared reached the main menu. Compatibility terminated while loading `data/missions/afistfulofcredits/descriptor.json`.

The dialog listed all resource-search roots; the first listed mods are not proven causes. The pair was independently invalid because GraphicsLib generated two normal-map cache files and changed the profile fingerprint before the second half.

Do not use the prepared half's 85-second value as performance evidence.

## Guard-path correction

The first post-repair launch attempt stopped before Starsector ran because the runner assumed:

```text
Contents/Resources/starfarer.res/res/data/missions
```

The exact accepted macOS installation actually stores the core missions under:

```text
Contents/Resources/Java/data/missions
```

The supplied installed `mission_list.csv` contains `afistfulofcredits`.

PR #158 replaced the hardcoded path with a tested discovery helper. It requires exactly one valid root, requires both `mission_list.csv` and the `afistfulofcredits` descriptor, and requires the mission list to name that mission. It retains support for the alternate packaged-resource layout and records resolved paths for hashing.

Evidence:

- [invalid first main-menu comparison](evidence/2026-07-23-prepared-pixel-main-menu-comparison-failure.md)
- [core-resource guard path failure](evidence/2026-07-23-prepared-pixel-core-resource-guard-path-failure.md)
- issue #149

## Replacement-run safeguards

The merged runner now:

- waits for six seconds of launcher log quiet before instructing Play;
- excludes that safety wait from the launcher timing endpoint;
- records the initial profile fingerprint;
- performs deep preparation before and after each half;
- aborts and retains exact per-mod deltas on profile drift;
- discovers and hashes the installed core mission resources;
- verifies those resources remain present and byte-identical;
- packages evidence on failures;
- preserves automatic main-menu detection and `benchmarkAccepted=false`.

## Authorized operator action

Run exactly once:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-main-menu-comparison-pilot.sh
```

Type `COMPARE`. For each randomized half:

```text
press Enter once to launch
→ wait for automatic launcher-ready notification
→ click Play when instructed
→ wait for automatic main-menu-ready notification
→ confirm the menu is visible and responsive
→ exit from main menu
→ close launcher if it reappears
```

Do not enter a campaign or combat. Upload the Desktop archive whether the runner completes both halves or deliberately stops with drift/failure evidence. Do not rerun it.

## Decision after a valid pair

- Equivalent diagnostics, stable profile, unchanged core resources, and accurate detection: design the repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate before timing or default enablement.
- Profile drift or visual/lifecycle/core-resource/detector failure: stop and retain compatibility mode.

Preserve identity gates, cleanup behavior, memory limits, compatibility rollback, opt-in coherent-direct, and the no-acceleration-claim boundary.
