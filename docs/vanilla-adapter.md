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
- `PROBE` — observes candidate game classes and writes their SHA-256 hashes and method signatures. It always retains the original class bytes.
- `ENABLED` — permits an exact allowlisted target to reach a registered transformation plan. Unknown builds, partial matches, missing plans, malformed classes, and all transformer errors retain the original bytes.

This build ships with zero live transformation plans. `ENABLED` therefore remains observational until a reviewed target-specific plan is added.

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
```

Use an explicit installation or launcher when automatic discovery needs help:

```bash
java -jar preflight.jar run --game "/path/to/Starsector.app" --adapter-probe
java -jar preflight.jar run --launcher "/path/to/starsector" --adapter-probe
```

`--adapter` selects `ENABLED` mode. Until a supported transformation plan is present, exact target matches are reported and the original bytes remain active.

A custom allowlist file may be supplied with:

```bash
java -jar preflight.jar run --adapter --adapter-targets targets.txt
```

## Ranked candidates

`adapter.json` contains two candidate views:

- `candidates` — a compact alphabetical list of the best retained classes.
- `rankedCandidates` — up to 50 likely image or texture integration points ordered by a deterministic relevance score.

Ranking currently uses class names, method names, JVM descriptors, and code-source ownership. Signals include texture/image/sprite terminology, image decoding, pixel-buffer and OpenGL types, upload/mipmap methods, and whether the class came from Starsector core, Fast Rendering, or a mod.

Each ranked candidate includes:

- exact class SHA-256
- source classification and code-source path
- score evidence
- the highest-scoring method names and exact JVM descriptors
- whether additional relevant methods were truncated

Ranking narrows the review set. It never generates or activates an allowlist automatically, and it does not prove a method is safe to rewrite.

## Kill switch

Either setting disables adapter transformer installation:

```text
PREFLIGHT_DISABLE_ADAPTER=1
-Dpreflight.adapter.disabled=true
```

JFR profiling remains independent and may continue.

## Target records

Every allowlisted target requires:

- an internal JVM class name
- an exact classfile SHA-256
- required method names and JVM descriptors
- a transformation plan ID

Class name alone is insufficient. A changed game build or modified class hash fails closed.

## When a real Starsector installation is required

Synthetic fixtures can verify parsing, matching, ranking, report generation, concurrency, fallback behavior, and packaged-agent operation. A real installation becomes necessary for two steps:

1. Run `--adapter-probe` against the exact Starsector build to collect and rank candidate class hashes and method signatures.
2. Validate a target-specific rewrite against that build and a representative mod profile before enabling it by default.

The probe step is read-only. The first live rewrite will remain opt-in, exact-version-gated, and covered by a global kill switch.
