# Attributed startup I/O

Startup summaries group JFR file reads and writes by normalized path, lowercase extension, and startup-relevant category. Image reads are also correlated with application methods on their JFR stack traces.

```bash
java -jar preflight.jar summarize startup.jfr --json summary.json
```

Normal `preflight run` summaries include the same `ioAttribution` and `imageReadStackAttribution` sections automatically.

## Path normalization

- Windows separators become `/`.
- Repeated separators collapse.
- Paths containing spaces remain intact.
- Missing or unreadable JFR path fields become `<unknown>` for aggregate I/O.
- Extension matching is case-insensitive.

## Aggregate I/O output

The `ioAttribution` section includes bounded deterministic lists:

- `topReadPaths`
- `topWritePaths`
- `readExtensions`
- `writeExtensions`
- `readCategories`
- `writeCategories`

Every item reports operation count, bytes, and total event duration.

Entries are sorted by bytes, then duration, then operation count, then name. The first 25 items are retained so very large modpacks cannot produce unbounded reports.

## Image-read stack output

The `imageReadStackAttribution` section contains:

- total image-read events, bytes, and event duration
- events with and without usable stack traces
- considered and excluded frame counts
- retained-method and truncation diagnostics
- `topMethods`, ranked by repeated behavioral evidence

Each ranked method includes:

- normalized internal class name
- method name and exact JVM descriptor
- behavioral score
- image-read event count and bytes
- total event duration
- shallowest observed stack depth
- accumulated depth weight
- bounded normalized image-path samples

JDK, JUnit, Maven, and other platform frames are excluded. A method is counted at most once per read event, so recursion or duplicated frames cannot inflate one event.

The behavioral score rewards repeated appearances across distinct image paths and strongly favors frames nearer the actual file read. It is a diagnostic ranking, not proof that a method owns image decoding or texture upload.

## Categories

Current categories include:

- `image` — PNG, JPEG, WebP, BMP, GIF, TGA
- `sound` — OGG, WAV, MP3, FLAC
- `archive` — JAR and ZIP
- `data` — common Starsector data and definition extensions
- `code` — class, Java, and Kotlin files
- `preflight-cache` — SPFI, SPFT, SPFM, SPFJ, and SPFC
- `other` and `unknown`

## Interpretation

These reports answer questions that aggregate I/O alone cannot:

- Are encoded images dominating read bytes or operation count?
- Which game or mod methods were actually on-stack during those image reads?
- Do obfuscated methods repeatedly appear close to the read operation?
- Are JARs repeatedly opened or read?
- Are loose CSV/JSON/variant files numerous but individually small?
- Is Preflight cache traffic replacing encoded-source traffic?
- Which exact files and methods deserve a closer trace or adapter probe?

Event duration is useful evidence, but it is not identical to wall-clock startup contribution. File operations can overlap with decoding, compilation, synchronization, and other work. Repeated shallow stack presence is generally a stronger adapter-candidate signal than duration alone.

## Tests

Focused tests verify exact aggregation, repeated reads, mixed separators, case-insensitive extensions, bounded deterministic ties, missing path handling, synthetic behavioral ranking, and real `jdk.FileRead`/`jdk.FileWrite` stack events on Linux, macOS, and Windows.

Automatic cross-correlation with the exact class hashes in `adapter.json` remains a follow-up layer. Per-mod root attribution can also build on the normalized paths introduced here.
