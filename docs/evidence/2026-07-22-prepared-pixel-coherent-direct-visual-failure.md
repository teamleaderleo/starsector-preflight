# Prepared-pixel coherent-direct launcher visual failure

Date: 2026-07-22

## Result

The installed launcher-only coherent-direct NPOT diagnostic completed without a process failure, but the launcher again rendered with its large NPOT textures black or absent. Text and some power-of-two UI elements remained visible.

Manual visual classification: **broken**.

This rejects the hypothesis that replacing the historical synthetic `1x1` carrier with a coherent source-sized image was sufficient by itself.

## Retained run identity

```text
repositoryHead: f252e6eff207e2ed7d2b3682396c3450bbccccf8
jarSha256: 69a8a99a64b86049de6181eb2359f94ed510a5d94dd0dc286b69fa897721eab5
archiveSha256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256: d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
diagnosticProperty: -Dpreflight.preparedPixels.coherentDirect=true
```

The complete run was retained as:

```text
prepared-pixel-coherent-direct-probe-20260722-225441
```

## Lifecycle and prepared-pixel evidence

The automated contract behaved exactly as designed:

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
transformationsApplied: 1
containedFailures: 0

carriers: 20
hits: 20
fallbacks: 0
coherentDirectCarriers: 7
coherentDirectHits: 7
paddedUploads: 7
paddingBytes: 1002677
internalErrors: 0
releases: 20
activeDirectBytes: 0
activeBuffers: 0
pendingBuffers: 0
```

Therefore the visual failure was not accompanied by:

- a decode or converter fallback;
- an admission failure;
- a direct-memory leak;
- an internal prepared-pixel error;
- a launcher process failure;
- fatal console or log evidence.

## Corrected conclusion

The coherent cached image, observed NPOT byte layout, cached colors, and bounded direct-buffer lifecycle are not by themselves sufficient to reproduce Starsector's original conversion result at the renderer seam.

The historical synthetic `1x1` carrier may have been invalid, but it was not the sole material cause of the black launcher.

Review of the exact installed converter contract exposed one concrete omitted side effect. Before returning the upload buffer, the original converter:

1. reads the image width and height;
2. computes the next-power-of-two backing dimensions;
3. calls two exact `(I)V` methods on `com/fs/graphics/Object` in width-then-height order;
4. then derives colors and produces the upload buffer.

The prepared wrapper already carried the computed upload width and height, but did not apply those two texture-object setter calls.

That omission can leave the texture object with source-sized or unset backing dimensions while the OpenGL upload uses a larger power-of-two buffer. Renderer UV calculations or related texture state can then address the wrong region even when the uploaded bytes are correct.

## Next diagnostic

The next launcher-only experiment changes exactly one thing:

- retain the coherent carrier, cached colors, and direct padded upload from the failed run;
- additionally replay the two exact reviewed texture-object backing-dimension setters using `PreparedPixel.width()` and `PreparedPixel.height()`;
- preserve all existing identity gates, cleanup, exceptions, limits, and safe default fallback.

Interpretation:

```text
normal launcher
→ missing backing-dimension side effects caused the black rendering;
→ direct NPOT data is viable at the launcher seam with those writes restored.

broken launcher
→ another converter side effect or cached-color difference remains;
→ direct NPOT bypass stays unaccepted.
```

No gameplay lifecycle, benchmark, or acceleration claim is authorized from this evidence.
