# Prepared texture blobs

Preflight Textures converts an encoded image into the exact byte layout consumed by the current texture upload path, then stores that result in a versioned cache blob.

The literal reference implementation remains the compatibility authority. Production cache generation now uses a row-bulk converter only after proving complete `PreparedTexture` equality against that reference path.

## Commands

Prepare one image:

```bash
java -jar preflight.jar texture prepare graphics/ships/example.png
```

The default content-addressed output is:

```text
~/.starsector-preflight/textures/SOURCE_SHA256-identity.spft
```

Choose an explicit output:

```bash
java -jar preflight.jar texture prepare example.png --output example.spft
```

Inspect and verify:

```bash
java -jar preflight.jar texture inspect example.spft
java -jar preflight.jar texture verify example.png example.spft
```

`verify` hashes the current source, performs a fresh literal reference conversion, and compares the complete prepared result with the blob. It does not reuse the optimized preparation path.

Compare literal conversion, bulk-row conversion, and blob reads:

```bash
java -jar preflight.jar texture benchmark example.png example.spft --runs 10
```

The benchmark performs untimed validation passes, alternates literal and bulk measurement order, reports every sample plus minimum, median, mean, and maximum, and keeps blob reads separate. CI checks correctness rather than enforcing timing ratios.

Build a profile-wide cache from an existing or discovered resource index:

```bash
java -jar preflight.jar texture build --game "/path/to/Starsector.app"
java -jar preflight.jar texture build --index profile.spfi --cache-dir cache
```

### Deterministic subset builds

For profiling and staged rollouts, pass a newline-delimited list of logical resource paths:

```bash
java -jar preflight.jar texture build \
  --game "/path/to/Starsector.app" \
  --cache-dir cache \
  --paths-file startup-images.txt
```

The selection file accepts blank lines and `#` comments. Every other line must be a relative logical resource path such as:

```text
graphics/ships/example.png
graphics/icons/example.jpg
cache/generated_normal.png
```

Subset preparation:

- normalizes, lowercases, deduplicates, and sorts paths with the same resource-index rules
- rejects absolute and traversal paths
- reports missing and non-image paths without discarding valid selections
- derives a deterministic subset fingerprint from the full-profile fingerprint and selected winning paths
- writes a matching subset `.spfi` index and `.spfm` manifest
- reuses the same content-addressed blob store as full builds
- never overwrites the full-profile index or manifest

The JSON command result reports `sourceIndex`, the active subset `index`, the subset `manifest`, selection counts, and diagnostics. A runtime adapter must use the reported subset index and subset manifest together.

## Prepared payload

A prepared texture contains:

- Source SHA-256
- Transformation identifier
- Original image dimensions
- Upload dimensions
- Three or four channels
- Bottom-up RGB or RGBA upload bytes
- Three packed `RRGGBBAA` color values used by the loader

The raw pixel payload remains uncompressed in version 1. This creates a clean baseline for LZ4, low-level Zstandard, pack-file, and memory-mapping experiments.

## Literal reference behavior

The reference converter mirrors the current loader loop:

- Decode through Java ImageIO
- Read pixels through `Raster.getPixel`
- Traverse source rows from bottom to top
- Store RGB for opaque images and RGBA for alpha images
- Leave fully transparent output texels zeroed
- Exclude fully transparent texels from color statistics
- Preserve float accumulation order, histograms, and derived-color calculations exactly

Literal raster behavior is preserved for grayscale and indexed-color images. This can differ from palette-expanded RGB expectations and gives optimized implementations a precise compatibility target.

`ALPHA_ADDER` is represented in the format and intentionally unsupported until its exact transformation is captured in an equivalence fixture.

## Bulk-row conversion

The optimized converter calls `Raster.getPixels()` once per source row instead of `Raster.getPixel()` once per pixel. It then executes the same ordered per-pixel arithmetic as the reference implementation.

It preserves:

- Source-row reversal and output byte order
- Raw raster band indexing, including grayscale and indexed-alpha quirks
- Persistent missing-band zero behavior
- Transparent-texel zeroing and exclusion from statistics
- Float sums and histogram update order
- All three derived loader colors
- Source SHA-256 and binary blob bytes

Translated subimage rasters are accepted when their bounds contain the image's logical `0..width` and `0..height` rectangle. Unsupported or unusual raster layouts fall back to the literal reference converter.

The equivalence suite covers every standard JDK `BufferedImage` type, premultiplied alpha, BGR/ABGR layouts, ushort RGB and grayscale, binary/indexed images, custom grayscale-plus-alpha and indexed-alpha models, odd dimensions, transparent pixels, translated subimages, and file-backed PNG/JPEG decoding. Deterministic randomized fixtures compare the complete immutable `PreparedTexture` value.

Profile-wide cache generation and `texture prepare` use the bulk-row path. `texture verify` always uses the literal reference path.

## Blob format

Version 1 uses:

```text
magic:      SPFT
version:    32-bit integer
length:     32-bit payload length
payload:    metadata plus raw pixels
checksum:   SHA-256 of the payload
```

The writer uses a sibling temporary file and atomic replacement where supported. The reader validates size bounds, payload length, checksum, transformation, codec, dimensions, channel count, expected pixel length, and trailing data before constructing the texture.

## Runtime consumers

Both live consumers use the exact-reviewed `TextureLoader` class, archive, method, source, and loader identity. A launch selects one mode:

- `compatibility` reconstructs a `BufferedImage` at the private decoded-image seam. Starsector retains its original pixel conversion, OpenGL upload, cleanup, and texture lifetime.
- `prepared-pixels` carries the verified SPFT payload to the lower `BufferedImage -> ByteBuffer` seam. A hit supplies bottom-up upload bytes and all three stored derived colors, bypassing ImageIO decode, raster traversal, vertical reversal, RGB/RGBA conversion, transparent-texel normalization, and color calculation. Starsector retains its original texture allocation, OpenGL upload, cleanup, flags, filtering, mipmaps, and texture lifetime.

Both version-2 plans preserve the original `com.fs.graphics.L.class(String)` asynchronous preloader handoff before any Preflight lookup. A preloaded image always wins. Preflight is consulted only on the original direct-decode branch after that handoff returns `null`; an absent or ambiguous handoff leaves the class untouched.

Compatibility mode may also use `run --adapter --texture-auto` to resolve the already-prepared manifest and index for the exact current installed profile. This convenience mode remains explicit and read-only; it does not support `prepared-pixels` and fails before launch when the cache is absent or stale.

Compatibility-v2 matches the exact installed class bytes. Prepared-pixels-v2 currently declines those bytes at its color-sink matcher, so the lower path remains fail-closed despite passing repository-owned synthetic tests. It must not be treated as live-ready until its fixture models the installed `TextureLoader` fields and subsequent texture-object setter calls.

Launch the lower consumer with explicit artifacts:

```bash
java -jar preflight.jar run \
  --game "/path/to/Starsector.app" \
  --adapter \
  --texture-mode prepared-pixels \
  --texture-cache-dir "/path/to/cache" \
  --texture-manifest "/path/to/cache/manifests/<fingerprint>.spfm" \
  --texture-index "/path/to/cache/indexes/<fingerprint>.spfi"
```

The prepared-pixel rewrite additionally requires the reviewed conversion pattern: one raster-reading conversion method, exactly three distinct `java.awt.Color` writes on the texture object, and the reviewed static ByteBuffer cleanup method. An ambiguous pattern leaves the class untouched.

Every lookup verifies the current winning source SHA-256, manifest/index fingerprint, blob checksum, source identity, transformation, dimensions, channels, and pixel length. The current payload format accepts identity textures whose original and upload dimensions match. `ALPHA_ADDER`, resized payloads, oversized images, stale indexes, absent entries, changed sources, corrupt blobs, direct-memory pressure, and bridge failures execute the retained original direct-decode and conversion paths once.

Prepared direct-buffer ownership is bounded to 32 MiB per texture, 64 MiB active bytes, and 1,024 active buffers. The existing Starsector cleanup method always runs. Preflight releases its identity-tracked accounting in a `finally` path after that original cleanup call.
