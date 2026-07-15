# Synthetic startup laboratory

This module provides deterministic synthetic startup workloads for proving persistent cache behavior without requiring a Starsector installation or committing a large binary corpus.

The first workload slice covers:

- explicit mod order
- deterministic resource-provider selection
- override collisions
- PNG decoding into RGBA bytes
- a bounded checksummed synthetic prepared-image cache
- cold, warm, and corrupt-cache runs in separate JVM processes
- structural work counters and output-equivalence hashes

The `SPXI` cache format belongs only to this laboratory. It is not a production image-cache format and is not consumed by the Starsector agent.

Required tests assert decoder invocation counts and exact output hashes. They do not use startup-time thresholds.
