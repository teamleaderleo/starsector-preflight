# Roadmap

Preflight follows a measurement-first sequence. Each optimization keeps the original loader available as a fallback.

## M0: Measurement foundation

- Launch-time JFR agent
- Startup trace summarizer
- Repeatable benchmark protocol
- Profile and environment fingerprints

Exit condition: a baseline result bundle explains the dominant startup costs for at least one large mod profile.

## M1: Resource index

- Ordered enabled-mod fingerprint
- Winning-provider lookup
- All-provider lookup for mergeable resources
- Negative lookup cache
- Case-collision diagnostics

Exit condition: fixture tests match reference resource resolution and benchmarks show the saved lookup work.

## M2: Prepared textures

- Benchmark current decode and conversion path
- Bulk conversion for common image layouts
- Versioned prepared-texture payload
- Content-addressed cache pack
- Corruption detection and rebuilding

Exit condition: cached and uncached texture data are equivalent and repeat startup improves on image-heavy profiles.

## M3: Script bytecode

- Measure loose-source compilation cost
- Persist generated and transformed bytecode
- Conservative whole-profile invalidation

Exit condition: representative source-heavy profiles compile once and safely reuse bytecode.

## M4: Scheduling and integration

- Separate image and script worker pools
- In-flight decoded-byte budget
- Runtime adapter for Fast Rendering
- Cross-platform packaging and diagnostics

## M5: Experiments

Sound preprocessing and selective lazy loading remain trace-driven experiments.
