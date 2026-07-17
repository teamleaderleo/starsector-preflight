# Benchmarking

## Report these separately

- Baseline without Preflight
- First launch after building or invalidating caches
- Repeat launch with warm Preflight caches
- Cold-ish filesystem launch where the platform permits a reproducible procedure

## Record for every run

- Hardware model, CPU architecture, and memory
- Operating system version
- Java vendor and full version
- Starsector version
- Fast Rendering build and platform port
- JVM arguments
- Ordered enabled-mod profile fingerprint
- Preflight commit and configuration
- Browser/background-load state
- Memory pressure or swapping observations

## Scenario result contract

Record one manually driven startup scenario with three separate phases:

1. process start to usable main menu;
2. main menu to representative campaign readiness;
3. campaign readiness to first representative combat readiness.

The recorder accepts runner-supplied UTC timestamps and writes one deterministic JSON result:

```bash
java -jar preflight.jar benchmark scenario \
  --run-id 20260717-100000 \
  --scenario-id campaign-combat-v1 \
  --mode enabled-warm-hit \
  --iteration 1 \
  --profile-fingerprint PROFILE_SHA256 \
  --process-start 2026-07-17T10:00:00Z \
  --main-menu-ready 2026-07-17T10:00:12Z \
  --campaign-ready 2026-07-17T10:01:02Z \
  --first-combat-ready 2026-07-17T10:01:32Z \
  --exit-code 0 \
  --adapter-counter texture.hits=17 \
  --adapter-counter texture.misses=2 \
  --cache-counter entries=15 \
  --cache-counter bytes=4096 \
  --output benchmark.json
```

Supported modes are:

- `off-coldish`
- `off-warm`
- `enabled-build-miss`
- `enabled-warm-hit`
- `enabled-corrupt-artifact`
- `enabled-changed-profile`

The version 1 schema records:

- run ID, scenario ID, mode, iteration, and optional profile fingerprint;
- all four milestone timestamps;
- each phase duration plus process-to-campaign and process-to-combat totals;
- bounded adapter counters, cache counters, and disable reasons;
- process exit code and successful-exit classification.

Counter names use lowercase letters, digits, dots, underscores, and hyphens. Each adapter/cache domain accepts at most 64 counters. A result accepts at most 16 disable reasons. Counter ordering and disable-reason ordering are canonical in the JSON output.

## Procedure

1. Use one fixed enabled-mod profile and launch target.
2. Perform one discarded setup launch after changing software.
3. Capture at least five successful measured runs per mode.
4. Keep raw traces, console output, timestamps, and failed result records.
5. Report median, minimum, maximum, and every individual result.
6. Reject runs with obvious updates, indexing jobs, thermal throttling, or severe swap activity.
7. Classify unsuccessful exits separately and preserve their result files.

Filesystem cache state differs by platform. Describe the exact method used instead of calling a result fully cold. The scenario schema establishes comparable evidence; it makes no performance claim by itself.
