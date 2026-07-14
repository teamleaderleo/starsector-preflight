# Starsector Preflight

Asset preprocessing, startup profiling, and cache tooling for heavily modded Starsector.

Preflight aims to shorten repeat launch times through measured, independently switchable subsystems:

- **Preflight Trace** — launch-time JFR profiling and reports
- **Preflight Index** — persistent resource-provider index
- **Preflight Textures** — upload-ready prepared texture cache
- **Preflight Scripts** — persistent Janino bytecode cache
- **Preflight Scheduler** — adaptive loading concurrency and memory limits
- **Preflight Builder** — cross-platform cache generation and diagnostics

## Quick start

Build or download the single runnable `preflight.jar`, then run:

```bash
java -jar preflight.jar run
```

Preflight will:

1. Discover a Starsector or Fast Rendering launcher.
2. Read the enabled mod profile and inventory its startup workload.
3. Inject the same JAR as a launch-time Java agent through the child process environment.
4. Start Starsector without editing the game, mods, saves, or VM parameter files.
5. Capture a timestamped JFR startup recording.
6. Write workload and startup summaries after the game process exits.

Run discovery without launching anything:

```bash
java -jar preflight.jar doctor
```

Create a convenient local launcher:

```bash
java -jar preflight.jar install
```

On macOS this creates `~/Applications/Starsector Preflight.app`. Linux receives a command and desktop entry. Windows receives a local command launcher.

## Workload census

Every normal run writes `profile.json` before starting the game. It includes:

- Ordered enabled mod IDs and missing-mod diagnostics
- Counts and compressed bytes for images, sounds, JARs, loose Java source, and data files
- Per-extension and per-mod totals
- Largest mods and individual assets
- Duplicate logical paths with probable enabled-order winners
- A repeatable profile fingerprint based on mod order, paths, sizes, and modification times

Run the census by itself:

```bash
java -jar preflight.jar scan --game "/path/to/Starsector" --json profile.json
```

Use `run --no-scan` to skip this step for a single launch.

## Overrides

Automatic discovery checks explicit arguments, `STARSECTOR_HOME`, `STARSECTOR_DIR`, the current directory, and common platform install locations.

```bash
java -jar preflight.jar run --game "/path/to/Starsector.app"
java -jar preflight.jar run --launcher "/path/to/fr.sh"
java -jar preflight.jar run --dry-run
```

Arguments following `--` are passed to the selected launcher:

```bash
java -jar preflight.jar run -- --some-launcher-option
```

Run data defaults to:

```text
~/.starsector-preflight/runs/YYYYMMDD-HHMMSS/
  run.json
  profile.json
  startup.jfr
  summary.json
```

See [automatic launch and discovery](docs/automatic-launch.md) for the full behavior and troubleshooting path.

## Build

Requires JDK 17 and Maven 3.9 or newer:

```bash
mvn verify
```

The self-contained executable and agent JAR is produced at:

```text
preflight-cli/target/preflight.jar
```

The verification suite launches that packaged file as a real `-javaagent` on Linux, macOS, and Windows.

## Other commands

Summarize an existing recording:

```bash
java -jar preflight.jar summarize preflight-startup.jfr --json report.json
```

Fingerprint a mod or file:

```bash
java -jar preflight.jar fingerprint /path/to/mod
```

Advanced manual agent usage remains available:

```bash
java -javaagent:preflight.jar=dest=preflight-startup.jfr -jar your-application.jar
```

Agent options are comma-separated. `dest64` is used internally for paths containing spaces or commas.

## Project documents

- [Roadmap](docs/roadmap.md)
- [Architecture](docs/architecture.md)
- [Benchmarking](docs/benchmarking.md)
- [Automatic launch and discovery](docs/automatic-launch.md)
- [ADR 0001: measurement first](docs/adr/0001-measurement-first.md)

## Status

Experimental. No Starsector or Fast Rendering binaries are included.

## License

License selection is pending.
