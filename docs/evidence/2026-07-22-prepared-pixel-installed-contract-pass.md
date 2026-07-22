# Prepared-pixel installed contract pass — 2026-07-22

## Scope

This evidence records one read-only `PreparedPixelContractCheck` run against the reviewed macOS Starsector installation. It is offline bytecode-contract evidence only. It does not establish live gameplay acceptance or startup acceleration.

The command did not launch Starsector or modify the installation.

## Environment

- Platform: macOS
- Installation: `/Applications/Starsector.app`
- Input archive: `Contents/Resources/Java/fs.common_obf.jar`
- Preflight source baseline: current `main` after PR #126
- Checker schema: `starsector-preflight-prepared-pixel-contract`, version `1`

## Exact identities

- Input archive bytes: `308178`
- Input archive SHA-256: `10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708`
- Archive entry: `com/fs/graphics/TextureLoader.class`
- Class bytes: `10709`
- Class SHA-256: `d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50`
- Internal class name: `com/fs/graphics/TextureLoader`
- Exact class-name match: `true`

These hashes match the manually reviewed 0.98a-RC8 target identities already recorded by the project.

## Required method gate

All nine required methods were present:

1. `o00000(BufferedImage, com.fs.graphics.Object) -> ByteBuffer`
2. `o00000(String, BufferedImage) -> void`
3. `Ò00000(String, BufferedImage) -> com.fs.graphics.Object`
4. `Ô00000(String) -> BufferedImage`
5. `o00000(BufferedImage, int, int, int, int) -> com.fs.graphics.Object`
6. `o00000(ByteBuffer, String) -> void`
7. `Ò00000(String) -> ByteBuffer`
8. `o00000(com.fs.graphics.Object, String, int, int, int, int, boolean) -> com.fs.graphics.Object`
9. `o00000(String) -> com.fs.graphics.Object`

## Prepared-pixel color-sink gate

- Model: `staged-loader-setters`
- Eligible: `true`
- Raster read observed: `true`
- Direct texture-object color fields: `0`
- Staged loader fields: `3`
- Staged fields declared exactly: `true`
- Staged setter flow exact: `true`
- Problems: none

The three reviewed non-static `TextureLoader` color fields were:

- `interface : java.awt.Color`
- `õ00000 : java.awt.Color`
- `Ó00000 : java.awt.Color`

Each field was bound to receiver local `0` and flowed through the reviewed distinct texture-object setter path without ambiguity.

## Transformation result

- Overall eligible: `true`
- Transformation succeeded: `true`
- Transformed bytes: `11484`
- Transformed SHA-256: `b32700195f5837c42dba0f2d202cc0a95af75bfd8b7725d8a2878f59d9e01527`
- Top-level problems: none

The shell attempted to assign the pipeline result to `status`, which is a read-only special parameter in zsh. That produced `zsh: read-only variable: status` after the checker had already emitted its complete successful JSON report. The operator instructions must use a different variable name such as `checker_status`.

## Decision

The offline installed-class contract gate passes.

No code repair or identity-gate weakening is required. The next phase is:

1. prepare or verify the exact current profile cache;
2. retain the generated preparation report;
3. extract the exact cache, resource-index, and texture-manifest paths from that report;
4. have a reviewing LLM construct one explicit prepared-pixel launch command;
5. perform one bounded main-menu, campaign, first-combat, save, and clean-exit lifecycle route.

Prepared pixels remain opt-in and not live-accepted until that lifecycle evidence is reviewed. Repeated benchmark runs remain blocked until behavioral acceptance passes.
