# Prepared-pixel lifecycle repair

Date: 2026-07-22

Status: implementation and automated validation complete on PR #132. No operator action is authorized.

Validated code head:

```text
6ae62ac627244ab1734397a94cb6460bef2d69e9
```

## Scope

This repair addresses issues #128, #129, and #130 after the first real prepared-pixel pilot failed before the main menu.

## Prepared-pixel admission and ownership

The prepared-pixel runtime now declines NPOT texture payloads before carrier creation. The observed `597x373` RGB payload is classified against the installed loader's `1024x512` upload dimensions and falls back to the original decode/conversion path. NPOT RGBA fixtures receive the same fail-open treatment.

Observed fixture:

```text
597 * 373 * 3 = 668043
1024 * 512 * 3 = 1572864
```

Power-of-two prepared payloads prove:

- `ByteBuffer.remaining() == uploadWidth * uploadHeight * channels`
- normal cleanup returns active buffers and active direct bytes to zero
- converter-caller exceptions release prepared-buffer accounting and rethrow the original exception
- fallback paths create no prepared direct buffer

The exact installed archive and class identities remain unchanged. Current direct-memory limits and the circuit breaker remain unchanged.

## Launcher console evidence

The run command now drains one combined child stdout/stderr stream, forwards it to the operator, and retains a bounded 1 MiB chronological tail at `console.txt` in the run directory.

Fatal child-console markers produce a non-clean effective exit while preserving the launcher result in `launcherExitCode`. Tests cover:

- fatal child console plus zero launcher exit;
- clean zero exit;
- nonzero launcher exit;
- existing log-file fatal detection;
- combined stdout/stderr volume above the retained-byte limit.

## Preparation reporting

Preparation prints bounded stage start/completion progress to stderr while stdout remains the report path.

Prepared-pixel readiness now records:

```text
preparedPixelsAdapter: offline-contract-accepted-live-pilot-revalidation-required
preparedPixelsBehavioralAcceptance: failed-2026-07-22-revalidation-required
realInstallPilotRequired: true
launchAccelerationClaimed: false
```

## Automated validation

The standard repository workflows passed on the validated code head:

```text
CI run 483:                       success
Prepare command tests run 73:   success
Texture cache tests run 334:    success
Vanilla adapter gate run 335:   success
Adapter probe analysis run 169: success
```

The CI run executed:

```bash
mvn --batch-mode --no-transfer-progress verify
```

A temporary diagnostic modification used during failure isolation was removed before the successful validation run. The standard CI workflow file matches `main`.

## Safety boundary

Compatibility mode remains the accepted rollback path. Another real prepared-pixel pilot, benchmarks, and acceleration claims remain unauthorized until this repair is reviewed and merged.
