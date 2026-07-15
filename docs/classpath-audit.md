# Mod classpath audit

Preflight audits the enabled mod classpath before introducing classloader or script-bytecode caches.

```bash
java -jar preflight.jar classpath audit --game "/path/to/Starsector"
```

Write the report to a file:

```bash
java -jar preflight.jar classpath audit --game "/path/to/Starsector" --json classpath.json
```

## Inputs

The audit reads:

- `mods/enabled_mods.json`
- each enabled mod's `mod_info.json`
- declared `jars` entries
- dependency IDs
- every physical `.jar` under the enabled mod directory
- ZIP central-directory metadata

Metadata parsing accepts the comment and trailing-comma conventions found in common Starsector libraries, including `#`, `//`, and block comments plus commented-out JAR entries.

## Report

The report includes:

- enabled and resolved mod counts
- declared, missing, undeclared, valid, and malformed JARs
- missing dependencies and dependencies that appear after their dependent mod
- class and non-class resource counts
- compressed and uncompressed archive entry bytes
- duplicate class names with ordered providers and a probable winner
- per-JAR SHA-256, size, class count, resource count, and validity

## Fingerprints

Two fingerprints serve different invalidation needs:

- `archiveFingerprint` is independent of enabled-mod order. It represents the set of mod IDs, JAR paths, and JAR content hashes and can key reusable archive indexes.
- `classpathFingerprint` includes enabled-mod and JAR order. It represents class/resource resolution semantics and changes when the profile is reordered.

## Failure behavior

Malformed or unreadable JARs remain report entries and diagnostics. They do not abort the full audit. No class is loaded or initialized during inspection.

## Optimization gate

The audit provides evidence for later work:

- persistent central-directory indexes
- duplicate-class diagnostics before launch
- direct class/resource provider maps
- Janino source and bytecode caches
- classpath-order compatibility checks

Those optimizations should be enabled only when traces and audit reports show meaningful repeated scanning or compilation cost.
