# Next LLM Implementation Handoff

This is the single living implementation handoff. Archive dated evidence under `docs/evidence/`; do not create parallel handoffs.

## Mission

After the mutable-cache repair is reviewed and merged, perform exactly one replacement two-run main-menu comparison from current `main`.

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

## Invalid comparison attempts

```text
archiveSha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
order: prepared,compatibility
```

Prepared reached the main menu. Compatibility terminated while loading `data/missions/afistfulofcredits/descriptor.json`.

The dialog listed all resource-search roots; the first listed mods are not proven causes. The pair was independently invalid because GraphicsLib generated two normal-map cache files and changed the profile fingerprint before the second half.

Do not use the prepared half's 85-second value as performance evidence.

The next replacement attempt also ran prepared first and stopped before compatibility:

```text
archiveSha256: 6c3c4f2d1220ce5e11f73649b5c9e1f11b30f3bf115c48fffdccc10733ed4729
repositoryHead: acd2c274d36d17513ecc245200d0fd6cd3adbf1a
order: prepared,compatibility
before fingerprint: 3c1fc13ee4b47a93d36122ee2804070dbacf43523a3d38df5cc531e35e4513fe
after fingerprint: 5bf805bc6c8898c0f3c9eefb8808783cc405938286e00d44a349953046d9b1a1
```

The old aggregate classifier reported only two added GraphicsLib images. Real installation inspection found 27 cache entries written during the prepared half as identical 68-byte, 1×1 PNGs after the 27 normal-map buffer failures. Two were new; the rest replaced existing live cache contents. GraphicsLib's installed source confirms that these cached maps are subsequently loaded and affect texture behavior.

The cache is therefore bounded mutable runtime state, not ignorable growth. Do not use this attempt's timing.

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
- [mutable-cache comparison failure and real-install inspection](evidence/2026-07-23-prepared-pixel-main-menu-mutable-cache-failure.md)
- [core-resource guard path failure](evidence/2026-07-23-prepared-pixel-core-resource-guard-path-failure.md)
- issue #149

## Replacement-run safeguards

The repaired runner:

- waits for six seconds of launcher log quiet before instructing Play;
- excludes that safety wait from the launcher timing endpoint;
- resolves GraphicsLib from the actual census report (`/Applications/Starsector.app/mods` on the reviewed installation);
- separates immutable enabled-profile inputs from the exact `shaderLib/cache` runtime subtree;
- records every mutable filename, size, SHA-256, mode, and nanosecond timestamp plus `shaderlib_cache_hash.data`;
- snapshots the pre-warmed mutable state outside the installation;
- explains the exact reversible installation mutation and requires the typed `RESTORE GRAPHICSLIB CACHE` permission;
- restores and verifies the exact pre-warmed state before each randomized half and after the pair;
- rejects immutable drift, multiple mutable mods, symlinks, nested paths, or unexpected cache files;
- preserves changed-existing-file and generated-file evidence instead of reducing it to aggregate counts;
- performs deep preparation before and after each half;
- discovers and hashes the installed core mission resources;
- verifies those resources remain present and byte-identical;
- packages evidence on failures;
- preserves automatic main-menu detection and `benchmarkAccepted=false`.

Two consecutive real deep preparations left all mod file metadata unchanged and produced the same census fingerprint. The inspection step does not invalidate its own baseline.

## Authorized operator action

Run exactly once:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-main-menu-comparison-pilot.sh
```

First type `RESTORE GRAPHICSLIB CACHE` after reading the exact mutation notice. Then type `COMPARE`. For each randomized half:

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

- Equivalent diagnostics, stable immutable profile, verified equivalent starting cache state, restored final state, unchanged core resources, and accurate detection: design the repeated alternating timing campaign.
- Prepared-only or increased diagnostics: investigate before timing or default enablement.
- Profile drift or visual/lifecycle/core-resource/detector failure: stop and retain compatibility mode.

Preserve identity gates, cleanup behavior, memory limits, compatibility rollback, opt-in coherent-direct, and the no-acceleration-claim boundary.
