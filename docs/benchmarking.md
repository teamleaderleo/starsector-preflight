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

## Procedure

1. Use one fixed enabled-mod profile and launch target.
2. Perform one discarded setup launch after changing software.
3. Capture at least five measured runs per configuration.
4. Keep raw traces and timestamps.
5. Report median, minimum, maximum, and every individual result.
6. Reject runs with obvious updates, indexing jobs, thermal throttling, or severe swap activity.

Filesystem cache state differs by platform. Describe the exact method used instead of calling a result fully cold.
