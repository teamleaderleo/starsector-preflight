# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-23

## Current decision

Direct NPOT prepared-pixel bypass is **not yet behaviorally accepted**.

The backing-dimension probe made textures visible, but the launcher was tiled, cropped, and stretched because width and height were assigned from obfuscated setter call order. Merged PR #145 corrects that axis mapping. Exactly one launcher-only axis validation is now authorized. Gameplay and benchmarks remain blocked.

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

## Technical conclusion

The prepared NPOT upload bytes match Starsector's original relevant row-padded layout. Cached pixels can form a coherent source-sized image accepted by Starsector's original converter. Direct-buffer cleanup and lifecycle accounting are clean.

The direct path remained black until the converter's two texture backing-dimension writes were replayed. Restoring both writes made the pixels visible, proving those side effects are required.

The restored build was still visually invalid: background textures repeated at the sides, the center was black, and UI textures were sliced and stretched. The run had 20 hits, 7 coherent-direct NPOT hits, zero fallbacks/errors, complete release accounting, and a clean exit. This isolates the problem to dimension-axis metadata rather than pixels or ownership.

## Corrected dimension mapping

Merged PR #145 uses:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

The transformer maps `PreparedPixel.height()` to the first setter and `PreparedPixel.width()` to the second while preserving the existing invocation, color, upload, cleanup, and exception paths.

The transformation still declines if the exact two-setter reviewed shape is missing or ambiguous. No setter names are guessed or broadly allowlisted.

## Safe default

Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

- power-of-two prepared hits may use the direct lower seam;
- NPOT textures use Starsector's original decode/conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited.

## Validation

PR #145 merged as:

```text
d2333deca1697214231b6392b944ea2992150cae
```

Validated head and workflows:

```text
03829ef2950201ff91182e0b1aa9879fc0d618b8
CI run 579 — full Maven verification
Vanilla adapter gate tests run 422
Texture cache tests run 411
Prepare command tests run 118
```

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-axis-probe.sh
```

Environment overrides:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-direct-axis-probe.sh
```

The runner builds and verifies the checkout, checks exact installed identities, reruns the offline contract, verifies the axis-specific readiness marker and source mapping, enables coherent-direct, checks lifecycle and buffer telemetry, records:

```text
dimensionReplay=reviewed-converter-height-first-width-second
```

and packages the complete run directory on the Desktop.

When the launcher appears:

```text
inspect background, logo, resolution selector, toggles, Play button,
Options, Mods, borders, and vendor logos
→ do not click Play
→ close with the launcher X
```

Report only `normal` or `broken` and upload the generated archive. A duplicate screenshot is optional when the classification is unambiguous.

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
- clean launcher exit;
- operator identity records the reviewed height-first/width-second replay.

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

Do not run more than the single authorized launcher probe, click Play, enter gameplay, treat a normal launcher as final behavioral acceptance, begin benchmarks, weaken identity gates, patch Starsector, swallow original exceptions, or claim acceleration.
