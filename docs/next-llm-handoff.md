# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Review and merge PR #144, then perform exactly one installed **launcher-only coherent-direct axis probe** using the repository runner.

Do not click Play, enter gameplay, repeat the probe, benchmark, or make an acceleration claim.

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
- issue #129 — NPOT upload dimensions and prepared-path visual acceptance

## Merged milestones

```text
PR #132 lifecycle, release, and fatal-evidence repair:
4f3b79c6d7683242d16cb7b34081cd7800f20017

PR #133 guessed direct NPOT padding:
68ece81782b54022d58d41634dd88491fca13601

PR #135 NPOT fail-open and original-layout observation:
1fd63567e5834546ab5d617234f84371df9909ea

PR #137 coherent cached image with retained original converter:
fd390ff797e554101cc78ab52516273c1c06fc24

PR #139 coherent carrier plus direct cached NPOT diagnostic:
23a8ec653d9f07e5df50ff3deab04efdf4104e49

PR #141 backing-dimension replay using the incorrect call-order axis assumption:
1b4194977c0fac9a5717d05bec6e858cb2fec419
```

## Established facts

The direct NPOT buffer no longer crashes. Its bytes match Starsector's original row-padded upload layout, and buffer ownership returns to zero cleanly.

A coherent cached image works when Starsector's original converter is retained. A coherent image plus the direct cached buffer still rendered black when dimension writes were omitted.

Replaying both dimension setters made textures visible, proving those writes are required, but the launcher became tiled, cropped, and stretched. The run remained technically clean:

```text
20 prepared hits
7 coherent-direct NPOT hits
7 padded uploads
0 fallbacks
0 internal errors
20 releases
0 active or pending buffers
clean launcher exit
```

## Corrected axis conclusion

The installed converter computes width and height, then invokes two obfuscated texture-object `(I)V` setters. PR #141 assigned axis meaning from setter call order and produced distorted UV/backing behavior.

The reviewed installed flow and live visual result establish the mapping used by PR #144:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

PR #144 changes the transformer's `DimensionSetters` mapping to second=width and first=height. The executable installed-style fixture models the same obfuscated semantics.

The safe default remains unchanged: without the explicit diagnostic property, NPOT textures use Starsector's original decode/conversion path.

## Current validation

The core axis change has already passed:

```text
CI run 568 — full Maven verification
Vanilla adapter gate tests run 412
Texture cache tests run 401
```

Before merge, the final head including readiness, runner, and documentation must pass full Maven verification, vanilla adapter gates, texture cache tests, and preparation tests.

## Authorized operator action after merge

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-axis-probe.sh
```

The runner refuses to launch unless the source contains the corrected axis mapping and preparation reports:

```text
preparedPixelsNextOperatorAction=launcher-only-coherent-direct-axis-probe
dimensionReplay=reviewed-converter-height-first-width-second
```

When the launcher appears:

```text
inspect all launcher visuals
→ do not click Play
→ close with the launcher X
→ report normal or broken
→ upload the generated Desktop archive
```

A normal launcher is not gameplay acceptance. Do not repeat the launcher probe or begin benchmarks.

## Definition of a good handback

Leave the exact merged repository head and JAR SHA-256, retained archive, visual classification, dated evidence, and issue #129/readiness alignment. Preserve identity gates, cleanup behavior, compatibility rollback, and direct-memory limits.
