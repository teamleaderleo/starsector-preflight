# 2026-07-18 real texture preparation and compatibility pilot

Related: #5, #20, #45, #78, and #80.

## Scope and decision

This pass exercised the existing texture preparation and exact-gated compatibility adapter against the real macOS Starsector 0.98a-RC8 installation. It did not modify the installation, mods, launcher, saves, or VM parameter files. Cache and report writes were isolated under `/private/tmp`.

The offline preparation gate now passes. A first compatibility-adapter exercise stopped at the launcher. A follow-up advanced into the title-screen render path but ended in a fatal `LayeredRenderer.renderExcluding` null-iterator exception. The adapter reported no contained or internal failure, but that does not establish that the adapter was uninvolved. The live compatibility gate therefore remains failed pending an identical adapter-OFF control and root-cause review. Prepared-pixel mode was not activated. No acceleration claim is supported.

## Defects found by the first preparation

The first full-profile preparation exited `5` and retained report SHA-256 `3ea2108f241e442e12fedb784334d8bd3a7c37ce329394719278aacd077e84e2` (79,202 bytes).

It exposed three repository defects:

1. The macOS core-resource root is `Contents/Resources/Java`, but discovery only accepted a nested `starsector-core` directory. The resulting index was mod-only.
2. The resource equivalence benchmark compared path spellings. On the case-insensitive macOS filesystem, 25 indexed paths with retained source casing referred to the same files as lower-case baseline paths but were reported as mismatches.
3. Three WebP resources had no installed ImageIO reader. Safe original-loader fallbacks existed, but the builder counted them as fatal blob failures and failed the entire texture stage.

The fix recognizes the real macOS core layout, compares ordered filesystem provider identity with `Files.isSameFile`, and reports unsupported ImageIO formats as explicit skipped fallbacks rather than prepared entries or fatal failures. Corrupt or failed supported images remain failures.

## Fixed offline preparation

The fixed preparation exited `0` in 39,851 ms. The bounded report is 72,093 bytes with SHA-256 `ab480c381b7e8db7a8cb5ed7b8722ad66758f6f2db94741a038c54fb56e53eb0`.

- profile: 44,670 files, 23,512 images, 1,090,287,066 encoded image bytes;
- resolved enabled mods: 71; unresolved enabled IDs retained as diagnostics: 6;
- resource roots: 72, including exact macOS vanilla core;
- resource entries/providers: 48,447 / 50,183;
- texture candidates: 26,638;
- validated manifest entries: 26,635;
- unique content groups: 24,706;
- prior-cache hits/new builds: 21,710 / 2,993;
- deduplicated entries: 1,932;
- failed blobs: 0;
- unsupported WebP fallbacks: 3;
- validated prepared pixel bytes: 4,877,252,608;
- validated prepared blob bytes: 4,880,216,968;
- disposable cache disk use after both passes: approximately 4.6 GiB;
- resource lookup comparison: 10,000 queries, 0 mismatches;
- classpath lookup comparison: 10,000 queries, 0 mismatches.

The retained preparation report described the exact-gated compatibility and prepared-pixel modes as integrated but opt-in. Preparation enabled neither mode and made no acceleration claim. After the title-screen failure and exact lower-path review, the branch now reports `liveAdapterIntegrated: false` and `liveAdapterEnabledByPreparation: false` until compatibility-v2 is accepted; prepared-pixels is separately fail-closed.

## Bounded compatibility exercise

The reviewed exact target remained:

```text
class:       com/fs/graphics/TextureLoader
class SHA:   d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
archive:     Contents/Resources/Java/fs.common_obf.jar
archive SHA: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
loader:      jdk/internal/loader/ClassLoaders$AppClassLoader, name=app
plan:        texture-compatibility-v1
```

The exact evaluation contained no identity problems and applied one transformation. The launcher exercised the retained original texture construction and OpenGL path while the compatibility adapter reported:

- attempts/hits: 20 / 20;
- misses/fallbacks: 0 / 0;
- bytes served: 1,654,859;
- corruption/quarantine/internal errors: 0 / 0 / 0;
- circuit breaker: inactive;
- prepared-pixel direct attempts: 0;
- contained transformation failures: 0.

The 59.218-second JFR retained 47 texture/image execution samples out of 268 total samples. Sampling is prioritization evidence, not removable wall time.

The Mac automation bridge could not address the running Java launcher because two windows shared `com.azul.zulu.java`. The Preflight-owned process was therefore interrupted at the launcher, producing exit `130`; `run.json` correctly has no normal end timestamp or final exit code. This is a partial compatibility result, not a clean game-start acceptance.

Artifact identities:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `adapter.json` | 164,837 | `d23721fba297296e1a8ce3a9078ccf30d7c028aa1ce7e8b3aa999dcbaa955f5c` |
| `startup.jfr` | 1,377,423 | `3311913850a9e6e5415a827060563db9eb03aa4eabb0c5e9a734036c714c85b0` |
| `summary.json` | 121,518 | `a99b938a16bb327b25cdc027340444bad83559e64a1e8e40e2fe7e41a533a43d` |
| `adapter-analysis.json` | 88,542 | `e83fd0052792d60107b7be120c58e1cdf6133477efc6322e73d7bf8713a9e723` |

## Title-screen failure evidence

The follow-up used a new trace directory and the same exact compatibility target. After the user pressed **Play Starsector**, startup loaded the enabled profile and entered `TitleScreenState.render`. The main-menu music started, then the game reported:

```text
java.lang.NullPointerException: Cannot invoke "java.util.Iterator.hasNext()" because "<local8>" is null
    at com.fs.graphics.LayeredRenderer.renderExcluding(Unknown Source)
    at com.fs.starfarer.combat.CombatEngine.render(Unknown Source)
    at com.fs.starfarer.title.TitleScreenState.render(Unknown Source)
```

The compatibility adapter recorded:

- one exact `TextureLoader` transformation;
- 17,080 attempts, 17,014 hits, and 66 original-loader fallbacks;
- 2,271,002,292 bytes served;
- fallback reasons: 4 missing entries and 62 changed sources;
- zero corruption, quarantine, internal errors, contained transformation failures, or circuit-breaker activation;
- zero prepared-pixel attempts and zero active or pending prepared-pixel buffers.

These counters show that the adapter's guarded paths behaved as designed from its own perspective. They do not exonerate the reconstructed-image path from a downstream semantic incompatibility. The retained log history contains no earlier instance of this exact `LayeredRenderer.renderExcluding` exception, but that absence is not a baseline control.

The launcher shell returned exit `0` after the fatal game-thread failure, so `run.json` also records `0`. That value is not a clean-exit result. Future acceptance must correlate the launcher exit with fatal-log or explicit game-lifecycle evidence instead of relying on the shell exit alone.

The follow-up branch now snapshots the Starsector log set before launch and inspects at most eight changed log files and 16 MiB written during that run. A fatal classification requires both the exact `CombatMain` error logger and the top-level `CombatMain.main` stack frame. It preserves the raw launcher status as `launcherExitCode`, records bounded lifecycle evidence, and returns effective exit `6` when the launcher returned `0` after a fatal game-thread stack. Unit coverage includes pre-existing evidence, recoverable logger errors, rotation, and file-limit ordering; a packaged CLI integration test proves the zero-launcher-exit regression with adapter mode OFF and no texture cache context.

Follow-up artifact identities:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `adapter.json` | 400,852 | `132f8a668d60c1339b2e446631de157f2f6005a7c6d2fb2088594bb866d840fe` |
| `adapter-analysis.json` | 132,653 | `7bbda7b9c11b74693e528f6d3d105c69d6243b98cf13e88abede6c8b4fd9b9d6` |
| `summary.json` | 166,416 | `4d6e5f63a58a086dad1d27dfb2c82827a88ac0aa103350a2a8b756f29b0f680c` |
| `startup.jfr` | 8,827,802 | `cda62bbc32e66722275902d491ce8848c8a71063577a3dd9038441b4a920217a` |
| `run.json` | 1,263 | `9042ea1c9c524431ea57fa6dc02245d4315954f846167ce3f835de25fbdabfd1` |

## Adapter-OFF title-screen control

After the fatal-lifecycle reporting fix passed focused and full-reactor verification, the same installed game and enabled-mod list were launched with explicit adapter mode OFF. No texture cache directory, manifest, or index was supplied; no adapter report was created; and no proprietary class transformation was installed.

The user pressed **Play Starsector**, reached the main screen, and exited through the normal game UI without starting or saving a campaign. The finalized run reported:

- effective exit: `0`;
- raw launcher exit: `0`;
- outcome: `COMPLETED`;
- fatal lifecycle matches: 0;
- adapter mode: `OFF`;
- texture cache, manifest, and index: null;
- adapter report: absent.

The lifecycle reader examined the newest 16 MiB written to one Starsector log and reported truncation because the run produced more output than that ceiling. Absence of a fatal marker is therefore bounded evidence, not a claim about every log byte. It is paired here with the observed main screen and user-performed normal exit.

Both runs resolved 71 mods and retained the same six missing enabled IDs. The profile fingerprints were not identical: generated image data increased the later census by 22,920 bytes, and the launcher used a 6 GiB maximum heap in the OFF control versus 8 GiB in the enabled failure. This prevents treating the pair as benchmark-quality timing evidence. It does not provide an alternative explanation for the adapter-enabled `LayeredRenderer` null iterator, so the compatibility path remains implicated and must stay disabled until reduced-surface root-cause work is complete.

Control artifact identities:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `run.json` | 1,022 | `a2ee4fd1cb7505dc1fad1c994f9727e87403fcaba93f81ec34095fbea230e7c2` |
| `profile.json` | 66,907 | `29c91a818c4923d89e7516f62e7f7173a40ea1dd993a70fd93be4aea16cd9559` |
| `summary.json` | 137,158 | `8bda1967eb0683952c43f9803da8ba4bdb5fe30edddd6d9c63ba856b822474e1` |
| `startup.jfr` | 7,113,061 | `3b9db0ee499ba2124f56b2b88474ef8185a59de1db052c253e7b6c01f08cc32a` |

## Confirmed compatibility-v1 contract defect

Read-only disassembly of the exact installed `TextureLoader` and its `com.fs.graphics.L` collaborator identified a skipped lifecycle handoff. The original decoded-image method first calls:

```text
com.fs.graphics.L.class(String) -> BufferedImage
```

That method is the game's asynchronous image-preloader rendezvous. For requested paths it waits for a background result, removes the result from the concurrent map, and returns it. Only a `null` result continues to the direct resource-stream and `ImageIO.read` branch.

The failed `texture-compatibility-v1` wrapper called Preflight before the original method. A prepared hit therefore bypassed the rendezvous completely. With 17,014 hits, this changed load ordering and left Starsector's preload path to proceed independently. The adapter's zero internal-error counters could not detect that external contract violation. This is a concrete architectural defect and a plausible cause of the title-render failure; the live run alone does not prove it was the only cause.

The branch replaces that design with `texture-compatibility-v2`:

1. execute the exact original preloader call;
2. return the preloaded image when present;
3. attempt the prepared lookup only after a preloader miss;
4. continue into the original direct-decode instructions on a prepared miss;
5. decline transformation if the exact handoff shape is absent or ambiguous.

The separate prepared-pixel prototype had the same bypass, so its synthetic version-2 plan now follows the same ordering and retains a direct-decode-only clone for the rare carrier-failure fallback. Packaged child-JVM regression tests use a controllable preloader stand-in and prove that a preloaded image wins, Preflight records zero attempts in that case, warm prepared hits occur only after one preloader call, and cold/corrupt paths execute direct decode once.

A read-only transformation check against the exact installed class bytes produced:

```text
compatibility-v2=true
prepared-pixels-v2=false
```

The lower plan declines at its pre-existing reviewed-color-sink matcher: the installed converter stores derived colors on `TextureLoader` and later transfers them through texture-object setter methods, while the repository fixture modeled direct texture-object field writes. No live prepared-pixel transformation should be claimed from the synthetic tests. Prepared-pixels remains fail-closed and requires a separate exact dataflow review and fixture correction. Compatibility also remains disabled pending full diff review and controlled real-install acceptance.

## Next gate

1. Build a smaller startup working-set manifest rather than treating the approximately 4.6 GiB all-image cache as the default pilot policy.
2. Keep compatibility and prepared-pixel modes disabled while compatibility-v2 receives full reactor, cross-platform, exact-bytecode, and diff review.
3. Only after that review, repeat compatibility mode through main menu, representative campaign readiness, and normal exit; require one exact version-2 transformation, successful hits after preloader misses, bounded fallbacks, no contained failures, and no fatal lifecycle evidence.
4. Exercise the kill switch and one controlled corrupt entry, proving zero transformation and original-path fallback respectively.
5. Re-review the exact installed lower conversion/color dataflow and replace the synthetic prepared-pixel fixture before considering a live prepared-pixel route; require the exact installed-byte transformation check to pass first.
6. After behavioral acceptance, perform the five-run OFF-versus-ENABLED protocol tracked by #80.
