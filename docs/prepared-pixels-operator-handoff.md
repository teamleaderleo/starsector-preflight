# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

Direct NPOT prepared-pixel bypass is **not yet behaviorally accepted**.

The crash, byte-layout question, and coherent-image reconstruction have been resolved far enough to run one final launcher-only diagnostic. Gameplay and benchmarks remain blocked.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct diagnostic contract](evidence/2026-07-22-prepared-pixel-coherent-direct-diagnostic.md)

## Corrected technical conclusion

The first direct NPOT path supplied the required power-of-two buffer and exited cleanly, but the launcher rendered black.

A later safe probe observed Starsector's original NPOT buffers. Five textures unambiguously matched:

```text
row-pad-source-then-zero-rows
```

Two `172x32 -> 256x32` play-button textures also matched the equivalent leading-row label because no unused vertical rows existed.

The old direct path therefore used the same relevant byte arrangement as the original converter. The earlier upper-versus-lower padding diagnosis was wrong.

The next coherent-image/original-converter probe reconstructed real source-sized cached images, bypassed ImageIO, retained Starsector's converter, and rendered normally. It completed with no fatal evidence, no internal errors, and zero prepared buffer ownership at shutdown.

That proves the cached pixels and coherent source-sized image are acceptable. The remaining question is whether the black launcher was caused by the historical synthetic `1x1` carrier or by behavior/colors omitted when bypassing the original converter.

## Safe default

Without an explicit diagnostic property, current behavior remains:

- power-of-two prepared hits may use the direct lower seam;
- NPOT textures use Starsector's original decode and conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited.

## Coherent-direct diagnostic

The diagnostic is enabled only by:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

For admitted NPOT prepared-cache hits under that property, Preflight combines:

1. the proven real source-sized top-down sRGB image;
2. the observed row-padded power-of-two upload bytes;
3. the cached three derived colors;
4. the existing bounded direct-buffer ownership and cleanup path.

It bypasses ImageIO and Starsector's original pixel converter for admitted hits.

If both diagnostic properties are set, coherent-direct takes precedence. The runner sets only coherent-direct.

## Result meanings

```text
normal launcher visuals
→ the synthetic 1x1 carrier was the material cause of the prior black launcher;
→ coherent image + direct cached bytes + cached colors are viable at the launcher seam.

broken launcher visuals
→ a required original-converter side effect or cached-color mismatch remains;
→ direct NPOT bypass stays disabled.
```

A normal launcher is not gameplay acceptance.

## Preserved boundaries

The diagnostic retains:

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

## Automated validation

Validated implementation/readiness/runner head before evidence-only commits:

```text
61a6b8c86b49f08745c5a9f75ecdb45a4719e3c0
```

Successful workflows:

```text
CI run 537 — full Maven verification
Vanilla adapter gate tests run 387
Texture cache tests run 379
Prepare command tests run 102
```

The packaged transformed-loader proof verifies direct NPOT bytes, cached colors, decode `0`, conversion `0`, cleanup `1`, and zero buffer ownership after cleanup.

## Authorized operator action after PR #139 merges

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

Environment overrides remain available:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

The runner builds and verifies the checkout, checks exact installed identities, reruns the offline contract, prepares current artifacts, enables only the coherent-direct property, checks lifecycle and buffer telemetry, and packages the run directory on the Desktop.

When the launcher appears:

```text
inspect background, logo, resolution selector, toggles, Play button,
Options, Mods, borders, and vendor logos
→ do not click Play
→ close with the launcher X
```

Report only `normal` or `broken` and upload the generated archive. A duplicate screenshot is optional if the launcher is identical to the previously accepted normal launcher.

## Required automated evidence

The run must show:

- exact transformation applied once;
- coherent-direct property enabled;
- coherent-direct carriers and hits above zero;
- padded uploads equal coherent-direct hits;
- padding bytes above zero;
- original NPOT fallback and coherent-original-converter counters zero;
- internal errors zero;
- active direct bytes, active buffers, and pending buffers zero at shutdown;
- no fatal console/log evidence;
- clean launcher exit.

## Standing safety rules

Do not:

- run more than the single authorized launcher probe;
- click Play or enter gameplay;
- treat a normal launcher as final behavioral acceptance;
- begin benchmarks;
- weaken identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, logs, observations, buffers, or worker pools;
- claim acceleration before a separate accepted measurement campaign;
- delete the failed pilot or retained probe evidence.
