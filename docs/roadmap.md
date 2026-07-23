# Roadmap

Preflight follows a measurement-first sequence. Each optimization keeps the original loader available as a fallback.

## Current near-term program

The July 2026 unified real-install runs completed the broad discovery gate for texture loading, Janino compilation, and audio decoding.

- [Optimization North Star](optimization-north-star.md) records the real-install evidence, exact reviewed targets, ordered implementation program, benchmark protocol, and release gates.
- [Real texture preparation and compatibility pilot](evidence/2026-07-18-real-texture-preparation-and-compatibility-pilot.md) records the passing full-profile preparation, the title-screen renderer failure, and the bounded launcher-lifecycle reporting fix.
- [Compatibility-v2 acceptance evidence](evidence/2026-07-19-real-texture-compatibility-v2-acceptance.md) records one bounded accepted real-install texture run.
- The dated reports under [docs/evidence/](evidence/) track the current prepared-pixel acceptance state; the newest file is the live status.

The adapter-OFF control reached the main screen and exited normally. Compatibility-v2 preserves Starsector's asynchronous image-preloader handoff, matches the exact installed bytes, and passed bounded real-install behavioral acceptance on 2026-07-19. PR #117 repaired the installed-style prepared-pixel color flow, and PR #119 added an offline exact installed-class contract checker. The immediate sequence is now: run that checker against the reviewed installation, review the report, complete one prepared-pixel lifecycle through campaign/combat/save/clean exit, and only then run repeated OFF-versus-compatibility-versus-prepared-pixel measurements. Audio and Janino remain exact-evidence gated until the texture decision is made.

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
