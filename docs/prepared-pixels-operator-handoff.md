# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

Direct NPOT prepared-pixel bypass is **not yet behaviorally accepted**.

PR #139 is merged and authorizes one final launcher-only coherent-direct diagnostic. Gameplay and benchmarks remain blocked.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct diagnostic contract](evidence/2026-07-22-prepared-pixel-coherent-direct-diagnostic.md)

## Technical conclusion

The first direct NPOT path supplied a complete power-of-two buffer and exited cleanly, but the launcher rendered black.

A later safe probe showed that Starsector's original NPOT buffers used the same relevant `row-pad-source-then-zero-rows` arrangement as the failed direct path. The upper-versus-lower padding diagnosis was wrong.

The coherent-image/original-converter probe then reconstructed real source-sized cached images, bypassed ImageIO, retained Starsector's converter, and rendered normally. This proves the cached pixels and coherent image are acceptable.

The remaining question is whether the black launcher was caused by the historical synthetic `1x1` carrier or by behavior/colors omitted when bypassing the original converter.

## Safe default

Without an explicit diagnostic property:

- power-of-two prepared hits may use the direct lower seam;
- NPOT textures use Starsector's original decode and conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited.

## Merged coherent-direct diagnostic

PR #139 merged as:

```text
23a8ec653d9f07e5df50ff3deab04efdf4104e49
```

The diagnostic is enabled only by:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

For admitted NPOT prepared-cache hits under that property, Preflight combines:

1. the proven real source-sized top-down sRGB image;
2. the observed row-padded power-of-two upload bytes;
3. the cached three derived colors;
4. bounded direct-buffer ownership and cleanup.

It bypasses ImageIO and Starsector's original pixel converter for admitted hits. If both diagnostic properties are set, coherent-direct takes precedence. The runner sets only coherent-direct.

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

Final validated PR head:

```text
df3f3707c5c1d292e0e112399e468ff7fea1b4ec
```

Successful workflows:

```text
CI run 541 — full Maven verification
Vanilla adapter gate tests run 391
Texture cache tests run 383
Prepare command tests run 106
```

The packaged transformed-loader proof verifies direct NPOT bytes, cached colors, decode `0`, conversion `0`, cleanup `1`, and zero buffer ownership after cleanup.

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

Environment overrides:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

The runner builds and verifies the checkout, checks exact installed identities, reruns the offline contract, prepares current artifacts, enables only coherent-direct, checks lifecycle and buffer telemetry, and packages the run directory on the Desktop.

When the launcher appears:

```text
inspect background, logo, resolution selector, toggles, Play button,
Options, Mods, borders, and vendor logos
→ do not click Play
→ close with the launcher X
```

Report only `normal` or `broken` and upload the generated archive. A duplicate screenshot is optional if the launcher is identical to the previously accepted normal launcher.

## Required automated evidence

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

Do not run more than the single authorized launcher probe, click Play, enter gameplay, treat a normal launcher as final behavioral acceptance, begin benchmarks, weaken identity gates, patch Starsector, swallow original exceptions, or claim acceleration.
