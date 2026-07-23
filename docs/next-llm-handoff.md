# Next LLM Implementation Handoff

This is the single living implementation handoff. Update it at the end of every working session. Archive dated evidence under `docs/evidence/`; do not create parallel repository handoffs.

## Mission

Review and merge the dedicated coherent-direct gameplay-smoke runner, then perform exactly one installed gameplay smoke from current `main`.

Do not repeat the accepted launcher probe, use a valuable save, benchmark, enable direct NPOT by default, or make an acceleration claim.

## Evidence chain

- [offline contract pass](evidence/2026-07-22-prepared-pixel-installed-contract-pass.md)
- [first live crash](evidence/2026-07-22-prepared-pixel-live-pilot-failure.md)
- [lifecycle repair](evidence/2026-07-22-prepared-pixel-lifecycle-repair.md)
- [guessed NPOT padding](evidence/2026-07-22-prepared-pixel-npot-padding.md)
- [NPOT visual failure](evidence/2026-07-22-prepared-pixel-visual-failure.md)
- [successful original-layout probe](evidence/2026-07-22-prepared-pixel-original-layout-probe.md)
- [successful coherent-image/original-converter probe](evidence/2026-07-22-prepared-pixel-coherent-converter-probe.md)
- [coherent-direct visual failure](evidence/2026-07-22-prepared-pixel-coherent-direct-visual-failure.md)
- [dimension-axis visual failure](evidence/2026-07-22-prepared-pixel-dimension-axis-failure.md)
- [corrected-axis launcher pass](evidence/2026-07-23-prepared-pixel-axis-launcher-pass.md)
- issue #129 — NPOT upload dimensions and prepared-path visual acceptance

## Merged implementation milestones

```text
PR #132 lifecycle, release, and fatal-evidence repair:
4f3b79c6d7683242d16cb7b34081cd7800f20017

PR #135 NPOT fail-open and original-layout observation:
1fd63567e5834546ab5d617234f84371df9909ea

PR #137 coherent cached image with retained original converter:
fd390ff797e554101cc78ab52516273c1c06fc24

PR #139 coherent carrier plus direct cached NPOT diagnostic:
23a8ec653d9f07e5df50ff3deab04efdf4104e49

PR #141 backing-dimension replay with incorrect axis assignment:
1b4194977c0fac9a5717d05bec6e858cb2fec419

PR #145 corrected height-first/width-second dimension axes:
d2333deca1697214231b6392b944ea2992150cae
```

## Established facts

The direct NPOT byte layout matches Starsector's original relevant row-padded upload layout. Coherent source-sized cached images are valid. Buffer ownership, cleanup, and failure accounting return to zero cleanly.

The two texture-object backing-dimension writes are required. The reviewed installed mapping is:

```text
first obfuscated setter  <- power-of-two upload height
second obfuscated setter <- power-of-two upload width
```

The corrected launcher-only run rendered normally and exited cleanly.

Retained launcher acceptance identity:

```text
archiveSha256: 898f99beb8940900a34634d53affc9a97705366fd42faf57a7d2b033bb8bb555
repositoryHead: fd5b240756674ea831aa1caae8edacc425a4c05c
jarSha256: 488f362d59aaad5408c844f9bae4821d4407dbf45b713128f325be10b673b939
prepared hits: 20
coherent-direct NPOT hits: 7
fallbacks/internal errors: 0
active/pending buffers at shutdown: 0
fatal evidence: none
```

This is launcher-level acceptance only. Gameplay and performance remain unproven.

## Safe default

Without:

```text
-Dpreflight.preparedPixels.coherentDirect=true
```

NPOT textures continue through Starsector's original decode/conversion path. Compatibility mode remains the accepted rollback. No installation or launcher files are edited.

## Gameplay-smoke runner contract

The dedicated runner:

- requires the corrected source mapping;
- verifies exact archive and class identities;
- reruns full Maven verification and the installed contract check;
- prepares exact cache, manifest, and index paths;
- records the accepted launcher archive SHA-256 as a prerequisite;
- enables coherent-direct only for this run;
- requires the route `launcher-main-menu-campaign-combat-save-clean-exit`;
- checks lifecycle, telemetry, cleanup, and fatal evidence;
- asks the operator explicit milestone questions;
- writes `operator-gameplay-result.json`;
- packages the complete run directory on the Desktop.

## Authorized operator action after merge

Use a new campaign, copied save, or disposable save:

```bash
git switch main
git pull --ff-only
bash scripts/run-prepared-pixel-coherent-direct-gameplay-smoke.sh
```

Required route:

```text
normal launcher
→ click Play Starsector
→ normal main menu
→ new campaign or disposable save
→ inspect campaign UI, portraits, ships, backgrounds, and effects
→ enter one combat and inspect ships, weapons, projectiles, effects, and UI
→ finish or exit combat normally
→ save
→ return to main menu
→ clean game exit
→ close the launcher if it reappears
```

Stop and exit cleanly if any black, sliced, repeated, stretched, missing, flipped, or progressively corrupt textures appear.

## Definition of a good handback

Retain the exact repository head, JAR SHA-256, generated Desktop archive, `operator-gameplay-result.json`, automated telemetry, visual classification, save result, process-attachment result, and clean-exit result. Update issue #129 and readiness. Do not repeat the smoke or begin benchmarks.
