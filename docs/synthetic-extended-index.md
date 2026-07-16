# Extended synthetic provider index

This workload extracts the deterministic profile and loose/JAR provider-index
layers from the broader experimental startup laboratory. It deliberately does
not reuse that branch's duplicate prepared-audio, bytecode, or generic prepared
store implementations. Production prepared-audio and generated-bytecode caches
remain the only accepted substrates for later integration.

## Deterministic profiles

| Scale | Mods | Resources | PNGs | WAVs | Java sources | JARs | Loose JAR overrides | Physical files |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Tiny | 4 | 36 | 48 | 12 | 8 | 4 | 4 | 112 |
| Medium | 10 | 2,010 | 400 | 50 | 40 | 8 | 8 | 2,516 |
| Large | 77 | 17,967 | 24,000 | 1,400 | 3,800 | 78 | 78 | 47,323 |

Generation uses an explicit seed and mod order. The generator accepts only a
missing or empty destination and never deletes an occupied directory. Profile
fingerprints stream sorted logical paths and file bytes under global file,
path, and total-byte ceilings. The manifest is deterministic UTF-8 text with no
timestamp-bearing `Properties.store` output.

## Provider semantics

Mods are processed in explicit order, with later mods replacing earlier
providers. Inside one mod, sorted JAR entries are indexed first and sorted loose
files second, so a loose file in that same mod replaces a JAR-backed logical
path. Each final provider records:

- provider kind: JAR entry or loose file;
- source path relative to the exact profile root;
- exact JAR entry name when applicable;
- resource byte length; and
- resource SHA-256.

The tiny profile has 89 final providers, 23 distinct collided logical paths,
and 27 provider replacement events. Keeping both counts makes repeated
replacement of one logical path visible.

## Safety ceilings

The index builder enforces global ceilings of:

- 1,000 mods;
- 100,000 physical files;
- 100,000 final providers;
- 100,000 visited JAR entries;
- 128 MiB per JAR archive;
- 32 MiB per indexed resource;
- 16 KiB per serialized string; and
- 128 MiB per persisted index file.

Profile walks reject symbolic links. JAR enumeration is counted before sorting,
duplicate normalized entry names are rejected, and entry reads are bounded.
Index serialization calculates the exact payload size with checked arithmetic
before allocating its output buffer.

## Persistent identity

SPXR v2 files contain the exact profile fingerprint, collision counts, sorted
provider records, and an authenticated payload. Lookup uses a bounded read and
classifies an exact path as `HIT`, `MISS`, `CORRUPT`, or `ERROR`. Copying a valid
index under another profile's content-addressed key is rejected because the
embedded profile fingerprint must match the requested identity.

Provider consumption reopens the recorded loose file or JAR entry and verifies
its exact byte length and SHA-256 before returning bytes. A source change cannot
silently reuse stale indexed content.

## Separate-JVM proof

The focused workflow runs the tiny profile on Linux, macOS, and Windows and the
2,516-file medium profile on Ubuntu. Each cross-process test performs:

1. a cold JVM that misses, scans loose/JAR providers, and persists the index;
2. a warm JVM that hits the exact index with zero provider-index build visits;
3. a corrupt-index JVM that rebuilds once; and
4. exact provider metadata and provider-output digest comparisons across all
   passes.

This proves deterministic corpus generation, provider precedence, persisted
identity, and separate-JVM reuse. It does not represent a Starsector install and
does not establish a startup-time change. PR #60 remains unmerged while its
useful layers are extracted onto current production-safe cache substrates.
