# First vanilla prepared-image adapter pilot

This protocol validates the opt-in `prepared-image-v1` compatibility plan against one exact reviewed Starsector build. It is not a default launch mode and it does not yet establish an acceleration claim.

## Supported target

The built-in target requires every reviewed identity:

```text
class             com/fs/graphics/TextureLoader
class SHA-256      d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
method             Ô00000(Ljava/lang/String;)Ljava/awt/image/BufferedImage;
source kind        STARSECTOR_CORE
source suffix      contents/resources/java/fs.common_obf.jar
loader class       jdk/internal/loader/ClassLoaders$AppClassLoader
loader name        app
```

A mismatch leaves the original class bytes active.

## Pilot cache scope

Do not prepare every profile image for the first run. Use a deterministic logical-path selection extracted from the reviewed startup JFR recording:

```bash
java -jar preflight.jar texture build \
  --game "/Applications/Starsector.app" \
  --cache-dir "$HOME/.starsector-preflight/pilot-cache" \
  --paths-file startup-images.txt \
  > texture-build.json
```

The JSON result contains the matching values required by the adapter:

- `cacheDirectory`
- `index` — selected `.spfi` resource index
- `manifest` — selected `.spfm` texture manifest

The subset index and manifest share a derived fingerprint. They must be supplied together. Paths outside the selection are normal cache misses and use Starsector's original loader.

## Baseline run

Launch with the adapter completely off:

```bash
java -jar preflight.jar run \
  --game "/Applications/Starsector.app" \
  --trace-dir pilot-results/off-1 \
  --no-scan \
  --no-adapter
```

Reach the chosen measurement milestone, then exit normally. For initial-startup tests, use the first stable main-menu display. For campaign-ready tests, load the same save and exit after the same campaign screen settles. Do not mix the two milestones in one result set.

## Enabled run

Launch with the exact subset artifacts:

```bash
java -jar preflight.jar run \
  --game "/Applications/Starsector.app" \
  --trace-dir pilot-results/enabled-1 \
  --no-scan \
  --adapter \
  --adapter-cache-dir "$CACHE_DIRECTORY" \
  --adapter-resource-index "$SUBSET_INDEX" \
  --adapter-texture-manifest "$SUBSET_MANIFEST"
```

`adapter.json` must show:

- `exactMatches: 1`
- `transformationsApplied: 1`
- `preparedImageCache.enabled: true`
- one or more hits for the pilot to exercise the cache path
- zero contained failures and zero internal errors

Any other outcome is evidence to review, not permission to widen activation.

## Run order

Use at least three alternating pairs for an engineering pilot:

```text
OFF 1
ENABLED 1
ENABLED 2
OFF 2
OFF 3
ENABLED 3
```

Alternating the order reduces warm filesystem/JVM and user-action bias. Use the same machine state, mod profile, launcher, milestone, and save action. Do not change mods or source files between cache preparation and the final run.

Five or more runs per mode are required before treating a timing ratio as stable.

## Compatibility limits

The compatibility bridge reconstructs a `BufferedImage` from verified prepared RGB/RGBA bytes. It skips encoded source reads and ImageIO decoding on a hit, but Starsector still performs its later raster conversion, derived-color analysis, texture allocation, OpenGL upload, and mipmap work.

The bridge declines prepared images when:

- the blob is missing, stale, corrupt, unsupported, or mismatched
- the winning source size or modification time differs from the selected index
- dimensions require padding or another transformation
- the raw prepared pixel payload exceeds 32 MiB
- the profile fingerprints disagree
- the bridge is disabled or reaches its internal error budget

Every declined lookup executes the untouched original decoder.

## Kill switch

Either setting prevents adapter transformer installation:

```text
PREFLIGHT_DISABLE_ADAPTER=1
-Dpreflight.adapter.disabled=true
```

Deleting the pilot cache is also safe. It does not modify Starsector, mods, saves, launcher files, or VM parameter files.

## Review package

Retain these files for every run:

```text
startup.jfr
summary.json
adapter.json          enabled runs
adapter-analysis.json enabled runs when generated
run.json
```

Also retain `texture-build.json`, the exact selection file, and its SHA-256. Compare behavior, hit/fallback counts, image-read evidence, class/JIT attribution, process duration to the controlled exit milestone, and any visible rendering differences before moving the plan out of draft.
