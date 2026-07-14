# Prepared texture blobs

Preflight Textures converts an encoded image into the exact byte layout consumed by the current texture upload path, then stores that result in a versioned cache blob.

The v1 implementation is deliberately a reference implementation. It favors literal behavioral equivalence over clever pixel access. Later optimized converters must prove byte-for-byte equality against this path.

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

`verify` hashes the current source, performs a fresh reference conversion, and compares the complete prepared result with the blob.

Compare repeated reference conversion with blob reads:

```bash
java -jar preflight.jar texture benchmark example.png example.spft --runs 10
```

The benchmark performs one warmup, reports every sample plus minimum, median, mean, and maximum, and keeps first-build time separate from repeated reads.

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

## Reference conversion behavior

The converter mirrors the current loader loop:

- Decode through Java ImageIO
- Read pixels through `Raster.getPixel`
- Traverse source rows from bottom to top
- Store RGB for opaque images and RGBA for alpha images
- Leave fully transparent output texels zeroed
- Exclude fully transparent texels from color statistics
- Preserve the loader's histogram and derived-color calculations exactly

Literal raster behavior is preserved for grayscale and indexed-color images. This can differ from palette-expanded RGB expectations and gives optimized implementations a precise compatibility target.

`ALPHA_ADDER` is represented in the format and intentionally unsupported by the v1 reference converter until its exact transformation is captured in an equivalence fixture.

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

A future Fast Rendering adapter can read an `.spft` file, create a direct byte buffer, and proceed directly to texture upload. OpenGL object creation, upload, and mipmap generation still occur in the running process.

The runtime path will treat any missing, stale, corrupt, or incompatible blob as a cache miss and use the original image loader.
