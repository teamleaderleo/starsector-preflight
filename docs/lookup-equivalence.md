# Game-like lookup equivalence benchmark

Preflight can compare persisted indexes with baseline ordered probing on the same installation artifacts.

```bash
java -jar preflight.jar benchmark lookups \
  --resource-index resources.spfi \
  --classpath-index classpath.spfc \
  --queries 10000 \
  --seed 42
```

Either index may be supplied alone.

## Loose-resource baseline

For each generated query, the baseline checks every resource root in order and records every regular file with that relative path. The indexed path performs one logical-key lookup and resolves the recorded providers.

The complete provider list must be identical. This verifies misses, overrides, and the final winner.

## Classpath baseline

The classpath baseline keeps each source JAR open and calls `ZipFile.getEntry()` in ordered classpath sequence. The indexed path performs one entry-name lookup in the `SPFC` provider map.

Again, the complete ordered provider list must match.

## Workload

The command generates a deterministic workload from the selected index:

- approximately 75% existing entries
- approximately 25% generated absent entries
- identical query sequence for baseline and indexed paths
- configurable count and seed

## Output

Each domain reports:

- query, hit, and miss counts
- indexed entry count
- baseline root/JAR probes
- indexed map lookups
- selected provider accesses
- probe-reduction ratio
- baseline and indexed elapsed time
- equivalence status
- mismatch count and bounded samples

Timing is diagnostic. Semantic equivalence and probe-count reduction are the release gates.

The command exits with status `6` when any provider list differs.

## CI fixture

Focused tests create game-like synthetic workloads with:

- 40 ordered loose-resource roots
- 24 ordered JAR providers
- hundreds of unique and overridden entries
- duplicate classes
- thousands of deterministic hits and misses

The same tests run on Linux, macOS, and Windows. They assert zero mismatches and prove baseline probing scales with roots or archives while indexed lookup remains one map operation per request.

## Runtime relevance

This benchmark does not claim the current game loader opens or probes resources in exactly the same implementation pattern. It tests the semantic contract required by a runtime adapter:

- indexed hits select the same providers;
- indexed misses are complete negative results;
- profile ordering remains intact;
- a future fail-open adapter can safely use the index only after validation.
