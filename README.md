# Starsector Preflight

Asset preprocessing, startup profiling, and cache tooling for heavily modded Starsector.

Preflight is an experimental project. The first milestone is measurement: produce repeatable startup traces and benchmark reports before changing the loading pipeline.

## Planned components

- **Preflight Trace** — launch-time profiling and reports
- **Preflight Index** — persistent resource-provider index
- **Preflight Textures** — prepared texture cache
- **Preflight Scripts** — persistent Janino bytecode cache
- **Preflight Scheduler** — adaptive loading concurrency
- **Preflight Builder** — cross-platform cache builder and diagnostics

See [`docs/roadmap.md`](docs/roadmap.md) once the bootstrap pull request lands.

## Status

Repository bootstrap in progress.

## License

License selection is pending.
