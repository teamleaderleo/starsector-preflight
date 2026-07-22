# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs. The root `LLM_HANDOFF.md` is only a pointer here.

## Mission

Repair the first real prepared-pixel lifecycle failure without weakening exact gates or fallback behavior.

Do not start another subsystem, optimize CI, rerun prepared-pixels, or begin benchmarks.

Primary evidence:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- issue #129 — NPOT upload dimensions and exceptional-path direct-buffer accounting
- issue #130 — fatal console evidence missed when the launcher exits zero
- issue #128 — silent prepare progress and stale readiness fields

## State as of 2026-07-22

### What passed

- `texture-compatibility-v2` passed bounded real-install behavioral acceptance on 2026-07-19: one exact transformation, 4,926 prepared decoded-image hits, three original-path fallbacks, main screen reached, normal exit.
- The exact installed `TextureLoader` archive passed `PreparedPixelContractCheck` with the reviewed archive/class hashes, all nine methods, exact staged color flow, complete transform success, and no problems.
- Exact-profile preparation passed for a 71-mod profile with valid resource index and texture manifest, zero failed/invalid/quarantined blobs, 19,396 new blobs, and 5,307 hits.

### What failed

The first live `PREPARED_PIXELS` pilot failed before the main menu:

```text
prepared remaining bytes: 668043 = 597 * 373 * 3
GL required bytes:        1572864 = 1024 * 512 * 3
```

The exact target transformed once and served one prepared payload. Starsector then attempted a next-power-of-two GL upload using a source-sized buffer.

Retained telemetry at shutdown:

```text
transformationsApplied: 1
carriers: 1
directAttempts: 1
hits: 1
bytesBypassed: 668043
activeBuffers: 1
activeDirectBytes: 668043
releases: 0
releasedBytes: 0
```

The launcher returned zero and `run.json` incorrectly reported `COMPLETED`, even though inherited console output contained a fatal `GLLauncher` exception.

Prepared pixels therefore failed behavioral acceptance. Repeated measurement is blocked.

## Exact identities

```text
TextureLoader class:
com/fs/graphics/TextureLoader

class SHA-256:
d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50

archive SHA-256:
10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708

offline transformed SHA-256:
b32700195f5837c42dba0f2d202cc0a95af75bfd8b7725d8a2878f59d9e01527
```

Do not broaden these identities or generate new allowlists automatically.

## First actions for the next session

1. Read:
   - `README.md`;
   - `docs/prepared-pixels-operator-handoff.md`;
   - both 2026-07-22 prepared-pixel evidence documents;
   - issues #128, #129, and #130;
   - `docs/prepared-textures.md`;
   - the prepared-pixel transformer/runtime, texture preparation format, run command, and lifecycle evidence code.
2. Sync `main` and run:

```bash
mvn --batch-mode --no-transfer-progress verify
```

3. Trace the exact installed bytecode dimension flow that yields `597x373` source dimensions and `1024x512` GL upload dimensions.
4. Choose the narrowest safe repair:
   - exact upload-sized/padded cache payload with proven semantics; or
   - fail-open decline before the carrier reaches upload for any unsupported dimension contract.
5. Repair exceptional-path direct-buffer release/accounting.
6. Repair zero-exit fatal console classification with bounded, streamed output capture or equivalent evidence.
7. Correct preparation readiness and add bounded stage progress.

## Required tests

The implementation must add focused tests for:

- observed NPOT RGB `597x373 -> 1024x512` behavior;
- NPOT RGBA behavior;
- exact padding, row order, placement, dimensions, channels, and `ByteBuffer.remaining()`; or explicit safe fallback;
- power-of-two and existing identity cases remaining correct;
- buffer release/accounting on success, fallback, GL/upload exception, and bridge exception;
- child fatal console output with exit code zero producing a non-clean run;
- clean zero-exit behavior remaining `COMPLETED`;
- nonzero launcher exits and existing log-file fatal evidence;
- bounded console capture with no process pipe deadlock;
- prepare progress written to stderr while stdout behavior remains stable;
- current prepared-pixel readiness values;
- shaded packaged JAR integrity.

## Merge constraints

Every repair PR must state:

- exact identity boundary;
- original fallback behavior;
- direct-memory bounds and circuit breaker;
- tests added;
- evidence/counters added or corrected;
- what remains disabled;
- that the failed pilot supports no acceleration claim.

Run full verification and relevant platform workflows. Do not authorize another real pilot until the repair is merged and code/CI are reviewed.

## Operator status

The operator has no prepared-pixel command to run now.

They may launch Starsector normally or use the already accepted compatibility route, but must not rerun `--texture-mode prepared-pixels` and must not start a repeated campaign.

The next operator action will be one repaired lifecycle route only after explicit review approval.

## Definition of a good handback

Leave:

1. one narrow merged or review-ready repair PR covering #129 and, when practical, #130/#128 without mixing unrelated optimization work;
2. full and focused test results;
3. issue comments with commit SHA and remaining blockers;
4. updated evidence/handoff documents;
5. one exact next operator action, or an explicit statement that no operator run is authorized.