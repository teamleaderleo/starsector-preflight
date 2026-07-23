# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

The corrected coherent-direct NPOT prepared-pixel path passed launcher and gameplay visual/lifecycle acceptance for the exact reviewed macOS/Starsector 0.98a-RC8 installation and profile.

It remains opt-in and is not enabled by default. The bounded mutable-cache repair is reviewed, merged, and green. Exactly one replacement pair is authorized. It is not a benchmark.

## Merged comparison work

```text
PR #152 — comparison runner and contract
PR #154 — automatic Starsector-log readiness detection
PR #156 — profile stability and launcher-settling repair
PR #158 — installed core mission resource discovery
PR #160 — bounded GraphicsLib mutable-cache control
```

PR #160 merged as:

```text
a4364366244af67183992bd420c46c5ef5d6ef72
```

Its final PR head passed CI, and the merge commit passed main CI run 30007526191. Validation included shell parsing, nine focused profile/cache tests, the existing detector/core-resource tests, and full Maven verification.

## Accepted implementation

The required texture backing-dimension mapping is:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

Retained gameplay acceptance:

```text
archiveSha256: cbc9f5884d89f69e93f6b0ca882c911fdb0cb43397932b77b191920ded0a11bf
runtimeSeconds: 624.640
prepared hits: 5015
coherent-direct NPOT hits: 4450
fallbacks/internal errors: 0
active direct bytes at shutdown: 0
active/pending buffers at shutdown: 0
operatorAccepted: true
automatedAccepted: true
```

## Invalid first comparison

The first comparison archive is invalid:

```text
archiveSha256: 2530de69d2251319422b3224a0d8430e5537f77a667fd69a9a726996785fdd08
order: prepared,compatibility
```

Prepared reached the main menu. Compatibility later stopped loading `data/missions/afistfulofcredits/descriptor.json`.

The dialog printed every resource-search directory. AI Tweaks, Arma Armatura, and the first other entries are not proven causes merely because they appeared first.

The pair was already invalid because GraphicsLib generated two normal-map cache files and changed the enabled-profile fingerprint before the second half. Do not use the prepared half's 85-second result as performance evidence.

## Invalid mutable-cache attempt

The next replacement archive also stopped after prepared ran first:

```text
archiveSha256: 6c3c4f2d1220ce5e11f73649b5c9e1f11b30f3bf115c48fffdccc10733ed4729
before fingerprint: 3c1fc13ee4b47a93d36122ee2804070dbacf43523a3d38df5cc531e35e4513fe
after fingerprint: 5bf805bc6c8898c0f3c9eefb8808783cc405938286e00d44a349953046d9b1a1
```

Your visual answers all passed. The old guard reported two added GraphicsLib images, but the real cache inspection found 27 files written during the prepared half as identical 68-byte, 1×1 PNGs after the 27 normal-map buffer failures. Most replaced existing cache files. GraphicsLib loads this cache during later starts, so it cannot simply be ignored or broadly whitelisted.

Do not use this attempt's 90-second value.

## Core-resource guard correction

A later attempt stopped before launching Starsector because the runner checked the wrong bundle path:

```text
Contents/Resources/starfarer.res/res/data/missions
```

Your exact installation stores the core mission files at:

```text
Contents/Resources/Java/data/missions
```

The installed `mission_list.csv` contains `afistfulofcredits`.

The runner now discovers the resource root. It requires exactly one valid supported root, the mission list, the `afistfulofcredits` descriptor, and the mission entry. It then hashes the resolved files and verifies they remain unchanged.

That path-guard stop occurred before the launcher or game ran. It is not mod, texture, or timing evidence.

## Replacement-run safeguards

The repaired runner:

- waits for six seconds of launcher log quiet before telling you to click Play;
- excludes the safety wait from the measured launcher endpoint;
- treats enabled-mod configuration, jars, scripts, data, and source assets as immutable;
- treats only the exact GraphicsLib generated-normal cache and its hash-control file as bounded mutable runtime state;
- records exact cache filenames, sizes, hashes, modes, and timestamps;
- makes a read-only pre-warmed snapshot outside the installation;
- shows the exact cache paths and mutations before asking for permission;
- restores and verifies the identical pre-warmed state before both randomized halves and once at the end;
- refuses to touch the cache if it finds symlinks, subdirectories, unexpected files, multiple mutable mods, or immutable drift;
- performs deep preparation before and after each half;
- discovers and hashes the installed core mission files;
- verifies those files remain present and byte-identical;
- preserves automatic main-menu detection and `benchmarkAccepted=false`.

The restore is narrowly scoped and reversible. It may remove newly generated reviewed `*_normal.png` cache entries, replace changed/missing reviewed cache entries from the snapshot, restore their modes/timestamps, and restore `shaderlib_cache_hash.data`. It does not modify any other mod, save, core resource, or game file. If the cache shape is unexpected, it stops and retains the recovery snapshot instead of mutating it.

## Safe default

Outside this explicitly authorized comparison restore, Preflight does not edit the installation. Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

NPOT textures use Starsector's original decode/conversion path. Compatibility remains the rollback. Preflight does not edit the installation, launcher, mods, or saves.

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-main-menu-comparison-pilot.sh
```

After the build and preparation complete, read the exact cache mutation notice and type:

```text
RESTORE GRAPHICSLIB CACHE
```

Then type:

```text
COMPARE
```

For each randomized half:

1. Press Enter once to launch.
2. Wait for the automatic launcher-ready notification.
3. Click **Play Starsector** when instructed; do not press Enter for timing.
4. Wait for the automatic main-menu-ready notification.
5. Confirm the menu is fully visible and responsive.
6. Exit Starsector from the main menu.
7. Close the launcher if it reappears.
8. Answer the visual, detector, attachment, and clean-exit questions accurately.

Do not load a campaign or enter combat. Upload the Desktop `.tar.gz` whether the runner completes both halves or deliberately stops with retained drift/failure evidence. Do not run it again.

## Interpretation boundary

```text
samplesPerMode=1
preliminaryOnly=true
benchmarkAccepted=false
timingMethod=automatic-starsector-log-phase-detection
```

A valid pair also requires stable immutable inputs, exact equivalent cache state at the start of both halves, successful final cache restoration, unchanged core mission resources, accurate readiness detection, and clean lifecycle/visuals in both modes.

Do not enable coherent-direct by default or claim acceleration from one pair.
