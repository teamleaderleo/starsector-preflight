# Community Evidence and Benchmark Additions (operational)

Status: **operational evidence, not a formal benchmark.** This records a Reddit/forum
sweep and the concrete benchmark and diagnostic additions it justifies. Community reports
supply workload examples, compatibility failures, and test-case ideas; they are weaker
than installed-build measurement for any technical claim.

## Evidence order

Rank evidence when it conflicts:

1. Installed-build measurements and exact class evidence (Preflight JFR + contracts).
2. Fractal Softworks developer statements.
3. Current mod documentation and reproducible issue reports.
4. Several independent community reports showing the same pattern.
5. Individual anecdotes — used only as test-case generators.

## Launch-time envelope: mod count is a weak predictor

A September 2024 thread collected launch reports spanning roughly **90 seconds to
30–45 minutes**. Representative points (tier-4/5 anecdote):

- 15–20 min to menu, then 5–10 min to load a save.
- A 45-min record cut to ~4 min after a hardware upgrade, 205 mods.
- ~11 min with 163 mods, Java 23, Ryzen 7 3700X, NVMe.
- ~2 min with ~70 mods, Java 23, SSD.
- 80+ mods taking 5–10 min.
- Steam Deck ~1 min with 100+ mods.
- A separate thread: 13 min for 209 mods, Java 23 launcher.

Mod **count** alone predicts launch time poorly. The variables that actually matter:

- Number and size of images and sounds.
- Loose Java source count.
- Number of factions and content-heavy mods.
- CPU generation and single-thread throughput.
- Java runtime and arguments.
- Storage and filesystem cache state.
- Launcher type.
- Save age and campaign complexity.
- Background load and memory pressure.

Preflight already records hardware, OS, Java vendor/full version, Starsector version, FR
build, JVM arguments, mod-profile fingerprint, background-load state, and memory pressure,
and its campaign comparison rejects mixed launcher kinds and mixed JVM identities. **The
existing identity-heavy design is correct; these reports explain why those identities must
stay mandatory** — the community range is unusable precisely because it mixes all of the
variables above.

## Runtime and launcher campaigns

Starsector 0.98a ships Java 17 by default (community/dev-tier guidance: start with the
bundled runtime, then introduce alternate runtimes or Fast Rendering only when a measured
problem remains). FR is reported as especially useful on low-end integrated graphics and
extreme mod counts, but also carries version-specific stutter/launch/platform/combat/save
issues that later releases reportedly fixed. **FR is a distinct supported compatibility
target, not a universal baseline.**

Run one campaign report per combination, then compare campaign summaries at a higher
level (never merge identities into one campaign):

```text
A. Vanilla launcher + bundled Java 17
B. Vanilla launcher + selected alternate Java runtime
C. FR launcher + its supported Java runtime
D. Each of the above with Preflight disabled
E. Each of the above with Preflight enabled and warm
```

Record separately per combination:

- Process start → main menu.
- Main menu → campaign readiness.
- Campaign → representative combat readiness.
- Save duration; load-save duration.
- Clean-exit rate.
- Peak heap and direct memory; peak system memory.
- Texture-cache hits; Janino compilation events; audio-decoder samples.
- Render-thread and game-thread samples.
- Any startup, save, rendering, or shutdown failure.

Retain the **exact FR binary hash / build identity** in every run — a displayed version
string is weak protection when users replace launcher files by hand.

## Community-envelope report

`doctor` / profile reports can situate a measured profile against reported community
workloads **without presenting community data as a benchmark**:

```text
Profile:
  77 enabled IDs
  24,382 images
  1,396 sounds
  3,838 loose Java files
  78 JARs

Measured warm launch:
  4m 12s

Community context:
  Reports for large profiles range from a few minutes to several dozen minutes.
  Comparisons across users contain different hardware, runtimes, launchers,
  saves, and mod contents.
```

## Combat-only slowdown often means VRAM pressure

A July 2026 troubleshooting thread describes smooth campaign performance with severe
combat slowdown, ultimately attributed to VRAM exhaustion from a large modpack; reducing
VRAM demand and changing GraphicsLib settings helped. This is one recent case but matches
expected combat-only texture/effect pressure, and older reports associate GraphicsLib
content with combat slowdown (with unresolved config/version variables). This motivates
the first-class VRAM estimator specified in
[asset-quality-track.md](asset-quality-track.md) and reinforces that in-game FPS is a
VRAM/compression problem for this project, not an upscaling one.

## MagicLib and LunaLib: profile the caller, not the package

The sweep produced crashes from version mismatches, dependency problems, and
consuming-mod behavior — but **no consistent evidence that MagicLib or LunaLib alone
dominates launch or combat cost.** Shared libraries appear in a stack because they provide
the API through which another mod does the work. Attribute cost to the concrete feature
and caller. Preflight should record:

- Library version and hash.
- Which enabled mods declare each library.
- Sampled methods inside each library.
- Immediate callers of those methods.
- Per-frame call counts where measurable.

## What this changes

The sweep strengthens the existing identity-heavy benchmark design and justifies two
concrete additions: a **VRAM / decoded-texture estimator** (see the quality track) and
**separate runtime/launcher campaign orchestration** (the A–E matrix above). Everything
here is tier-4/5 evidence: use it to generate regression cases and test matrices, and
reserve performance claims for repeatable runs with exact identities.
