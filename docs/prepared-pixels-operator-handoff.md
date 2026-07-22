# Prepared-pixel acceptance: operator and LLM handoff

Status: 2026-07-22

## Current decision

The exact installed-class offline gate passed, exact-profile preparation passed, and the first real `prepared-pixels` lifecycle pilot failed before the main menu.

PR #132 now contains the narrow repair for issues #128, #129, and #130. Automated validation passed on code head `6ae62ac627244ab1734397a94cb6460bef2d69e9`.

See:

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [live pilot failure](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)

The operator has no authorized prepared-pixel command. Compatibility mode remains the accepted rollback path.

## Failure repaired by PR #132

The live pilot produced:

```text
remaining buffer elements: 668043
required buffer elements: 1572864

668043  = 597 * 373 * 3
1572864 = 1024 * 512 * 3
```

The prepared bridge supplied source-sized RGB bytes while Starsector requested a next-power-of-two upload. The retained adapter report also showed one active direct buffer and zero releases. The launcher shell returned zero while its console contained a fatal `GLLauncher` exception.

## Repair behavior

### Unsupported NPOT dimensions

Prepared-pixel admission now declines any payload whose source dimensions require next-power-of-two padding. The original Starsector decode/conversion path handles that texture.

Tests include:

- observed `597x373 -> 1024x512` RGB;
- NPOT RGBA;
- power-of-two RGB payload length;
- fallback before carrier/direct-buffer creation.

### Direct-buffer accounting

Exact converter callers now release the current prepared buffer when upload code throws, then rethrow the original exception. Normal cleanup still releases through the existing exact cleanup seam.

Tests prove active buffers and active direct bytes return to zero after success and upload exceptions. Fallback retains zero prepared direct bytes.

### Fatal child console evidence

The launcher child now uses one continuously drained combined stdout/stderr stream. Output remains visible to the operator and a bounded 1 MiB chronological tail is retained as `console.txt`.

Fatal child-console markers produce a non-clean effective exit even when the launcher shell returns zero. `launcherExitCode` preserves the launcher result.

### Preparation progress and readiness

Preparation emits bounded stage start/completion progress to stderr. Stdout remains the report path.

Readiness records the failed 2026-07-22 pilot and requires a future revalidation. No acceleration claim is present.

## Automated validation

The standard workflows passed on validated code head `6ae62ac627244ab1734397a94cb6460bef2d69e9`:

- CI run 483;
- Prepare command tests run 73;
- Texture cache tests run 334;
- Vanilla adapter gate tests run 335;
- Adapter probe analysis tests run 169.

## Preserved boundaries

PR #132 preserves:

- exact archive, class, source, and loader identity gates;
- original fallback behavior;
- current direct-memory limits and circuit breaker;
- unchanged Starsector installation and launcher;
- compatibility mode as an independent rollback path;
- original exceptions on upload failure.

## Future operator route after merge and review approval

Only after PR #132 is reviewed and merged may the operator repeat one lifecycle route:

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
- `console.txt`;
- the exact command and binary identity.

Stop after one repaired pilot. Repeated benchmarks remain blocked until behavioral evidence is accepted.

## Acceptance requirements for a future repaired pilot

Acceptance requires:

- one expected exact transformation;
- prepared-pixel hits and bypassed bytes above zero;
- clean dimensions and visuals;
- only understood original-path fallbacks;
- zero internal errors and no circuit breaker;
- active direct bytes, active buffers, and pending buffers equal zero at shutdown;
- release counts and bytes consistent with acquisitions and exceptions;
- fatal console/log evidence absent;
- main menu, campaign, first combat, save, and clean exit completed;
- no loading, campaign, combat, save, or shutdown regression.

## Standing safety rules

Do not:

- rerun the known-bad prepared-pixel build;
- authorize a repaired pilot before merge and review;
- start repeated benchmarks;
- generate allowlists from probe output;
- weaken exact identity gates;
- patch Starsector or its launcher;
- swallow original exceptions;
- perform OpenGL work on background threads;
- add unbounded caches, maps, logs, buffers, or worker pools;
- claim acceleration from the failed run;
- delete the failed run or its evidence.
