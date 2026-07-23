# Prepared-pixel comparison mutable-cache failure

Date: 2026-07-23

## Result

The replacement comparison stopped after its randomized first (`prepared`) half. It did not produce a valid pair.

Retained archive:

```text
prepared-pixel-main-menu-comparison-pilot-20260723-120310.tar.gz
sha256: 6c3c4f2d1220ce5e11f73649b5c9e1f11b30f3bf115c48fffdccc10733ed4729
repositoryHead: acd2c274d36d17513ecc245200d0fd6cd3adbf1a
order: prepared,compatibility
```

The archive was available at the reported Desktop path and its SHA-256 was independently verified. `/mnt/data` was not mounted in the inspection session.

The operator accepted the launcher, main menu, automatic notification, attached command, and clean exit, with no visible corruption. Automated acceptance failed only because the old guard required the complete census fingerprint to remain identical:

```text
before: 3c1fc13ee4b47a93d36122ee2804070dbacf43523a3d38df5cc531e35e4513fe
after:  5bf805bc6c8898c0f3c9eefb8808783cc405938286e00d44a349953046d9b1a1
```

Do not use the retained timing as a result. `samplesPerMode` remains one, `benchmarkAccepted` remains false, and coherent-direct remains opt-in.

## Actual mutable state

Preparation resolved the real mod root from the census report:

```text
/Applications/Starsector.app/mods
```

GraphicsLib was:

```text
mod id: shaderLib
directory: /Applications/Starsector.app/mods/zz GraphicsLib-1.12.1
cache: /Applications/Starsector.app/mods/zz GraphicsLib-1.12.1/cache
hash control: /Applications/Starsector.app/saves/common/shaderlib_cache_hash.data
```

The inspected cache contained 4,926 direct-child PNG files totaling 196,289,621 bytes. Every filename matched GraphicsLib's generated-normal-map grammar. There were no non-PNG files or nested cache directories.

GraphicsLib's installed source states that:

- generated normal maps are written to `GraphicsLib/cache`;
- cached maps are loaded and used when the mod/version hash matches;
- the cache is regenerated when that hash changes; and
- `shaderlib_cache_hash.data` is rewritten after auto-generation.

Therefore these files affect texture behavior and resource loading. They are runtime outputs, but they are not ignorable profile noise.

## Count-only classification was insufficient

The old profile guard reported only:

```text
files: +2
imageFiles: +2
bytes: +136
imageBytes: +136
```

That aggregate delta hid changed existing files. Twenty-seven cache entries had modification times during the prepared half and were 68-byte, 1×1 RGBA PNGs with the same SHA-256:

```text
c3087446afe87c5da27035fd77db71f3d9911966b3cd33a452f80d731fbf8159
```

Examples:

```text
sotf_wisp_lesser___SHIP_normal.png
tpc_weaver___TURRET0_normal.png
```

The prepared log contained 27 matching GraphicsLib normal-map load failures with the four-versus-sixteen-element buffer diagnostic. GraphicsLib writes the generated PNG before attempting to load it. The matching timestamps, file count, 1×1 dimensions, and installed source show that the prepared half rewrote existing live cache content as well as adding two files.

The repaired contract must consequently record per-file names, sizes, hashes, modes, and timestamps; aggregate mod counts are not sufficient.

## Deep preparation self-check

Two consecutive real `prepare --deep --verify-lookups` runs produced the same census fingerprint:

```text
5bf805bc6c8898c0f3c9eefb8808783cc405938286e00d44a349953046d9b1a1
```

A before/after metadata manifest of all files under `/Applications/Starsector.app/mods` had zero changed lines. Deep preparation itself did not change mod mtimes, sizes, inodes, or the census fingerprint.

## Repaired contract

The replacement guard uses a split state model:

- immutable inputs: enabled-mod configuration/order and every enabled-mod file except the exact `shaderLib/cache` subtree;
- one bounded mutable mod: exact direct-child generated normal maps under the resolved GraphicsLib cache plus `shaderlib_cache_hash.data`;
- unchanged core inputs: the discovered mission list and `afistfulofcredits` descriptor remain separately hashed.

It snapshots the pre-warmed mutable bytes outside the installation, requires explicit operator permission before any restore, and then:

1. restores and verifies the exact snapshot before each randomized half;
2. rejects immutable drift, multiple mutable mods, symlinks, subdirectories, and unrecognized cache files;
3. records bounded cache changes without hiding changed existing contents;
4. restores and verifies the exact baseline after the comparison; and
5. retains the snapshot if an unsafe cache shape prevents automatic recovery.

This preserves equivalent starting state without whitelisting arbitrary `shaderLib` image growth. It also preserves randomized order, compatibility rollback, automatic log readiness, core mission hashing, and `benchmarkAccepted=false`.
