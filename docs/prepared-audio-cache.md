# Prepared audio cache

Prepared audio is a production cache domain for deterministic decoded PCM. It stores no live sound objects, OpenAL state, streams, classloaders, decoder instances, or JVM state.

## Eligibility policy

Every audio resource has one explicit policy:

- `FULLY_DECODED_EFFECT` — eligible for decoded PCM reuse
- `STREAMED` — original streaming behavior always runs
- `UNSUPPORTED` — original behavior always runs

Only `FULLY_DECODED_EFFECT` may create or consume a `PreparedAudio` blob. Unknown loader behavior must use `STREAMED` or `UNSUPPORTED` until real evidence proves full startup decoding.

## Exact identity

A prepared blob is addressed by:

- encoded source SHA-256
- decoder-policy identity SHA-256
- eligibility policy

The decoder-policy identity is supplied by the future exact target adapter. Its canonical inputs must include every decoder behavior input, including:

- Starsector build identity
- decoder class name and class SHA-256
- source archive identity
- method name and descriptor
- classloader class and name
- decoder options and output policy
- PCM normalization rules

A changed input creates a different identity. A producer may add stricter inputs and may never omit a required input.

Logical resource paths are excluded from blob addressing, so identical encoded content under multiple paths can reuse one exact prepared payload. Logical paths, source size, source modification time, and winning-provider policy belong in `PreparedAudioManifest`.

## Blob format

Version 1 uses the `SPAU` format. The authenticated payload contains:

- source SHA-256
- decoder-policy identity SHA-256
- policy
- PCM encoding and bit depth
- byte order
- sample rate
- channel count
- frame and sample counts
- PCM byte count
- PCM SHA-256
- raw PCM bytes

The file also contains a SHA-256 of the complete payload. Reads validate both checksums, every count and length, all enum values, sample/frame arithmetic, and a 64 MiB per-effect PCM ceiling.

## Manifest format

Version 1 uses the `SPAM` format. Each deterministic sorted entry records:

- normalized logical path
- encoded source SHA-256, size, and modification time
- policy
- derived cache key for eligible effects
- exact PCM metadata and PCM SHA-256 for eligible effects

The manifest identity includes the profile fingerprint, Starsector build identity, decoder-policy identity, and every entry field. Manifest allocation, entry count, path bytes, and file size are bounded before serialization.

## Fail-open behavior

`PreparedAudioCache.lookup` returns one of:

- `HIT`
- `MISS`
- `INELIGIBLE`
- `CORRUPT`
- `ERROR`

Every result except `HIT` means the future adapter executes the untouched original loader path. Cache writes are explicit and atomic. Cache failures must never prevent Starsector from launching.

## Current boundary

This cache substrate does not decode OGG files and does not install a live sound-loader hook. Codec-equivalence fixtures, exact real-install signatures, packaged child-JVM adapter tests, and real OFF-versus-ENABLED launches remain required before any startup improvement claim.
