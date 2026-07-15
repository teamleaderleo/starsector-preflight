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
- `ENABLED` — permits an exact allowlisted target to reach an available transformation plan. Unknown builds, partial matches, missing source bindings, unavailable plans, malformed classes, and all transformer errors retain the original bytes.

This build contains one reviewed opt-in vanilla compatibility plan. It remains unavailable unless the wrapper supplies a validated prepared-texture cache root, texture manifest, and resource index for the same profile fingerprint.

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
```

`adapter-analysis.json` is generated automatically after the child process exits. It joins exact classes observed by the agent with methods that appeared on JFR stacks during image reads.

Use an explicit installation or launcher when automatic discovery needs help:

```bash
java -jar preflight.jar run --game "/path/to/Starsector.app" --adapter-probe
java -jar preflight.jar run --launcher "/path/to/starsector" --adapter-probe
```

A custom allowlist file may be supplied with:

```bash
java -jar preflight.jar run --adapter --adapter-targets targets.txt
```

## Prepared-image compatibility pilot

The first live vanilla plan wraps one exact private `TextureLoader` image-decoder method identified by a reviewed real probe. It activates only when all of these match:

- exact internal class name
- exact classfile SHA-256
- exact method name and JVM descriptor
- Starsector-core source kind and portable source suffix
- app classloader class and name
- a validated cache root, `.spfm` texture manifest, and `.spfi` resource index with the same profile fingerprint

Launch the pilot with explicit artifacts:

```bash
java -jar preflight.jar run \
  --adapter \
  --adapter-cache-dir "/path/to/cache" \
  --adapter-texture-manifest "/path/to/cache/manifests/<fingerprint>.spfm" \
  --adapter-resource-index "/path/to/indexes/<fingerprint>.spfi"
```

All three cache arguments are required together and are accepted only with `--adapter`. Preflight does not choose a newest or similarly named manifest automatically.

The compatibility wrapper behaves as follows:

1. Resolve the loader argument as either a logical resource name or the physical winning provider recorded by the resource index.
2. Verify the current winning source still has the indexed size and modification time.
3. Read and validate the manifest-selected prepared blob, including its checksum and metadata.
4. On a supported identity blob, reconstruct a top-down ARGB `BufferedImage` without ImageIO and return it.
5. On every miss, stale or corrupt artifact, unsupported shape, disabled state, or internal bridge error, execute the renamed untouched original decoder.

After eight unexpected internal bridge failures, the cache bridge disables itself for the rest of the process. The original decoder remains available.

`adapter.json` records:

- whether the live plan was available for the session
- exact target evaluations and transformations applied
- cache root, manifest, and resource-index paths
- prepared-image hits, fallbacks, internal errors, and status counts

This pilot skips encoded source-file reads and ImageIO decoding on verified hits. It does **not** yet bypass Starsector's later raster conversion, row reversal, derived-color analysis, texture allocation, OpenGL upload, or mipmap work. A lower-level upload-ready pixel plan is the intended high-payoff follow-up.

## Ranked candidates

`adapter.json` contains two candidate views:

- `candidates` — a compact alphabetical list of the best retained identities.
- `rankedCandidates` — up to 50 likely image or texture integration points ordered by a deterministic relevance score.

A candidate identity includes:

- internal class name and exact classfile SHA-256
- raw and normalized code-source location
- source kind: Starsector core, Fast Rendering, mod, other, or unknown
- classloader class and optional loader name
- optional source archive SHA-256 when a target explicitly requests it
- static relevance and exact method descriptors

Identical class bytes loaded from two JARs or classloaders remain separate candidates. They are not collapsed merely because the class name and bytes match.

Ranking uses class names, method names, JVM descriptors, and code-source ownership. Signals include texture/image/sprite terminology, image decoding, pixel-buffer and OpenGL types, upload/mipmap methods, and whether the class came from Starsector core, Fast Rendering, or a mod.

Ranking narrows the review set. It never generates or activates an allowlist automatically, and it does not prove a method is safe to rewrite.

## Combined probe analysis

`adapter-analysis.json` combines:

- every retained agent identity and its exact class SHA-256
- code-source and classloader identity
- richer static relevance evidence from `adapter.json`
- image-read behavioral methods from `summary.json`
- separate static, behavioral, and combined scores
- exact method descriptors and bounded image-path samples
- behavior-only classes that did not overlap an agent candidate

The behavioral join uses exact internal class names because JFR stack frames do not always expose source and loader identity. When one class name maps to multiple retained source/loader identities, the report preserves every variant and lists the class under `ambiguousBehavioralClassNames`. Human review must choose the correct identity.

The probe-analysis report explicitly records:

```text
automaticAllowlistGenerated: false
liveTransformationEligible: false
requiresHumanReview: true
sourceIdentityPreserved: true
```

Analyze existing reports manually with:

```bash
java -jar preflight.jar analyze probe adapter.json summary.json --json adapter-analysis.json
```

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
source-suffix contents/resources/java/fs.common_obf.jar
loader-class jdk/internal/loader/ClassLoaders$AppClassLoader
loader-name app
```

Archive hashing is performed only when a target declares `source-sha256`. A missing, unreadable, non-file, oversized, or changed source archive fails closed. The hash result is cached by real path, size, and modification time within the process.

An exact class hash without the required live source bindings is rejected before the transformation registry is consulted. A copied identical class in another mod, JAR, or classloader therefore remains unmodified.

See `docs/adapter-targets.example.txt` for the complete line-oriented format.

## When a real Starsector installation is required

Synthetic fixtures verify parsing, matching, source binding, ranking, report generation, cache reconstruction, bytecode verification, fallback behavior, and packaged-agent operation. A real installation is still required to:

1. Collect a read-only probe against the exact Starsector build.
2. Validate the target-specific rewrite against that build and a representative mod profile.
3. Measure repeatable adapter-OFF versus adapter-ENABLED launch outcomes before making any acceleration claim.

The probe step is read-only. The first live rewrite remains opt-in, exact-version-gated, source-bound, and covered by a global kill switch.
