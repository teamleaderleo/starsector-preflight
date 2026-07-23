# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

Direct NPOT prepared-pixel bypass has passed corrected-axis **launcher-level** visual acceptance. It is not yet behaviorally accepted for gameplay and is not enabled by default.

Merged PR #147 provides exactly one controlled gameplay smoke. Use a new campaign, copied save, or other disposable save. Benchmarks remain blocked.

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

## Corrected technical conclusion

The prepared NPOT upload bytes match Starsector's original relevant row-padded layout. Cached pixels form coherent source-sized images accepted by the original converter. The two backing-dimension writes are required.

The validated mapping is:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

With that mapping, coherent source-sized carriers, cached colors, and direct cached NPOT buffers rendered the launcher normally.

Retained launcher evidence:

```text
archiveSha256: 898f99beb8940900a34634d53affc9a97705366fd42faf57a7d2b033bb8bb555
repositoryHead: fd5b240756674ea831aa1caae8edacc425a4c05c
jarSha256: 488f362d59aaad5408c844f9bae4821d4407dbf45b713128f325be10b673b939
outcome: COMPLETED
launcherExitCode: 0
fatalDetected: false
prepared hits: 20
coherent-direct NPOT hits: 7
padded uploads: 7
fallbacks/internal errors: 0
releases: 20
active/pending buffers at shutdown: 0
```

This evidence does not cover the main menu, campaign, combat, saving, or longer-lived texture use.

## Safe default

Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

- power-of-two prepared hits may use the direct lower seam;
- NPOT textures use Starsector's original decode/conversion path;
- compatibility mode remains the accepted rollback;
- no installation or launcher files are edited.

## Merged gameplay-smoke runner

PR #147 merged as:

```text
ab4d1abfacf81c0c27216894b56ccffe3314b0a1
```

The runner checks the corrected source contract and exact installed identities, rebuilds and verifies the repository before launch, prepares exact cache artifacts, and records the accepted launcher archive as a prerequisite.

It requires at least 120 seconds of observed attached wrapper lifetime and asks the operator whether the terminal command remained running until Starsector exited. This rejects a launcher which spawns an untracked game process and returns early.

It writes:

```text
operator-smoke-identity.txt
operator-contract.json
operator-preparation.json
operator-gameplay-result.json
run.json
adapter.json
console.txt
startup.jfr
summary.json
adapter-analysis.json
```

It packages the full run directory on the Desktop even when the operator checklist or automated acceptance fails after a completed run.

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-gameplay-smoke.sh
```

Environment overrides:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-direct-gameplay-smoke.sh
```

The runner requires typing `SMOKE` before launch.

Use a new campaign, copied save, or other disposable save. Do not overwrite a valuable save.

Required route:

```text
1. Confirm the launcher looks normal and click Play Starsector.
2. Reach and inspect the main menu.
3. Start a new campaign or load a disposable save.
4. Inspect campaign UI, portraits, ships, backgrounds, and effects.
5. Enter one combat and inspect ships, weapons, projectiles, effects, and UI.
6. Finish or exit combat normally and save.
7. Return to the main menu and exit Starsector cleanly.
8. If the launcher reappears, close it with its X.
```

After the process exits, answer the runner's yes/no questions accurately. In particular, report whether the terminal command remained attached until Starsector exited.

Stop and exit cleanly if you see black, sliced, repeated, stretched, missing, flipped, or progressively corrupt textures.

## Automated acceptance requirements

- exact transformation applied once;
- coherent-direct enabled with carriers and hits above zero;
- padded uploads equal coherent-direct hits;
- original-converter and NPOT-probe fallback counters zero;
- internal errors zero;
- active direct bytes, active buffers, and pending buffers zero at shutdown;
- no fatal console or log evidence;
- clean process exit;
- at least 120 seconds of observed wrapper lifetime;
- operator identity records `reviewed-converter-height-first-width-second`;
- operator result records normal launcher, main-menu, campaign, and combat visuals;
- save completed;
- command remained attached until game exit;
- clean exit observed;
- no visual corruption observed.

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

## Standing safety rules

Do not repeat the gameplay smoke, use a valuable save, treat launcher acceptance as gameplay acceptance, begin benchmarks, weaken identity gates, patch Starsector, swallow original exceptions, or claim acceleration.
