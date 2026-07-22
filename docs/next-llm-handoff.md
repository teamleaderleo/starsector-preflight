# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Review and merge PR #137, then perform and review exactly one real installed **launcher-only coherent-image/original-converter probe** using the repository runner.

Do not re-enable direct NPOT prepared buffers, run a prepared-pixel gameplay lifecycle, begin repeated measurement, or make acceleration claims.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- issue #129 — NPOT upload dimensions and prepared-path visual acceptance
- PR #137 — coherent cached image with retained original converter

## State as of 2026-07-22

Merged milestones:

```text
PR #132 lifecycle, release, and fatal-evidence repair:
4f3b79c6d7683242d16cb7b34081cd7800f20017

PR #133 guessed direct NPOT padding:
68ece81782b54022d58d41634dd88491fca13601

PR #135 NPOT fail-open and original-layout observation:
1fd63567e5834546ab5d617234f84371df9909ea

PR #136 one-shot original-layout runner:
60071e12cfc29d691142f272857b37b06233b32c
```

The guessed direct upload no longer crashed but rendered the launcher incorrectly. The later safe probe used Starsector's original NPOT conversion path and rendered normally.

Retained safe-probe telemetry:

```text
carriers: 20
hits: 13
fallbacks: 7
npotProbeFallbacks: 7
paddedUploads: 0
paddingBytes: 0
layoutObservationErrors: 0
internalErrors: 0
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
```

All seven observed original NPOT buffers matched `row-pad-source-then-zero-rows`. The two `172x32 -> 256x32` play-button observations also matched the leading-row name because no unused vertical rows existed; those names represented identical bytes in that case.

The prior lower-versus-upper padding diagnosis is therefore disproved. The failed direct path supplied the relevant bytes in the same arrangement as Starsector's original converter.

## Remaining hypotheses

The material difference is outside the returned upload bytes:

1. the prepared path's historical carrier was a real `1x1` raster and sample model that only overrode no-argument width and height; or
2. Starsector's original converter performs required side effects beyond returning the buffer and assigning the three reviewed colors.

A blind direct-buffer retry cannot distinguish those cases.

## PR #137 diagnostic

PR #137 adds an explicit opt-in system property:

```text
-Dpreflight.preparedPixels.coherentOriginalConvert=true
```

For NPOT prepared-cache hits under that property only, the transformed path:

1. reconstructs a dimensionally coherent top-down sRGB `BufferedImage` from the cached bottom-up RGB/RGBA source payload;
2. bypasses the original ImageIO decode;
3. executes Starsector's retained original converter on that coherent cached image;
4. returns the original converter's exact buffer;
5. preserves every original converter side effect, cleanup call, upload, and exception;
6. records bounded original-buffer and coherent-carrier metadata.

Without the property, current safe NPOT behavior is unchanged: Starsector performs both original decode and conversion.

The diagnostic does **not** supply a direct padded prepared buffer. `paddedUploads` and `paddingBytes` remain zero.

New telemetry:

```text
coherentOriginalConvertEnabled
coherentCarriers
coherentCarrierBytes
coherentOriginalConvertFallbacks
coherentOriginalDecodeBypasses
```

Each layout observation also records:

```text
coherentOriginalConvert
carrierRasterWidth / carrierRasterHeight
carrierSampleModelWidth / carrierSampleModelHeight
carrierColorComponents
carrierHasAlpha
```

## Diagnostic interpretation

```text
normal launcher visuals
→ the cached coherent image is acceptable to Starsector;
→ the failed direct path skipped additional required original-converter behavior.

broken launcher visuals
→ the reconstructed cached image is not equivalent to Starsector's decoded image;
→ inspect image type, color model, raster organization, properties, or source-pixel reconstruction.
```

Do not infer more than this single split from the result.

## Automated validation

Validated code head before readiness, runner, and evidence alignment:

```text
b5af3ef0982583c3563113cd0a882b3fb48aac31
```

Successful workflows:

```text
CI run 524 — full Maven verification
Vanilla adapter gate tests run 374
Texture cache tests run 368
```

The final PR head, including readiness and documentation, must pass all newly triggered affected workflows before merge. Record those final results in the PR.

## Exact identities

```text
TextureLoader class:
com/fs/graphics/TextureLoader

class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50

archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
```

Keep these identities exact. Automatic allowlist generation remains disabled.

## Operator action after merge

Use a newly built merged JAR and exact artifacts from a current successful preparation report:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-converter-probe.sh
```

The runner verifies the build, installed identities, offline contract, preparation artifacts, diagnostic property, original-converter observations, lifecycle result, and shutdown accounting.

When the launcher appears:

```text
inspect all launcher visuals
→ take a screenshot
→ do not click Play
→ close with the launcher X
```

Retain and upload the generated archive and screenshot. Stop after that one run.

## Expected evidence

- normal or clearly classified broken launcher visuals;
- exact transformation applied once;
- diagnostic property enabled;
- coherent carriers and coherent carrier bytes above zero;
- NPOT fallbacks and coherent-original-converter fallbacks above zero;
- ImageIO decode bypasses above zero while direct conversion bypasses remain separate;
- original layout observations present with raster/sample-model dimensions equal to source dimensions;
- direct padded uploads equal zero;
- observation and internal errors zero;
- active prepared buffers/direct bytes/pending buffers zero at shutdown;
- no fatal console or log evidence;
- clean launcher exit.

## Definition of a good handback

Leave:

1. PR #137 merged or exact review findings recorded;
2. final workflow results and validated commit SHA;
3. exact probe command and merged JAR SHA-256;
4. complete retained coherent-converter run directory and screenshot;
5. a dated evidence document classifying the diagnostic result;
6. issue #129 updated;
7. readiness and operator handoff aligned;
8. no direct NPOT retry, gameplay lifecycle, repeated benchmark, or acceleration claim.
