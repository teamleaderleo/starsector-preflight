# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Perform and review exactly one installed **launcher-only coherent-direct backing-dimension probe** from current `main` using the repository runner.

Do not click Play, enter gameplay, repeat the probe, benchmark, or make an acceleration claim.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct diagnostic contract](evidence/2026-07-22-prepared-pixel-coherent-direct-diagnostic.md)
- [coherent-direct visual failure](evidence/2026-07-22-prepared-pixel-coherent-direct-visual-failure.md)
- issue #129 — NPOT upload dimensions and prepared-path visual acceptance

## Merged milestones

```text
PR #132 lifecycle, release, and fatal-evidence repair:
4f3b79c6d7683242d16cb7b34081cd7800f20017

PR #133 guessed direct NPOT padding:
68ece81782b54022d58d41634dd88491fca13601

PR #135 NPOT fail-open and original-layout observation:
1fd63567e5834546ab5d617234f84371df9909ea

PR #136 original-layout runner:
60071e12cfc29d691142f272857b37b06233b32c

PR #137 coherent cached image with retained original converter:
fd390ff797e554101cc78ab52516273c1c06fc24

PR #139 coherent source-sized carrier plus direct cached NPOT diagnostic:
23a8ec653d9f07e5df50ff3deab04efdf4104e49

PR #141 reviewed backing-dimension side-effect replay:
1b4194977c0fac9a5717d05bec6e858cb2fec419
```

## Established facts

The direct NPOT upload no longer crashes and its observed bytes match Starsector's original converter layout. The safe original-converter probes render normally.

The coherent-direct probe supplied the expected coherent carrier, cached colors, padded bytes, cleanup, and lifecycle accounting, but still rendered the launcher black. Retained identity:

```text
repositoryHead: f252e6eff207e2ed7d2b3682396c3450bbccccf8
jarSha256: 69a8a99a64b86049de6181eb2359f94ed510a5d94dd0dc286b69fa897721eab5
archiveSha256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256: d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
```

Telemetry included 20 hits, 7 coherent-direct NPOT hits, 7 padded uploads, zero fallbacks/errors, 20 releases, and zero active/pending buffers at shutdown. The synthetic `1x1` carrier was therefore not the sole cause.

## Merged backing-dimension diagnostic

The exact installed converter performs two texture-object `(I)V` calls before deriving colors and returning its buffer:

1. first setter receives the computed power-of-two upload width;
2. second setter receives the computed power-of-two upload height.

Merged PR #141 extracts exactly two distinct `(I)V` calls on `com/fs/graphics/Object` from the reviewed converter shape and declines transformation if that shape is missing or ambiguous.

For a successful prepared result, and only while:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

is enabled, the wrapper replays the setters in reviewed width-then-height order using `PreparedPixel.width()` and `PreparedPixel.height()`, then writes the cached colors and returns the prepared buffer.

Without the property, safe NPOT behavior is unchanged. Identity gates, compatibility rollback, SPFT v1, cleanup, exceptions, circuit breaker, and direct-memory limits remain unchanged.

## Final validation

Validated PR head:

```text
50907f3d52dc1c22b9a1ab83c66369448ac548ce
```

Successful workflows:

```text
CI run 557 — full Maven verification
Vanilla adapter gate tests run 407
Texture cache tests run 398
Prepare command tests run 111
```

PR #141 squash-merged as `1b4194977c0fac9a5717d05bec6e858cb2fec419`.

## Interpretation

```text
normal launcher
→ missing backing-dimension writes caused the black rendering;
→ coherent carrier + cached data are viable at the launcher seam with those writes restored.

broken launcher
→ another converter side effect or cached-color difference remains;
→ direct NPOT bypass stays unaccepted.
```

A normal launcher is not gameplay acceptance.

## Authorized operator action

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-dimension-probe.sh
```

When the launcher appears:

```text
inspect all launcher visuals
→ do not click Play
→ close with the launcher X
→ report normal or broken
→ upload the generated Desktop archive
```

A duplicate screenshot is optional when the classification is unambiguous.

## Expected automated evidence

- exact transformation applied once;
- coherent-direct property enabled;
- coherent-direct carriers and hits above zero;
- padded uploads equal coherent-direct hits;
- padding bytes above zero;
- original NPOT fallback counters zero;
- internal errors zero;
- active direct bytes, active buffers, and pending buffers zero at shutdown;
- no fatal console or log evidence;
- clean launcher exit;
- operator identity records `dimensionReplay=reviewed-converter-two-setter-order`.

## Definition of a good handback

Leave the exact merged repository head and JAR SHA-256, complete retained archive, visual classification, dated evidence, and issue #129/readiness alignment. Do not run gameplay, repeat the probe, benchmark, or claim acceleration.
