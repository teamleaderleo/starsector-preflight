# Prepared-pixel backing-dimension axis failure

Date: 2026-07-22

The coherent-direct backing-dimension probe made NPOT textures visible, but the launcher remained visually invalid: the background repeated at the sides, the center was black, and UI textures were cropped, tiled, and stretched.

Retained run identity:

```text
repositoryHead=b964b6fe1f15f013f1eb9ac8af7b0b0c4ad091d0
jarSha256=26a9a77ee10be220f4621d49cbff9dc59cd2f1c63549576c448138a98697b1f2
archiveSha256=10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256=d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
diagnosticProperty=-Dpreflight.preparedPixels.coherentDirect=true
dimensionReplay=reviewed-converter-two-setter-order
```

The run completed cleanly with 20 prepared hits, 7 coherent-direct NPOT hits, 7 padded uploads, zero fallbacks/internal errors, 20 releases, zero active/pending buffers, and no fatal lifecycle evidence.

Conclusion: backing-dimension writes are required, but assigning width and height from setter call order is wrong. The installed converter flow points show `getWidth`, then `getHeight`, then `Object.Ô00000(I)V`, then `Object.Ó00000(I)V`; the rendered stretching is consistent with the first setter receiving height and the second receiving width. The next repair must preserve this axis mapping and decline safely if the reviewed shape changes.
