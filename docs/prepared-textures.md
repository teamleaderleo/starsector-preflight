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

A future vanilla or Fast Rendering adapter can read an `.spft` file, create a direct byte buffer, and proceed directly to texture upload. OpenGL object creation, upload, and mipmap generation still occur in the running process.

The runtime path treats any missing, stale, corrupt, or incompatible blob as a cache miss and uses the original image loader.
