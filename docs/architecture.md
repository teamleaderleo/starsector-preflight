# Architecture

## Principles

1. Source mods and saves remain untouched.
2. Cache deletion is always safe.
3. Cache failures return to the original loading path.
4. Every cache format and integration interface is versioned.
5. Content identity, enabled-mod order, and transformation configuration participate in invalidation.
6. First-build and repeat-launch performance are reported separately.

## Components

### preflight-core

Portable cache-key, format, validation, and report utilities. It has no dependency on Starsector or Fast Rendering artifacts.

### preflight-agent

A Java 17 javaagent that begins JFR recording in `premain`, before Starsector's main method. The first implementation uses only JDK events and writes a `.jfr` file at process shutdown.

### preflight-cli

Developer-facing commands for content fingerprints and JFR summaries. It will grow into the cache builder after resource ordering and profile discovery are specified.

### Runtime adapter

A future narrow adapter will connect core interfaces to Fast Rendering's resource, texture, and script loading paths. Keeping this adapter separate limits compatibility work and allows the builder and formats to remain reusable.

## Planned cache layout

```text
preflight-cache/
  profiles/PROFILE_FINGERPRINT/manifest.json
  indexes/INDEX_FINGERPRINT.bin
  textures/CONTENT_HASH.pft
  bytecode/COMPILE_KEY/CLASS_NAME.class
  traces/
```

Content-addressed blobs can be shared by multiple enabled-mod profiles. Profile manifests are replaced atomically.
