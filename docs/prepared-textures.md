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

## Runtime boundary

The exact-gated vanilla compatibility pilot consumes an active resource index and its matching texture manifest. A validated hit reads an `.spft` file and reconstructs a `BufferedImage` at the reviewed decoded-image seam. Starsector continues through its original texture-object creation, pixel conversion, OpenGL upload, cleanup, and lifetime path.

The pilot verifies the winning provider's current encoded SHA-256 before reading a blob. It also verifies the blob checksum, source hash, transformation, dimensions, channels, and pixel length. Version 1 runtime hits accept identity textures whose original and upload dimensions match. `ALPHA_ADDER`, resized payloads, oversized images, stale indexes, absent entries, changed sources, corrupt blobs, and internal failures execute the original image loader.

The compatibility pilot proves targeting, lookup, invalidation, fallback, corruption quarantine, and image equivalence. The later prepared-pixel stage will bind the lower `BufferedImage -> ByteBuffer` seam so a hit bypasses decode and pixel conversion while preserving original OpenGL work and texture lifetime.
