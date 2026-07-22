# Prepared-pixel NPOT upload padding

Date: 2026-07-22

Status: implementation merged and automated validation complete. One real installed lifecycle pilot remains required.

## Baseline

PR #132 was squash-merged as:

```text
4f3b79c6d7683242d16cb7b34081cd7800f20017
```

That baseline safely declined non-power-of-two prepared payloads, repaired exceptional direct-buffer accounting, captured bounded launcher console evidence, and corrected preparation reporting.

PR #133 was squash-merged as:

```text
68ece81782b54022d58d41634dd88491fca13601
```

## Implemented upload layout

SPFT version 1 remains unchanged on disk. It stores source-sized, bottom-up RGB or RGBA rows and the three derived loader colors.

The prepared-pixel bridge constructs the next-power-of-two OpenGL backing buffer at runtime:

1. calculate the next power of two independently for source width and height;
2. allocate one bounded direct buffer for `uploadWidth * uploadHeight * channels`;
3. copy every existing bottom-up source row without resampling;
4. append zero bytes to the right of each source row;
5. append zero rows above the source image;
6. retain the source dimensions on the carrier so Starsector keeps its original size and texture-coordinate calculations.

The source therefore occupies the lower-left of the backing texture. This is padding, not visual upscaling.

Observed RGB contract:

```text
source: 597 * 373 * 3 = 668043 bytes
upload: 1024 * 512 * 3 = 1572864 bytes
padding:                 904821 bytes
```

## Bounds and fallback

The existing limits apply to the expanded upload buffer:

```text
maximum prepared upload: 32 MiB per texture
maximum active direct memory: 64 MiB
maximum active buffers: 1024
```

SPFT v1 blobs whose stored upload dimensions already differ from their source dimensions remain unsupported and fail open to the original Starsector decode/conversion path. This avoids silently accepting a second, unreviewed cache contract.

The existing exact archive, class, source, method, and loader identity gates remain unchanged. The circuit breaker, original cleanup call, exceptional release guard, and original exception behavior remain unchanged. Compatibility mode remains the accepted rollback path.

## Automated proof

Validated implementation head before the documentation-only tail:

```text
b3b1b59856008ad91609c02ac52eb1986e7bc14b
```

Successful workflows:

```text
CI run 500
Texture cache tests run 349
Vanilla adapter gate tests run 352
Prepare command tests run 84
```

Focused tests prove:

- exact `597x373 RGB -> 1024x512 RGB` remaining-byte count;
- source-byte preservation at the lower-left boundary;
- zero-filled right and upper regions;
- exact `3x5 RGBA -> 4x8 RGBA` full-buffer equality;
- unchanged power-of-two behavior;
- rejection of an unexpected pre-padded SPFT v1 blob contract;
- direct-memory accounting against expanded bytes;
- normal and exceptional release back to zero;
- a packaged Java-agent fixture that reproduces Starsector's power-of-two minimum-buffer check and succeeds only with the padded buffer.

Telemetry adds `paddedUploads`, `paddingBytes`, and `uploadBytesSupplied`. `bytesBypassed` remains source-sized so zero padding does not inflate conversion work avoided; `uploadBytesSupplied` records the expanded bytes handed to the lower seam. The shared cache hit also continues to record source blob bytes.

## Remaining acceptance step

No Starsector installation is available in the implementation environment, so the installed-class checker and real lifecycle route were not rerun here.

PR #133 is merged. One controlled route is now authorized on the reviewed 0.98a-RC8 installation, using only the exact artifacts and paths from the current preparation report:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

Retain the complete run directory and verify clean visuals, expected padding telemetry, zero active direct bytes and buffers at shutdown, and no fatal console or log evidence. Stop after that one run. Repeated benchmarks and acceleration claims remain blocked until its evidence is reviewed.
