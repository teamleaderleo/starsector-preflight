# Prepared-pixel lifecycle repair

Date: 2026-07-22

Status: implementation complete on PR #132; automated validation in progress. No operator action is authorized.

## Scope

This repair addresses issues #128, #129, and #130 after the first real prepared-pixel pilot failed before the main menu.

## Prepared-pixel admission and ownership

The prepared-pixel runtime now declines NPOT texture payloads before carrier creation. The observed `597x373` RGB payload is classified against the installed loader's `1024x512` upload dimensions and falls back to the original decode/conversion path. NPOT RGBA fixtures receive the same fail-open treatment.

Power-of-two prepared payloads prove:

- `ByteBuffer.remaining() == uploadWidth * uploadHeight * channels`
- normal cleanup returns active buffers and active direct bytes to zero
- converter-caller exceptions release the prepared buffer accounting and rethrow the original exception
- fallback paths create no prepared direct buffer

The exact installed archive and class identities remain unchanged.

## Launcher console evidence

The run command now drains one combined child stdout/stderr stream, forwards it to the operator, and retains a bounded chronological tail at `console.txt` in the run directory. This avoids dual-pipe deadlocks and supplies lifecycle evidence when a launcher shell returns zero.

Fatal child-console markers produce a non-clean effective exit while preserving the launcher exit code in `launcherExitCode`. Tests cover fatal plus zero, clean plus zero, nonzero exit, and existing log-file fatal detection.

## Preparation reporting

Preparation prints bounded stage start/completion progress to stderr while stdout remains the report path. Prepared-pixel readiness now records the failed 2026-07-22 pilot and requires revalidation.

## Safety boundary

Compatibility mode remains the accepted rollback path. Another real prepared-pixel pilot, benchmarks, and acceleration claims remain unauthorized until this repair is reviewed and merged.
