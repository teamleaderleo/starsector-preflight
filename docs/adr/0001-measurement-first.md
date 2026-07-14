# ADR 0001: Measure startup before implementing caches

## Status

Accepted.

## Context

The Starsector loading sequence combines resource lookup, image decoding, per-pixel conversion, OpenGL upload, sound loading, class loading, Janino compilation, mod initialization, logging, and JVM behavior. Mod count alone cannot identify the dominant cost.

## Decision

The first deliverable is a launch-time JFR javaagent and benchmark protocol. Persistent caches begin after traces quantify their expected return.

## Consequences

- Initial releases improve observability before startup speed.
- Optimization issues include measurable acceptance criteria.
- Low-contribution ideas can be deferred early.
- The profiler remains useful without private game binaries in this repository.
