# Attributed startup I/O

Startup summaries now group JFR file reads and writes by normalized path, lowercase extension, and startup-relevant category.

```bash
java -jar preflight.jar summarize startup.jfr --json summary.json
```

Normal `preflight run` summaries include the same `ioAttribution` section automatically.

## Path normalization

- Windows separators become `/`.
- Repeated separators collapse.
- Paths containing spaces remain intact.
- Missing or unreadable JFR path fields become `<unknown>`.
- Extension matching is case-insensitive.

## Output

The summary includes bounded deterministic lists:

- `topReadPaths`
- `topWritePaths`
- `readExtensions`
- `writeExtensions`
- `readCategories`
- `writeCategories`

Every item reports operation count, bytes, and total event duration.

Entries are sorted by bytes, then duration, then operation count, then name. The first 25 items are retained so very large modpacks cannot produce unbounded reports.

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

These totals answer questions that aggregate I/O cannot:

- Are encoded images dominating read bytes or operation count?
- Are JARs repeatedly opened or read?
- Are loose CSV/JSON/variant files numerous but individually small?
- Is Preflight cache traffic replacing encoded-source traffic?
- Which exact files deserve a closer trace or benchmark?

Event duration is useful evidence, but it is not identical to wall-clock startup contribution. File operations can overlap with decoding, compilation, synchronization, and other work.

## Tests

Focused tests verify exact aggregation, repeated reads, mixed separators, case-insensitive extensions, bounded deterministic ties, missing path handling, and real `jdk.FileRead`/`jdk.FileWrite` events on Linux, macOS, and Windows.

Per-mod root attribution is a follow-up layer built on the normalized paths introduced here.
