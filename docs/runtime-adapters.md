# Runtime adapter boundary

Preflight's cache formats and lookup logic are independent from the launcher and renderer.

## Layering

```text
resource index + texture manifest + prepared blobs
                    |
           TextureCacheLookup
                    |
         FallbackTextureResolver
                    |
       +------------+-------------+
       |                          |
vanilla Starsector adapter   optional Fast Rendering adapter
       |                          |
original vanilla loader      original FR loader
```

The core layer has no Starsector, LWJGL, OpenGL, or Fast Rendering dependency. It can be tested with normal Java objects and synthetic game layouts.

## Fail-open rule

A cache artifact may improve a successful load. It may never turn that load into a failure.

For every logical texture request:

1. The adapter asks `TextureCacheLookup` for a prepared texture.
2. A verified `HIT` may bypass image decoding and pixel preparation.
3. `MISS`, `STALE`, `CORRUPT`, `UNSUPPORTED`, `DISABLED`, and `ERROR` invoke the original loader.
4. Unexpected runtime exceptions from the cache adapter are converted to `ERROR` and also invoke the original loader.
5. Exceptions from the original loader remain visible because they represent the game's uncached behavior.

The returned resolution records whether bytes came from the cache or the original path and preserves the cache status for diagnostics.

## Vanilla adapter

Vanilla Starsector is the primary runtime target. A version-specific javaagent adapter will:

- probe expected classes and method signatures before installing any transformation;
- load the profile manifest once;
- use the neutral resolver around the original texture preparation call;
- retain a process-wide kill switch;
- disable itself after compatibility or integrity failures according to a conservative error budget;
- leave the original implementation callable for every request.

The agent must decline activation when signatures are unknown. A declined adapter still permits census, indexes, cache generation, validation, and profiling.

## Optional Fast Rendering adapter

Fast Rendering support is separate. Detection of its classes may select an FR-specific call site, but it consumes the same manifest, blob, lookup, and fallback contracts.

Fast Rendering is therefore an optional integration path rather than a prerequisite for Preflight.

## Test strategy

Tests are divided into four levels:

1. **Core contract tests** — cache hits, every fallback status, unexpected cache exceptions, original-loader exceptions, and concurrency.
2. **Artifact tests** — missing, corrupt, stale, unsupported, and valid manifest/blob combinations.
3. **Synthetic ecosystem tests** — library-style and campaign-style mod layouts, override chains, shared content, nested JARs, comments, trailing commas, profile reordering, and repeat builds.
4. **Version-specific probes** — adapter signature fixtures and, where legally supplied by a developer, local smoke tests against an installed game. Game binaries are never committed or distributed.

## Integration gate

A version-specific adapter is eligible for opt-in testing only after it proves:

- cached output is byte-for-byte equal to the reference converter;
- every cache failure reaches the original loader;
- disabling Preflight restores original behavior without changing files;
- unsupported versions decline activation cleanly;
- adapter installation and lookup results are recorded in the startup report.
