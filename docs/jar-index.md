# Persistent JAR and classpath indexes

Preflight can persist the central-directory inventory of every enabled mod JAR and assemble those reusable inventories into an ordered profile index.

## Build

```bash
java -jar preflight.jar classpath index build --game "/path/to/Starsector"
```

The default cache root is:

```text
~/.starsector-preflight/cache/
```

Its classpath section contains:

```text
classpath/
  archives/
    ab/
      ab...sha256.spfj
  profiles/
    profile-fingerprint.spfc
quarantine/
```

## Two cache layers

### Archive index (`SPFJ`)

One archive index is keyed by the JAR's SHA-256. It stores every non-directory ZIP entry in sorted order with:

- exact case-sensitive entry name
- uncompressed size
- compressed size
- CRC-32
- ZIP compression method
- source JAR SHA-256 and byte length

Identical JAR content can therefore be reused across renamed mod folders and different enabled profiles.

### Profile index (`SPFC`)

One profile index stores:

- ordered mod and JAR providers
- physical JAR path, size, and modification time
- source SHA-256
- archive-index reference
- direct mapping from each class or resource entry to its ordered provider list

The last provider is the ordinary classpath winner.

## Repeat-build behavior

A profile fingerprint is computed from enabled mod order plus ordered JAR paths, sizes, timestamps, and declaration state.

- An unchanged profile loads its checksummed `SPFC` file and returns after metadata comparison.
- A changed profile reuses every matching content-addressed `SPFJ` archive index.
- A new or changed JAR is hashed and enumerated once.
- Corrupt or identity-mismatched indexes are moved to quarantine and rebuilt.
- A malformed source JAR remains a diagnostic and prevents a partial profile index from being persisted.

## Inspect and query

```bash
java -jar preflight.jar classpath index inspect profile.spfc
java -jar preflight.jar classpath index query profile.spfc shared/Utility.class
java -jar preflight.jar classpath index query profile.spfc shared/Utility.class --all
```

The query output includes the selected archive provider and the exact ZIP entry metadata from its archive index.

## Validate

Metadata validation:

```bash
java -jar preflight.jar classpath index validate profile.spfc
```

Deep validation also rehashes source JARs:

```bash
java -jar preflight.jar classpath index validate profile.spfc --deep
```

Validation checks source paths, regular-file status, byte length, modification time, optional content hash, archive-index checksum, and archive identity.

## Runtime boundary

The profile index is useful before any classloader hook exists:

- startup diagnostics can identify duplicate classes and exact winners;
- tools can answer class/resource provider queries without reopening every JAR;
- future runtime adapters can use the provider map as a negative cache and direct lookup table;
- Janino and loose-source caches can key results by ordered classpath fingerprint.

A future runtime adapter must keep the same fail-open rule used by prepared textures: an index miss, stale entry, corrupt cache, unknown game version, or internal exception returns control to the original Starsector class/resource lookup path.
