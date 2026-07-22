# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

The exact installed-class offline gate passed, and exact-profile preparation passed. The first real `prepared-pixels` lifecycle pilot then failed before the main menu with an undersized direct buffer at `glTexImage2D`.

See:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- issue #129 for the NPOT upload-dimension and buffer-accounting repair
- issue #130 for the fatal-console lifecycle false negative
- issue #128 for prepare progress and stale readiness output

Do not rerun or benchmark `prepared-pixels`. Compatibility mode remains the accepted rollback path.

## Exact failure

The live pilot produced:

```text
remaining buffer elements: 668043
required buffer elements: 1572864
```

For the failing RGB texture:

```text
668043   = 597 * 373 * 3
1572864  = 1024 * 512 * 3
```

The prepared bridge supplied source-sized bytes, while Starsector's upload call required a next-power-of-two allocation.

The retained adapter report also showed one active direct buffer and zero releases at shutdown. The launcher returned zero, so `run.json` incorrectly recorded `COMPLETED` despite the fatal console evidence.

## Current phase — implementation repair

The operator has no authorized prepared-pixel command right now.

The next implementation LLM must make narrow repairs tied to the real evidence:

1. Model the lower seam's upload-dimension contract exactly, or decline before a prepared carrier reaches upload when the required dimensions do not match the payload.
2. Add NPOT RGB and RGBA fixtures, including the observed `597x373 -> 1024x512` case.
3. Prove exact padding, placement, row order, channel count, and `ByteBuffer.remaining() == uploadWidth * uploadHeight * channels`; otherwise prove safe fallback.
4. Prove direct-buffer accounting returns to zero on success, fallback, and every exceptional path.
5. Expand offline/contract evidence so a structurally valid transform cannot hide a missing runtime dimension contract.
6. Capture bounded child stdout/stderr while streaming it, or otherwise classify fatal console evidence even when the launcher exits zero.
7. Correct stale preparation readiness and add bounded stage progress.

The repair must preserve:

- exact archive, class, source, and loader identity gates;
- untouched original fallback behavior;
- current direct-memory limits and circuit breaker;
- no game-installation, launcher, mod, save, or VM-parameter edits;
- compatibility mode as an independent rollback path.

## Required verification before another real pilot

A repair PR must pass:

```bash
mvn --batch-mode --no-transfer-progress verify
```

It must include focused tests for:

- NPOT RGB and RGBA upload sizes;
- exact prepared-buffer length and layout or explicit decline;
- source/upload-dimension mismatches;
- buffer release after GL/upload exceptions;
- child fatal output with exit code zero;
- normal clean zero-exit behavior;
- existing nonzero and log-file fatal detection;
- packaged shaded JAR integrity.

The reviewing LLM must inspect the code and CI before issuing any new operator command.

## Future operator route after repair approval

Only after a reviewed repair is merged may the operator repeat one lifecycle route:

```text
launch
→ main menu
→ load or start campaign
→ first combat
→ save
→ clean exit
```

Retain:

- `run.json`;
- `profile.json`;
- `summary.json`;
- `adapter.json`;
- `adapter-analysis.json` when present;
- `startup.jfr`;
- console output and exact command.

Stop after one repaired pilot. Repeated benchmarks remain blocked until behavioral evidence is accepted.

## Acceptance requirements for a future repaired pilot

Acceptance requires all of the following:

- one expected exact transformation;
- prepared-pixel hits and bypassed bytes above zero;
- no buffer-size or visual corruption;
- only understood original-path fallbacks;
- zero internal errors and no circuit breaker;
- active direct bytes, active buffers, and pending buffers equal zero at shutdown;
- release counts and bytes consistent with acquisitions, including exceptions;
- fatal console/log evidence absent;
- main menu, campaign, first combat, save, and clean exit completed;
- no visual, loading, campaign, combat, save, or shutdown regression.

## Standing safety rules

Do not:

- rerun the known-bad prepared-pixel build;
- start repeated benchmarks;
- generate allowlists from probe output;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, or worker pools;
- claim acceleration from this failed run;
- delete the failed run or its evidence.