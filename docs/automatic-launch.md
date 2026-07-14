# Automatic launch and discovery

Preflight is designed to run without editing Starsector or Fast Rendering files.

## Commands

```bash
java -jar preflight.jar doctor
java -jar preflight.jar run
java -jar preflight.jar install
```

`doctor` performs discovery and prints every candidate. `run` launches the selected candidate with trace capture. `install` copies the runnable JAR into the user's Preflight directory and creates a convenient platform launcher.

## Discovery order

Preflight considers:

1. `--launcher`
2. `--game`
3. `STARSECTOR_HOME`
4. `STARSECTOR_DIR`
5. The current directory
6. Common platform locations

macOS candidates include app bundles in `/Applications`, `~/Applications`, and `~/Games`. Linux candidates include common home, games, local-share, and `/opt` directories. Windows candidates include Program Files, Local AppData, and a home Games directory.

Within a game directory, Preflight recognizes common Starsector and Fast Rendering shell scripts, command files, executables, and macOS app-bundle executables. Fast Rendering names receive a higher selection score than vanilla launcher names.

Use an explicit launcher whenever an unusual port or custom wrapper receives the wrong score:

```bash
java -jar preflight.jar run --launcher "/absolute/path/to/custom-launcher"
```

## Injection model

Preflight passes one additional value to the child process through `JAVA_TOOL_OPTIONS`:

```text
-javaagent:/path/to/preflight.jar=dest64=ENCODED_TRACE_PATH
```

The encoded destination avoids parsing problems with spaces and commas. Existing `JAVA_TOOL_OPTIONS` content is preserved. A second Preflight agent is rejected to avoid duplicate recordings.

This environment change exists only for the launched child process. Preflight leaves all original launchers and VM parameter files untouched.

## Run output

Each run receives a directory containing:

- `run.json` — selected launcher, command, Java version, timestamps, and exit code
- `startup.jfr` — raw Java Flight Recorder data
- `summary.json` — aggregate startup metrics

The default location is `~/.starsector-preflight/runs/`.

## Installed launchers

### macOS

`install` creates:

```text
~/Applications/Starsector Preflight.app
```

The wrapper uses the Java runtime that executed the installer and invokes the copied Preflight JAR. The original Starsector app remains unchanged.

### Linux

`install` creates:

```text
~/.local/bin/starsector-preflight
~/.local/share/applications/starsector-preflight.desktop
```

### Windows

`install` creates a command launcher under Local AppData. Desktop and Start Menu shortcut generation can be added after native Windows validation.

## Troubleshooting

Run:

```bash
java -jar preflight.jar doctor --game "/path/to/game"
```

Then use `--launcher` with the exact file shown by the relevant Fast Rendering port. `--dry-run` prints the complete command, selected working directory, trace destination, and injected Java option without starting the game.
