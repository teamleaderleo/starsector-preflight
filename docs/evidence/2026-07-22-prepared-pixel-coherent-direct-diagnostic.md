# Prepared-pixel coherent-direct NPOT diagnostic

Date: 2026-07-22

## Purpose

This diagnostic performs the remaining controlled split after the successful coherent-image/original-converter probe.

It is enabled only by:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

The merged safe default is unchanged when the property is absent.

## Diagnostic path

For admitted NPOT prepared-cache hits under the property only, Preflight:

1. reconstructs the proven source-sized top-down sRGB `BufferedImage` from cached bottom-up RGB/RGBA pixels;
2. supplies the exact observed next-power-of-two row-padded upload buffer;
3. assigns the three cached derived colors;
4. bypasses the original ImageIO decode and original pixel converter;
5. retains the existing bounded direct-buffer ownership, original upload caller, cleanup wrapper, and exception behavior.

The property takes precedence over `preflight.preparedPixels.coherentOriginalConvert` if both are present. This prevents an ambiguous mixed diagnostic.

## Byte contract

For every source row, the upload buffer contains:

```text
source row bytes
→ zero bytes through the power-of-two row width
```

After all source rows, zero rows fill the remaining power-of-two height. This matches the retained original-buffer observations.

No resampling or interpolation occurs.

## Bounded behavior

The existing limits remain:

```text
maximum direct bytes per texture: 32 MiB
maximum active prepared direct bytes: 64 MiB
maximum active and pending buffers: 1,024
```

Exact archive, class, method, source, and classloader identity gates remain unchanged. Compatibility mode remains the accepted rollback.

## Telemetry

The diagnostic records:

```text
coherentDirectEnabled
coherentDirectCarriers
coherentDirectHits
paddedUploads
paddingBytes
bytesBypassed
uploadBytesSupplied
activeBuffers
activeDirectBytes
pendingBuffers
```

A clean launcher-only run must have coherent-direct hits and padding above zero, no NPOT original-path fallbacks, no internal errors, and zero buffer ownership at shutdown.

## Interpretation

```text
normal launcher visuals
→ the historical synthetic 1x1 carrier was the material cause of the black launcher;
→ the coherent carrier, cached direct bytes, and cached colors are jointly viable at the launcher seam.

broken launcher visuals
→ the direct bypass still omits required original-converter behavior or cached derived colors differ;
→ do not enable direct NPOT bypass.
```

The result is launcher evidence only. A normal launcher does not authorize gameplay, production acceptance, repeated runs, benchmarks, or acceleration claims.

## Automated validation

Validated implementation head before evidence-only commits:

```text
61a6b8c86b49f08745c5a9f75ecdb45a4719e3c0
```

Successful workflows:

```text
CI run 537 — full Maven verification
Vanilla adapter gate tests run 387
Texture cache tests run 379
Prepare command tests run 102
```

The packaged child-JVM proof verifies decode `0`, conversion `0`, cleanup `1`, the exact padded bytes, cached colors, and zero direct-buffer ownership after cleanup.

## Authorized operator action after merge

Run exactly once from current `main`:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

Inspect the launcher, do not click Play, close it with the launcher X, report `normal` or `broken`, and retain the generated archive.
