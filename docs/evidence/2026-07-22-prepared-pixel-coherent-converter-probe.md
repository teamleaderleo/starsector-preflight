# Prepared-pixel coherent-image/original-converter probe

Date: 2026-07-22

## Result

The single installed launcher-only coherent-image/original-converter probe passed visual and automated acceptance.

The operator reported that the launcher rendered normally and matched the previously accepted normal launcher. A new screenshot was not retained because the visual result was unchanged; the complete run archive was retained and reviewed.

## Exact run identity

```text
repositoryHead: ab208dd2f16aaf521b07431cac86dca20763bf5e
jarSha256: d579f6f16bca0c8a73db91bfa8aee2fe3eddd68ceb5932187bb461d6fd77a9d0
archiveSha256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256: d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
diagnosticProperty: -Dpreflight.preparedPixels.coherentOriginalConvert=true
```

Retained archive supplied by the operator:

```text
prepared-pixel-coherent-converter-probe-20260722-163854.tar.gz
```

## Lifecycle evidence

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
```

No gameplay was started. The launcher was inspected and closed with its X button.

## Prepared-pixel telemetry

```text
carriers: 20
hits: 13
fallbacks: 7
npotProbeFallbacks: 7
coherentCarriers: 7
coherentCarrierBytes: 779083
coherentOriginalConvertFallbacks: 7
coherentOriginalDecodeBypasses: 7
paddedUploads: 0
paddingBytes: 0
layoutObservationErrors: 0
internalErrors: 0
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
```

All seven observed NPOT carriers had raster and sample-model dimensions equal to their source dimensions. Five observations unambiguously matched `row-pad-source-then-zero-rows`. The two `172x32 -> 256x32` play-button textures also matched the equivalent leading-row name because there were no unused vertical rows.

## What this proves

The cached RGB/RGBA source payload can be reconstructed into a real source-sized Java image that Starsector's retained converter accepts visually.

The prior black launcher was therefore not caused by unusable cached pixels or by the observed row-padded upload-byte arrangement alone.

This probe intentionally retained Starsector's original converter, so it does not distinguish between:

1. the historical synthetic `1x1` carrier causing the failure; and
2. additional required behavior inside the original converter or a difference in cached derived colors.

## Next controlled split

The next diagnostic combines the proven coherent source-sized carrier with the direct cached row-padded upload buffer and cached colors. It remains opt-in and launcher-only.

```text
normal launcher
→ the synthetic 1x1 carrier was the material cause.

broken launcher
→ a required converter side effect or cached-color mismatch remains.
```

No direct NPOT behavior, gameplay lifecycle, benchmark, or acceleration claim is accepted by this evidence alone.
