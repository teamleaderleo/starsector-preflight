# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Review and merge PR #139, then perform and review exactly one installed **launcher-only coherent-direct NPOT probe** using the repository runner.

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
- issue #129 — NPOT upload dimensions and prepared-path visual acceptance
- PR #139 — coherent source-sized carrier plus direct cached NPOT upload

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
```

## Established facts

The first direct NPOT build stopped crashing but rendered a black launcher. Its buffer ownership and lifecycle accounting were clean.

The original-layout probe later rendered normally and showed that the original converter's seven observed NPOT buffers used the same relevant row-padded arrangement as the failed direct path. The upper-versus-lower placement diagnosis was therefore wrong.

The coherent-image/original-converter probe also rendered normally. Exact retained run identity:

```text
repositoryHead: ab208dd2f16aaf521b07431cac86dca20763bf5e
jarSha256: d579f6f16bca0c8a73db91bfa8aee2fe3eddd68ceb5932187bb461d6fd77a9d0
archiveSha256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
classSha256: d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
```

Its telemetry included:

```text
carriers: 20
hits: 13
fallbacks: 7
npotProbeFallbacks: 7
coherentCarriers: 7
coherentCarrierBytes: 779083
coherentOriginalConvertFallbacks: 7
coherentOriginalDecodeBypasses: 7
paddedUploads: 0
internalErrors: 0
activeBuffers: 0
activeDirectBytes: 0
pendingBuffers: 0
```

This proves the cached pixels can form a coherent source-sized image accepted by Starsector. It leaves one decisive split:

1. the historical real `1x1` carrier caused the black launcher; or
2. the original converter performs another required side effect, or cached derived colors differ.

## PR #139 diagnostic

PR #139 adds the explicit opt-in property:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

For admitted NPOT prepared-cache hits under that property only, the path combines:

1. the proven real source-sized coherent image;
2. the exact observed row-padded next-power-of-two upload bytes;
3. the cached three derived colors;
4. the existing bounded direct-buffer ownership and cleanup path.

It bypasses ImageIO and the original pixel converter. The safe default remains unchanged without the property. If both diagnostic properties are present, coherent-direct takes precedence.

New telemetry includes:

```text
coherentDirectEnabled
coherentDirectCarriers
coherentDirectHits
paddedUploads
paddingBytes
```

## Interpretation

```text
normal launcher
→ the historical synthetic 1x1 carrier was the material cause;
→ coherent carrier + cached direct bytes + cached colors are viable at the launcher seam.

broken launcher
→ a required converter side effect or cached-color mismatch remains;
→ keep direct NPOT bypass disabled.
```

Do not infer gameplay acceptance from a normal launcher.

## Automated validation

Validated implementation/readiness/runner head before evidence-only commits:

```text
61a6b8c86b49f08745c5a9f75ecdb45a4719e3c0
```

Successful workflows:

```text
CI run 537 — full Maven verification
Vanilla adapter gate tests run 387
Texture cache tests run 379
Prepare command tests run 102
```

Exact archive/class/method/source/classloader gates, compatibility rollback, SPFT v1, circuit breaker, cleanup, exceptions, and direct-memory limits remain unchanged.

## Authorized operator action after merge

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-probe.sh
```

When the launcher appears:

```text
inspect all launcher visuals
→ do not click Play
→ close with the launcher X
→ report normal or broken
→ upload the generated Desktop archive
```

A duplicate screenshot is optional when the launcher is visually identical to the already retained accepted launcher.

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
- clean launcher exit.

## Definition of a good handback

Leave:

1. PR #139 merged or exact review findings recorded;
2. final workflow results and validated SHA;
3. exact run command, merged repository head, and JAR SHA-256;
4. complete retained coherent-direct archive and visual classification;
5. dated evidence classifying the result;
6. issue #129 and readiness aligned;
7. no gameplay lifecycle, repeated run, benchmark, or acceleration claim.
