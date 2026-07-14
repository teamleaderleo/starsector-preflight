# Starsector Preflight

Asset preprocessing, startup profiling, and cache tooling for heavily modded Starsector.

Preflight aims to shorten repeat launch times through measured, independently switchable subsystems:

- **Preflight Trace** — launch-time JFR profiling and reports
- **Preflight Index** — persistent resource-provider index
- **Preflight Textures** — upload-ready prepared texture cache
- **Preflight Scripts** — persistent Janino bytecode cache
- **Preflight Scheduler** — adaptive loading concurrency and memory limits
- **Preflight Builder** — cross-platform cache generation and diagnostics

## Current milestone

Measurement comes first. The bootstrap implementation contains:

- A Java 17 agent that starts JFR before application `main`
- A CLI JFR summarizer
- A deterministic content-aware directory fingerprint
- Initial architecture, roadmap, and benchmarking documents

## Build

```bash
mvn verify
```

The agent JAR is produced at:

```text
preflight-agent/target/preflight-agent-0.1.0-SNAPSHOT.jar
```

## Capture a startup trace

```bash
java \
  -javaagent:preflight-agent/target/preflight-agent-0.1.0-SNAPSHOT.jar=dest=preflight-startup.jfr \
  -jar your-application.jar
```

Agent options are comma-separated:

```text
dest=PATH,settings=JFR_CONFIGURATION_NAME
```

The default JFR configuration is `profile`.

## CLI

During development, run through Maven with the core module built in the same reactor:

```bash
mvn -pl preflight-cli -am package
java -cp "preflight-cli/target/preflight-cli-0.1.0-SNAPSHOT.jar:preflight-core/target/preflight-core-0.1.0-SNAPSHOT.jar" \
  dev.starsector.preflight.cli.PreflightCli summarize preflight-startup.jfr
```

Use `;` instead of `:` in the classpath on Windows.

Fingerprint a mod directory:

```bash
java -cp "preflight-cli/target/preflight-cli-0.1.0-SNAPSHOT.jar:preflight-core/target/preflight-core-0.1.0-SNAPSHOT.jar" \
  dev.starsector.preflight.cli.PreflightCli fingerprint /path/to/mod
```

## Project documents

- [Roadmap](docs/roadmap.md)
- [Architecture](docs/architecture.md)
- [Benchmarking](docs/benchmarking.md)
- [ADR 0001: measurement first](docs/adr/0001-measurement-first.md)

## Status

Experimental. No Starsector or Fast Rendering binaries are included.

## License

License selection is pending.
