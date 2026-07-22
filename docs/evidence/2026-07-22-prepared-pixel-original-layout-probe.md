# Prepared-pixel original-layout launcher probe

Date: 2026-07-22

Status: launcher-only probe passed visual and lifecycle acceptance. The original NPOT upload buffer layout matched the previously implemented row-padded byte arrangement, so the prior black launcher was not caused by padding placement alone.

## Retained inputs

```text
prepared-pixel-layout-probe-20260722-154030.tar.gz
SHA-256: 56b6278c68339a7b97b3bb956fb176e9a7e04fae1c04c90799372924dea12120
bytes: 393183

Screenshot 2026-07-22 at 11.41.37 PM.png
SHA-256: 22bf68f7a71598c896a0cf396df7953d0ecc84b1bb5d7c19073b0257339c8b02
bytes: 1551408
```

The screenshot showed the expected launcher background, logo, resolution selector, fullscreen and sound toggles, Play button, Options and Mods controls, borders, and vendor logos. The operator did not start Starsector and closed from the launcher.

## Run identity

```text
run directory:
prepared-pixel-layout-probe-20260722-154030

repository head:
60071e12cfc29d691142f272857b37b06233b32c

Preflight JAR SHA-256:
e8f83503a0c4a240c11fa9eb4db72770746d3b4811b6f3cc18c366e17314f103

installed archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708

TextureLoader class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
```

## Lifecycle result

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
bytesExamined: 38813
consoleBytesExamined: 21842
filesExamined: 1
truncated: false
matches: []
problems: []
```

## Prepared-pixel result

```text
transformationsApplied: 1
carriers: 20
directAttempts: 20
hits: 13
fallbacks: 7
npotProbeFallbacks: 7
paddedUploads: 0
paddingBytes: 0
layoutObservationErrors: 0
internalErrors: 0
releases: 13
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
```

Power-of-two prepared hits remained active. Seven NPOT textures used Starsector's original decode and converter path. All original buffers were retained only as bounded comparisons; no texture payload was written into telemetry.

## Layout observations

Five observations had one unambiguous match:

```text
row-pad-source-then-zero-rows
```

Those textures were:

```text
graphics/ui/launcher_bg.jpg       597x373 RGB  -> 1024x512 RGB
graphics/ui/launch_button_bg.png  216x72 RGBA  -> 256x128 RGBA
graphics/ui/buttons/toggleA20x_on.png   20x20 RGBA -> 32x32 RGBA
graphics/ui/buttons/toggleA20x_off.png  20x20 RGBA -> 32x32 RGBA
graphics/ui/buttons/arrow_down.png      20x20 RGBA -> 32x32 RGBA
```

Two `172x32 RGBA -> 256x32 RGBA` play-button textures matched both leading- and trailing-row names because the source height already equaled the upload height. With no unused rows, those candidate names describe the same byte sequence.

Every observation had:

```text
bufferPosition: 0
bufferRemaining: uploadBytes
bufferLimit: uploadBytes
bufferCapacity: uploadBytes
layoutObservationErrors: 0
```

## Corrected conclusion

The probe proves that Starsector's original converter uses the same relevant byte arrangement as the failed direct prepared implementation:

1. source rows remain in their stored bottom-up order;
2. each source row is followed by right-side zero padding to the power-of-two stride;
3. unused upload rows follow the source rows and are zero-filled.

Therefore the prior visual failure cannot be attributed to an upper-versus-lower placement mistake.

The remaining material differences are outside the returned upload bytes. The strongest candidates are:

- the prepared path's synthetic `1x1` `BufferedImage` carrier, whose raster and sample model did not match its overridden width and height; or
- additional required side effects performed by Starsector's original converter beyond returning the buffer and assigning the three reviewed colors.

## Diagnostic decision

PR #137 adds an explicit opt-in coherent-image/original-converter diagnostic. For NPOT cache hits under the property only, it reconstructs a real top-down RGB/RGBA `BufferedImage`, bypasses ImageIO, then executes Starsector's retained original converter and returns that original buffer unchanged.

The diagnostic separates the remaining hypotheses:

```text
normal launcher visuals
→ cached image reconstruction is valid;
→ the direct prepared path skipped required original-converter behavior.

broken launcher visuals
→ the reconstructed cached image is not equivalent to Starsector's decoded image.
```

The default NPOT fail-open path remains unchanged. No direct NPOT prepared buffer, gameplay lifecycle, benchmark, or acceleration claim is authorized by this evidence.
