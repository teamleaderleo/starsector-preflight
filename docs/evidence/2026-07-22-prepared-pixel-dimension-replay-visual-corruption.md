# Prepared-pixel backing-dimension replay: visual corruption

Date: 2026-07-22

## Result

The launcher-only coherent-direct backing-dimension probe did not render black, but launcher textures were visibly cropped, tiled, stretched, and partially displaced. This is a behavioral failure; direct NPOT prepared-pixel bypass remains unaccepted.

## Retained identity

```text
repositoryHead=b964b6fe1f15f013f1eb9ac8af7b0b0c4ad091d0
jarSha256=b034b755d7b9a0ff7fd8131061d4666501bf348589106f4ab123dcc9e1313266
archiveSha256=10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256=d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
dimensionReplay=reviewed-converter-two-setter-order
```

## Automated evidence

```text
outcome=COMPLETED
exitCode=0
launcherExitCode=0
fatalDetected=false
hits=20
coherentDirectHits=7
paddedUploads=7
fallbacks=0
internalErrors=0
releases=20
activeBuffers=0
pendingBuffers=0
activeDirectBytes=0
```

The run therefore remained lifecycle-clean and supplied every prepared buffer through the intended diagnostic path. The visual corruption is not explained by a crash, fallback, unreleased buffer, or partial admission.

## Interpretation

Replaying the two texture-object integer setters was necessary to make the previously black textures visible, but the current implementation inferred axis meaning from setter call order. The retained bytecode-shape evidence shows width and height calculations precede both obfuscated setters; call order alone does not prove which setter consumes which calculated axis.

The next repair must derive each setter's axis from the original converter's local-variable data flow and preserve its original invocation order. It must decline transformation when that mapping is not exact. Do not run another launcher probe against the call-order inference.
