# Roadmap

Preflight follows a measurement-first sequence. Each optimization keeps the original loader available as a fallback.

## Current near-term program

The July 2026 unified real-install runs completed the broad discovery gate for texture loading, Janino compilation, and audio decoding.

- [Optimization North Star](optimization-north-star.md) records the two-run evidence, exact reviewed targets, ordered implementation program, benchmark protocol, and release gates.
- [Real texture preparation and compatibility pilot](evidence/2026-07-18-real-texture-preparation-and-compatibility-pilot.md) records the passing full-profile preparation, the title-screen renderer failure, and the bounded launcher-lifecycle reporting fix.
- [Next LLM Implementation Handoff](next-llm-handoff.md) provides the concrete starting task, pinned identities, test requirements, and prohibited shortcuts for the next implementation session.

The adapter-OFF control reached the main screen and exited normally. Source review then confirmed that compatibility-v1 bypassed Starsector's asynchronous image-preloader rendezvous; compatibility-v2 preserves that handoff and matches the exact installed bytes. Compatibility-v2 passed bounded real-install behavioral acceptance on 2026-07-19 — see [the acceptance evidence](evidence/2026-07-19-real-texture-compatibility-v2-acceptance.md). The prepared-pixel prototype remains fail-closed because its synthetic color-sink model differs from the installed dataflow. The immediate sequence is now repeated OFF-versus-ENABLED texture measurement, exact prepared-pixel dataflow repair, resolution of the negative audio-equivalence evidence, and complete-key Janino reuse.

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
- Exact-gated compatibility and upload-ready runtime consumers

Exit condition: cached and uncached texture data are equivalent and repeat startup improves on image-heavy profiles.

## M3: Script bytecode

- Measure loose-source compilation cost
- Persist generated and transformed bytecode
- Capture complete ordered source/resource dependencies
- Conservative exact-context invalidation

Exit condition: representative source-heavy profiles compile once and safely reuse complete generated class maps.

## M4: Scheduling and integration

- Separate image and script worker pools
- In-flight decoded-byte budget
- Runtime adapter for vanilla Starsector and optional Fast Rendering support
- Cross-platform packaging and diagnostics

## M5: Prepared audio and later experiments

- Prove installed-JOrbis PCM and wrapper-contract equivalence
- Reuse short fully decoded effects with exact keys and untouched fallback
- Preserve streaming music until its policy is proven safe
- Evaluate selective lazy loading only when traces identify a narrow safe target

Exit condition: prepared audio is byte-for-byte and metadata-equivalent, bounded, fail-open, and measurably reduces repeat-launch decoding work.

## Milestone numbering in the issue tracker

M0–M5 above are the original document milestones. Later work continued the numbering in the issue tracker rather than here:

- M6 — synthetic production-cache workload proofs (PRs #67–#68).
- M7 — self-contained real-install probe kits (PR #72).
- M8 — exact real-install identity and equivalence gates before live reuse: issues #75 (audio), #77 (Janino), #78 (texture shape).
- M9 — one exact-profile pre-launch build and launch context: issue #76.
- M10 — repeated real OFF-versus-ENABLED startup benchmarks: issue #80.
