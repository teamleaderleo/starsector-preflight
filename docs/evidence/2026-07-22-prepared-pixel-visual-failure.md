# Prepared-pixel NPOT visual failure

Date: 2026-07-22

Status: behavioral acceptance failed. The repaired buffer length prevented the prior crash, but the guessed NPOT padding layout rendered launcher textures incorrectly.

## Retained inputs

The operator retained and supplied:

```text
prepared-pixel-pilot-20260722-143238-visual-failure.tar.gz
SHA-256: 8e5b7c107c2d8414e7a4304002d27bcf50c5a5181e7774da419e68a1f247b142
bytes: 371451

launcher screenshot
SHA-256: ecdf1c632e575c5f1fbaeaa307bee8065cad7126d1ee89a4ced9c9f1cd853d75
bytes: 162142
```

The screenshot showed a mostly black launcher panel with only portions of the resolution, fullscreen, sound, Options, and Mods text visible. The expected launcher background and controls were missing or incorrectly sampled. The operator stopped without continuing to campaign gameplay.

## Run identity

Retained run directory:

```text
prepared-pixel-pilot-20260722-143238
```

Run interval:

```text
started: 2026-07-22T14:33:00.919829Z
ended:   2026-07-22T14:33:37.298979Z
```

Launcher:

```text
/Applications/Starsector.app/Contents/MacOS/starsector_mac.sh
```

The exact installed target remained the reviewed Starsector 0.98a-RC8 identity:

```text
archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708

TextureLoader class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
```

The tested Preflight JAR SHA-256 was:

```text
90317205d9bebab81a60247363928bf3df043ceda24073b994583a1d0c56b136
```

## Lifecycle evidence

The process itself completed cleanly:

```text
outcome: COMPLETED
exitCode: 0
launcherExitCode: 0
fatalDetected: false
postprocessingFailures: []
```

The bounded lifecycle inspection examined one log plus child-console evidence:

```text
bytesExamined: 38543
consoleBytesExamined: 21688
filesExamined: 1
truncated: false
matches: []
problems: []
```

This is therefore a visual behavioral failure, not a launcher crash, fatal-log false negative, or postprocessing failure.

## Adapter evidence

The exact prepared-pixel target transformed once:

```text
exactMatches: 1
transformationsApplied: 1
containedFailures: 0
```

Prepared-pixel telemetry at shutdown:

```text
carriers: 20
directAttempts: 20
hits: 20
fallbacks: 0
dimensionFallbacks: 0
paddedUploads: 7
paddingBytes: 1002677
bytesBypassed: 1654859
uploadBytesSupplied: 2657536
peakDirectBytes: 1572864
releases: 20
releasedBytes: 2657536
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
internalErrors: 0
```

The prior repairs therefore worked as intended for:

- next-power-of-two minimum buffer length;
- fatal console capture and clean-exit classification;
- bounded direct-memory ownership;
- normal cleanup and shutdown accounting.

The failure is isolated to the unproven NPOT upload byte arrangement.

## Retained converter-shape clue

The run also retained `adapter-texture-loader-contract.json` for the exact installed class. Its bounded method shape shows that the original `BufferedImage -> ByteBuffer` converter:

- calls `org.lwjgl.BufferUtils.createByteBuffer(int)`;
- writes through indexed `ByteBuffer.put(int, byte)` calls;
- explicitly calls `ByteBuffer.position(int)`;
- explicitly calls `ByteBuffer.limit(int)`;
- returns the resulting buffer to upload callers that contain both `glTexSubImage2D` and `glTexImage2D` branches.

This does not disclose the exact index arithmetic, because instruction listings and class bytes were intentionally not included. It does show that Starsector's original arrangement is deliberate and is not safely reducible to “append zero padding before or after the source.”

## What the run does and does not prove

The run proves that a source-sized buffer is insufficient and that an expanded upload buffer is required for the observed NPOT path.

It disproves the implemented assumption that copying bottom-up source rows to the lower-left, zero-filling the right side, and zero-filling rows above is behaviorally equivalent to Starsector's original conversion.

It does **not** prove that upper placement is correct. Other possibilities include row-order differences, contiguous source bytes followed by padding, edge replication, or another allocation/copy convention. The next implementation must observe Starsector's original converter output instead of selecting another layout by inference.

## Repair decision

PR #135 restores fail-open behavior for NPOT textures:

1. power-of-two prepared payloads remain eligible for the reviewed lower-seam bypass;
2. NPOT prepared payloads return to Starsector's original decode and conversion path;
3. the untouched original buffer is compared against a bounded set of candidate layouts;
4. only dimensions, buffer bounds, candidate matches, and first mismatch offsets are retained;
5. original bytes, position, limit, cleanup, upload, and exceptions remain unchanged.

The retained observation list is bounded to 16 deduplicated logical paths and contains no texture payload bytes.

Validated implementation and readiness head:

```text
6ad76b6964c91649d71bcd7e8b944cd4fe49ff65
```

Successful workflows:

```text
CI run 516 — full Maven verification
Vanilla adapter gate tests run 368
Texture cache tests run 363
Prepare command tests run 93
```

Later commits in PR #135 are documentation-only alignment.

## Operator decision

Do not repeat the failed padded implementation and do not run a gameplay lifecycle or benchmarks.

After PR #135 is reviewed and merged, one launcher-only layout probe may be authorized:

```text
launch
→ inspect normal launcher visuals
→ do not start Starsector
→ close from the launcher
→ retain the complete run directory
```

The probe must show NPOT original-path fallbacks, zero guessed padded uploads, bounded original-layout observations, no observation errors, clean visuals, clean exit, and zero active prepared buffers at shutdown. Stop after that one launcher probe and review the evidence before implementing NPOT bypass again.
