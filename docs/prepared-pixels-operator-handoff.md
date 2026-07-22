# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

Prepared pixels are **not behaviorally accepted** for direct NPOT conversion bypass.

The first installed pilot crashed because a source-sized `597x373 RGB` buffer reached a `1024x512 RGB` OpenGL upload. PR #132 repaired admission, exceptional direct-buffer release, fatal child-console classification, and preparation reporting.

PR #133 supplied a direct next-power-of-two buffer. The process no longer crashed and all buffer accounting cleaned up, but launcher textures rendered incorrectly.

PR #135 restored safe NPOT fallback and observed Starsector's untouched original upload buffers. The subsequent launcher-only probe rendered normally and proved that the original converter uses the same relevant row-padded byte arrangement as the failed direct path.

Evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)

## Corrected technical conclusion

The original-layout probe observed seven NPOT launcher textures. Five unambiguously matched:

```text
row-pad-source-then-zero-rows
```

Two play-button textures had no unused vertical rows and therefore matched both equivalent row-placement names.

The prior upper-versus-lower padding diagnosis was wrong. The failed direct prepared implementation supplied the relevant bytes in the same arrangement as Starsector's original converter.

The remaining material differences are outside those bytes:

- the historical prepared carrier was a real `1x1` raster/sample model that only overrode no-argument width and height; or
- Starsector's original converter performs additional required side effects beyond returning the buffer and assigning the three reviewed colors.

## Safe default behavior

Current merged `main` before PR #137:

- keeps power-of-two prepared bypass;
- sends NPOT textures through Starsector's original decode and conversion path;
- observes original NPOT buffer layout without mutating it;
- retains at most 16 deduplicated observations;
- retains no original pixel payload in telemetry;
- preserves original upload, cleanup, exceptions, and texture lifetime.

Compatibility mode remains the accepted rollback.

## PR #137 diagnostic behavior

PR #137 adds an explicit opt-in diagnostic property:

```text
-Dpreflight.preparedPixels.coherentOriginalConvert=true
```

Under that property, and only for NPOT cache hits:

1. Preflight reconstructs a real top-down sRGB `BufferedImage` with source-sized raster and sample-model dimensions.
2. The image contains the cached RGB/RGBA pixels with the stored bottom-up rows reversed into Java's top-down coordinate system.
3. Preflight bypasses ImageIO decode.
4. Starsector's retained original converter runs on that coherent cached image.
5. The original converter's exact buffer, all side effects, cleanup, upload, and exceptions remain authoritative.

The diagnostic does **not** return a direct prepared NPOT buffer. It is an isolation experiment, not a performance-ready implementation.

Without the property, safe NPOT behavior is unchanged.

## Diagnostic result meanings

```text
normal launcher visuals
→ the coherent cached image is acceptable;
→ the direct prepared path skipped required converter behavior.

broken launcher visuals
→ the coherent cached image is not equivalent to Starsector's decoded image.
```

Do not infer a final production repair until the retained run is reviewed.

## Preserved boundaries

The diagnostic keeps:

- exact archive, class, method, source, and loader identity gates;
- SPFT version 1 unchanged;
- the original asynchronous preloader handoff;
- original converter, buffer, side effects, cleanup, and exception behavior;
- current circuit breaker and direct-memory limits;
- compatibility mode as rollback;
- no installation, launcher, mod, or save edits;
- no automatic allowlist generation;
- no benchmark or acceleration claim.

## Authorized operator action after PR #137 merges

Exactly one **launcher-only coherent-image/original-converter probe** is authorized.

From the repository root:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-converter-probe.sh
```

Environment overrides remain available:

```bash
GAME="/path/to/Starsector.app" \
CACHE="$HOME/.starsector-preflight/cache" \
bash scripts/run-prepared-pixel-coherent-converter-probe.sh
```

The runner:

1. builds and verifies the checkout;
2. verifies exact installed archive and class identities;
3. reruns the offline contract checker;
4. prepares the exact current profile;
5. sets only the coherent-original-converter diagnostic property;
6. prints the dry-run plan;
7. starts one launcher-only run;
8. checks coherent carrier, original converter, layout observation, lifecycle, and cleanup telemetry;
9. packages the complete run directory on the Desktop.

When the launcher appears:

```text
inspect background, logo, resolution selector, toggles, Play button,
Options, Mods, borders, and vendor logos
→ take a screenshot
→ do not click Play
→ close with the launcher X
```

Stop after that one probe.

## Automated evidence requirements

The generated run must show:

- exact transformation applied once;
- diagnostic property enabled;
- coherent carriers and coherent carrier bytes above zero;
- NPOT and coherent-original-converter fallbacks above zero;
- coherent original decode bypasses above zero;
- direct padded uploads equal zero;
- original layout observations present;
- each observed carrier raster and sample-model dimension equal to its source dimension;
- observation and internal errors zero;
- active direct bytes, active buffers, and pending buffers zero at shutdown;
- no fatal console/log evidence;
- clean launcher exit.

Manual visual classification is still required.

Upload the generated `.tar.gz` and screenshot for review.

## Standing safety rules

Do not:

- run the old direct padded build;
- enable direct NPOT prepared buffers from this diagnostic alone;
- run more than the single authorized probe;
- click Play or enter gameplay;
- begin benchmarks;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, logs, observations, or worker pools;
- claim acceleration before a separate accepted measurement campaign;
- delete the failed pilot, original-layout probe, or coherent-converter evidence.
