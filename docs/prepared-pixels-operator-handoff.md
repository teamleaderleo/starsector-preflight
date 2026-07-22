# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

The exact installed-class offline gate passed, exact-profile preparation passed, and the first real `prepared-pixels` lifecycle pilot failed before the main menu because a source-sized RGB buffer reached a next-power-of-two OpenGL upload.

PR #132 repaired fail-open admission, exceptional direct-buffer release, fatal child-console classification, and preparation reporting. It was squash-merged as:

```text
4f3b79c6d7683242d16cb7b34081cd7800f20017
```

PR #133 implemented bounded runtime upload padding and was squash-merged as:

```text
68ece81782b54022d58d41634dd88491fca13601
```

Automated validation passed on implementation head:

```text
b3b1b59856008ad91609c02ac52eb1986e7bc14b
```

See:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [NPOT upload padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)

Exactly one prepared-pixel lifecycle route is now authorized. Compatibility mode remains the accepted rollback path.

## Original failure

```text
remaining buffer elements: 668043
required buffer elements: 1572864

668043  = 597 * 373 * 3
1572864 = 1024 * 512 * 3
```

The launcher shell also returned zero despite a fatal visible in its child console. PR #132 now captures that console and classifies the run as non-clean.

## Merged padding behavior

SPFT version 1 remains unchanged and source-sized. The bridge creates the live upload backing without resampling:

```text
bottom-up source rows
→ copy to lower-left of next-power-of-two backing
→ zero-fill each row to the right
→ zero-fill unused rows above
```

The carrier still reports the original source dimensions, leaving Starsector's original texture-size and texture-coordinate behavior in control.

Focused automated proof covers:

- observed `597x373 RGB -> 1024x512 RGB` exact remaining bytes;
- NPOT `3x5 RGBA -> 4x8 RGBA` full-buffer equality;
- source placement and row order;
- zero-filled right and upper padding;
- unchanged power-of-two payloads;
- rejection of an unexpected pre-padded SPFT v1 contract;
- expanded direct-memory accounting and normal release;
- expanded direct-memory accounting on upload exceptions;
- a packaged transformed-loader fixture that enforces the power-of-two minimum buffer.

Telemetry includes:

```text
paddedUploads
paddingBytes
uploadBytesSupplied
```

`bytesBypassed` remains source-sized so zero padding does not overstate conversion work avoided. Existing release, active-buffer, and active-direct-byte counters remain available.

## Automated validation

Successful workflows on the validated implementation head:

```text
CI run 500
Texture cache tests run 349
Vanilla adapter gate tests run 352
Prepare command tests run 84
```

The CI run executed the full Maven verification suite.

## Preserved boundaries

The merged implementation preserves:

- exact archive, class, source, method, and loader identity gates;
- original Starsector fallback behavior;
- original cleanup and exception behavior;
- current circuit breaker;
- compatibility mode as an independent accepted rollback path;
- unchanged SPFT version 1 files;
- 32 MiB maximum per expanded upload;
- 64 MiB maximum active prepared direct memory;
- 1,024 maximum active buffers;
- unchanged Starsector installation and launcher.

Automatic allowlist generation remains disabled. The failed pilot and the new automated tests support no acceleration claim.

## Authorized operator route

Use the exact current preparation report to obtain all paths. Do not infer or guess the cache fingerprint, manifest, index, target file, Java binary, or Starsector installation path.

Perform one route only:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

Retain:

- `run.json`;
- `profile.json`;
- `summary.json`;
- `adapter.json`;
- `adapter-analysis.json` when present;
- `startup.jfr`;
- `console.txt`;
- the exact command and binary identity;
- screenshots or precise notes for any visible texture problem.

Stop after that one repaired pilot. Repeated benchmarks remain blocked until behavioral evidence is accepted.

## Acceptance requirements

Acceptance requires:

- the exact expected transformation;
- prepared-pixel hits and source-sized bypassed bytes above zero;
- `uploadBytesSupplied` at least as large as `bytesBypassed`;
- `paddedUploads` and `paddingBytes` consistent with encountered NPOT textures;
- clean sprite, UI, campaign, and combat visuals with no flipped images, borders, fringes, or bleeding;
- only understood original-path fallbacks;
- zero internal errors and no circuit breaker;
- active direct bytes, active buffers, and pending buffers equal zero at shutdown;
- release counts and bytes consistent with acquisitions and exceptions;
- fatal console/log evidence absent;
- main menu, campaign, first combat, save, and clean exit completed;
- no loading, campaign, combat, save, or shutdown regression.

## Standing safety rules

Do not:

- run more than this one authorized prepared-pixel pilot;
- begin benchmarks;
- generate allowlists from probe output;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, or worker pools;
- treat zero padding as image resampling or claim visual upscaling;
- claim acceleration before a separate accepted measurement campaign;
- delete the failed run or the new pilot evidence.
