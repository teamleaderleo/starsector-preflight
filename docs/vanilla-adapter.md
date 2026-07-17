# Vanilla runtime adapter

Starsector Preflight uses a wrapper-launch model. It does not replace or edit the Starsector launcher.

```text
Preflight launcher
  -> selects the existing Starsector or Fast Rendering launcher
  -> starts that launcher as a child process
  -> adds one process-local -javaagent option
  -> the selected launcher starts the game normally
```

The process-local environment disappears when the child exits. Preflight does not modify the launcher, application bundle, VM parameter files, mods, or saves.

## Modes

The runtime adapter has three modes:

- `OFF` — default. JFR profiling may run, but no adapter transformer is installed and no `adapter.json` file is written.
- `PROBE` — observes candidate game classes and writes their class, source, loader, and method identities. It always retains the original class bytes.
- `ENABLED` — permits an exact allowlisted target to reach a registered transformation plan. Unknown builds, partial matches, missing source bindings, missing plans, malformed classes, and all transformer errors retain the original bytes.

The reviewed `TextureLoader` compatibility pilot is the first compiled plan. It remains inactive unless `ENABLED` mode receives a validated texture cache, manifest, and matching resource index.

## Wrapper commands

Probe the selected launcher without enabling transformations:

```bash
java -jar preflight.jar run --adapter-probe
```

The run directory contains:

```text
startup.jfr
summary.json
profile.json
run.json
adapter.json
adapter-analysis.json
adapter-code-loader-signatures.json
adapter-audio-decoder-signatures.json
```

`adapter-analysis.json` is generated automatically after the child process exits. It joins exact classes observed by the agent with methods that appeared on JFR stacks during image reads.

`adapter-code-loader-signatures.json` retains exact bounded Janino and compiler-loader identities. `adapter-audio-decoder-signatures.json` retains exact bounded JOrbis, Jogg, and Slick OpenAL identities that were actually loaded. Both are evidence reports only; neither creates an allowlist or enables a cache path. See [audio decoder evidence collection](audio-decoder-evidence.md) for the real-install protocol.

Use an explicit installation or launcher when automatic discovery needs help:

```bash
java -jar preflight.jar run --game "/path/to/Starsector.app" --adapter-probe
java -jar preflight.jar run --launcher "/path/to/starsector" --adapter-probe
```

`--adapter` selects `ENABLED` mode. Exact target matching and runtime configuration still decide whether a class changes.

A custom allowlist file may be supplied with:

```bash
java -jar preflight.jar run --adapter --adapter-targets targets.txt
```

## TextureLoader compatibility pilot

The pilot intercepts the exact reviewed decoded-image method:

```text
com/fs/graphics/TextureLoader.Ô00000(Ljava/lang/String;)Ljava/awt/image/BufferedImage;
```

It activates only when all reviewed identities match:

```text
class SHA-256:   d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
archive SHA-256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
source kind:     STARSECTOR_CORE
source suffix:   contents/resources/java/fs.common_obf.jar
loader class:    jdk/internal/loader/ClassLoaders$AppClassLoader
loader name:     app
```

The target also requires the complete manually reviewed nine-method descriptor set recorded by the texture contract report. The target is compiled by hand; probe output never creates it automatically.

Build a texture cache first. The build command prints the exact index and manifest paths:

```bash
java -jar preflight.jar texture build \
  --game "/path/to/Starsector.app" \
  --cache-dir "$HOME/.starsector-preflight/cache" \
  --paths-file startup-images.txt
```

Run the pilot with those exact files:

```bash
java -jar preflight.jar run \
  --game "/path/to/Starsector.app" \
  --adapter \
  --texture-cache-dir "$HOME/.starsector-preflight/cache" \
  --texture-manifest "$HOME/.starsector-preflight/cache/manifests/PROFILE.spfm" \
  --texture-index "$HOME/.starsector-preflight/cache/resource-indexes/PROFILE.spfi"
```

A full build may place the index under `indexes/` while a subset build uses `resource-indexes/`. Use the paths printed by the build result.

Before transformer installation, the pilot verifies:

- cache, manifest, and index paths remain inside the supplied cache directory;
- manifest and resource-index fingerprints match;
- every indexed provider still matches its recorded size and modification time;
- manifest and provider counts remain within fixed ceilings.

For each image request, a hit additionally verifies:

- normalized logical path and winning provider;
- current encoded source SHA-256;
- prepared blob checksum, source identity, transformation, dimensions, channels, and pixel length;
- identity transformation and equal original/upload dimensions;
- a fixed reconstructed-pixel ceiling.

A valid hit reconstructs a compatible `BufferedImage` from the existing bottom-up SPFT pixel payload. The remainder of Starsector's original texture-object, OpenGL upload, cleanup, and lifetime path stays active.

The transformer renames the reviewed original method body inside the same class and calls it directly on every cache miss, changed source, stale index, corrupt artifact, unsupported texture, ambiguity, circuit-breaker state, or internal error. Original exceptions from that original method propagate unchanged. Corrupt or identity-mismatched blobs are quarantined up to a fixed per-session limit.

`adapter.json` contains bounded texture telemetry:

- attempts, hits, misses, and fallbacks;
- bytes served;
- corruption and quarantine counts;
- internal-error count and circuit-breaker state;
- fixed fallback and disable-reason counters.

Synthetic tests prove exact identity acceptance/rejection, decoded-pixel equivalence, cold miss, warm hit, changed source, corrupt fallback, kill switch, and packaged child-JVM behavior. A real installation test becomes useful once a prepared working-set manifest contains images requested during a normal launch.

## Kill switch

Either setting disables adapter transformer installation:

```text
PREFLIGHT_DISABLE_ADAPTER=1
-Dpreflight.adapter.disabled=true
```

JFR profiling remains independent and may continue.

## Target records

Every target includes class identity:

- an internal JVM class name
- an exact classfile SHA-256
- required method names and JVM descriptors
- a transformation plan ID

A target is eligible for a live plan only when it also includes all required source bindings:

- `source-kind`
- a portable `source-suffix`
- `loader-class`

Optional stricter bindings are:

- `loader-name`
- `source-sha256`

The source suffix is installation-prefix independent. For example:

```text
source-kind STARSECTOR_CORE
source-suffix starsector-core/starfarer_obf.jar
loader-class jdk/internal/loader/ClassLoaders$AppClassLoader
```

Archive hashing is performed only when a target or specialized evidence report requests it. A missing, unreadable, non-file, oversized, or changed source archive fails closed. The hash result is cached by real path, size, and modification time within the process.

An exact class hash without the required live source bindings is rejected before the transformation registry is consulted. A copied identical class in another mod, JAR, or classloader therefore remains unmodified.

See `docs/adapter-targets.example.txt` for the complete line-oriented format.

## When a real Starsector installation is required

Synthetic fixtures verify parsing, matching, source binding, report generation, concurrency, fallback behavior, corruption handling, bytecode rewriting, and packaged-agent operation. A real installation is used for two validation steps:

1. Confirm the reviewed exact class/archive/source/loader identity on the supported Starsector build.
2. Run the opt-in compatibility pilot with a representative prepared working set, inspect hit/fallback telemetry, confirm startup/campaign/combat/save/exit behavior, and complete repeated OFF-versus-ENABLED measurements.

The pilot remains exact-version-gated, source-bound, opt-in, and covered by the global kill switch. No performance result follows from the synthetic tests alone.
