# Prepared-pixel corrected-axis launcher acceptance

Date: 2026-07-23

## Scope

This evidence records exactly one installed launcher-only coherent-direct prepared-pixel run after the backing-dimension axes were corrected.

It does **not** record gameplay acceptance, benchmark results, or an acceleration claim.

## Retained operator evidence

```text
archive:
prepared-pixel-coherent-direct-axis-probe-20260723-071326.tar.gz

archiveSha256:
898f99beb8940900a34634d53affc9a97705366fd42faf57a7d2b033bb8bb555

repositoryHead:
fd5b240756674ea831aa1caae8edacc425a4c05c

jarSha256:
488f362d59aaad5408c844f9bae4821d4407dbf45b713128f325be10b673b939

installedArchiveSha256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708

installedTextureLoaderClassSha256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50

dimensionReplay:
reviewed-converter-height-first-width-second
```

The operator reported that the launcher looked normal. No duplicate screenshot was required because the visual classification was unambiguous and matched the previously accepted original-converter launcher.

## Automated result

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
consoleCaptureTruncated: false

transformationsApplied: 1
containedFailures: 0
prepared hits: 20
coherent-direct NPOT carriers: 7
coherent-direct NPOT hits: 7
padded uploads: 7
fallbacks: 0
internal errors: 0
releases: 20
active direct bytes at shutdown: 0
active buffers at shutdown: 0
pending buffers at shutdown: 0
```

Byte accounting:

```text
source bytes bypassed: 1,654,859
upload bytes supplied: 2,657,536
padding bytes: 1,002,677
peak active direct bytes: 1,572,864
```

The circuit breaker did not activate. Original-converter NPOT fallbacks and layout-probe fallbacks remained zero.

## Conclusion

The corrected mapping is validated at the launcher seam:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

With coherent source-sized carriers, cached colors, the direct cached upload buffer, and that dimension replay, the launcher rendered normally and exited cleanly.

This accepts the corrected direct NPOT path for **launcher-level visual behavior only**.

## Next authorized action

Exactly one controlled gameplay smoke test is appropriate:

```text
launcher
→ Play Starsector
→ main menu
→ new campaign or disposable save
→ campaign visual inspection
→ one combat visual inspection
→ save
→ return to menu
→ clean exit
```

The test must retain complete automated evidence and explicit operator milestone results. Do not benchmark or enable direct NPOT by default based on launcher acceptance alone.
