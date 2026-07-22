# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

Direct NPOT prepared-pixel bypass is **not yet behaviorally accepted**.

The coherent-direct launcher probe rendered black even though its bytes, colors, lifecycle, and buffer accounting followed the diagnostic contract. Merged PR #141 restores one concrete omitted original-converter side effect: power-of-two backing width and height writes on the texture object.

Exactly one launcher-only validation is authorized. Gameplay and benchmarks remain blocked.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct diagnostic contract](evidence/2026-07-22-prepared-pixel-coherent-direct-diagnostic.md)
- [coherent-direct visual failure](evidence/2026-07-22-prepared-pixel-coherent-direct-visual-failure.md)

## Corrected technical conclusion

The original and prepared NPOT upload buffers use the same relevant row-padded byte arrangement. The cached pixels also form a real source-sized image that Starsector accepts when its original converter is retained.

The coherent-direct run used that coherent image, direct cached bytes, and cached colors, with 7 NPOT hits, zero fallbacks/errors, and zero buffer ownership at shutdown. The launcher still rendered black. The historical synthetic `1x1` carrier was therefore not the sole material cause.

The exact installed converter also writes the computed power-of-two upload width and height to the texture object before returning the buffer. The direct wrapper previously skipped those writes, potentially leaving UV or backing-size state inconsistent with the OpenGL upload.

## Safe default

Without an explicit diagnostic property:

- power-of-two prepared hits may use the direct lower seam;
- NPOT textures use Starsector's original decode and conversion path;
- compatibility mode remains the accepted rollback;
- no installation, launcher, mod, or save files are edited.

## Merged dimension-replay diagnostic

PR #141 squash-merged as:

```text
1b4194977c0fac9a5717d05bec6e858cb2fec419
```

It keeps the existing opt-in property:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

For a successful prepared NPOT result under that property only, the transformed wrapper now:

1. obtains the coherent source-sized carrier and direct padded buffer;
2. calls the first exact reviewed texture-object `(I)V` setter with `PreparedPixel.width()`;
3. calls the second exact reviewed setter with `PreparedPixel.height()`;
4. writes the three cached derived colors;
5. returns the prepared buffer through the existing upload and cleanup path.

The transformation declines if the converter does not contain exactly two distinct texture-object `(I)V` calls in the reviewed shape. No setter names are guessed or broadly allowlisted.

## Validation

Validated PR head:

```text
50907f3d52dc1c22b9a1ab83c66369448ac548ce
```

Successful workflows:

```text
CI run 557 — full Maven verification
Vanilla adapter gate tests run 407
Texture cache tests run 398
Prepare command tests run 111
```

## Result meanings

```text
normal launcher visuals
→ missing backing-dimension writes caused the black rendering;
→ coherent image + cached bytes + cached colors are viable at the launcher seam with those writes restored.

broken launcher visuals
→ another required converter side effect or cached-color mismatch remains;
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

## Authorized operator action

Run exactly once from the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-dimension-probe.sh
```

Environment overrides:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-direct-dimension-probe.sh
```

The runner builds and verifies the checkout, checks exact installed identities, reruns the offline contract, verifies the preparation-readiness marker and dimension-replay source contract, enables only coherent-direct, checks lifecycle/buffer telemetry, records `dimensionReplay=reviewed-converter-two-setter-order`, and packages the run directory on the Desktop.

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
- operator identity records the reviewed two-setter dimension replay.

## Standing safety rules

Do not run more than the single authorized launcher probe, click Play, enter gameplay, treat a normal launcher as final behavioral acceptance, begin benchmarks, weaken identity gates, patch Starsector, swallow original exceptions, or claim acceleration.
