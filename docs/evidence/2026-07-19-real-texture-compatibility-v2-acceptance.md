# Real texture compatibility-v2 acceptance — 2026-07-19

## Scope and conclusion

This is bounded behavioral acceptance evidence for the manually compiled `texture-compatibility-v2` target against the real macOS Starsector 0.98a-RC8 installation. It is not benchmark evidence and does not activate any automatic allowlist or prepared-pixel path.

After correcting the external enabled-mod profile, the user launched through Preflight, clicked **Play Starsector**, reached the main screen, and closed the game normally. The launcher and effective process status were both `0`, the lifecycle classifier reported `COMPLETED`, and the bounded changed-log inspection found no fatal signature.

The accepted run applied one exact compatibility-v2 transformation and served 4,926 prepared decoded-image hits totaling 554,585,903 bytes. Three requests were absent from the manifest and followed the retained original path. There were no contained failures, corruptions, quarantines, internal errors, source-binding rejections, or circuit-breaker activation.

## Exact adapter evidence

- adapter mode: `ENABLED` (explicit pilot only);
- texture mode: `COMPATIBILITY`;
- plan: `texture-compatibility-v2`;
- exact matches: 1;
- transformations applied: 1;
- contained failures: 0;
- source binding rejected: 0;
- attempts: 4,929;
- hits: 4,926;
- fallbacks: 3 (`entry-missing`);
- bytes served: 554,585,903;
- corruptions/quarantines/internal errors: 0/0/0;
- prepared-pixel attempts, carriers, buffers, hits, and bypassed bytes: all 0.

The exact compiled target retained the reviewed class, archive, loader, method-set, and source identities:

```text
class:           com/fs/graphics/TextureLoader
class SHA-256:   d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50
archive:         Contents/Resources/Java/fs.common_obf.jar
archive SHA-256: 10d89e113f6d1627cc7bc90b692e8a7f450fdd820c5a4ac5edaecd6710afe708
loader class:    jdk/internal/loader/ClassLoaders$AppClassLoader
loader name:     app
```

The run used profile fingerprint `bf562ba913b2f40472fdc706cc8293648d6b63f0096d131d074e5c6f9a115d51`, with 71 resolved mods and no missing enabled IDs.

## Lifecycle bounds

The lifecycle reader examined 16 MiB across two changed logs. The evidence is marked truncated because the changed log set exceeded that ceiling. No bounded fatal matches or inspection problems were recorded. That negative result is paired with the user's direct observation of the main screen and normal close; it is not a claim about unexamined log bytes.

## Telemetry correction found during review

The accepted adapter report nested `preparedPixels.ready=true` under the shared compatibility cache even though compatibility mode was selected and every prepared-pixel activity counter was zero. This was a reporting/plan-readiness defect: `TexturePreparedPixelRuntime.ready()` reflected only shared cache readiness, not selection of the prepared-pixel mode.

The follow-up fix makes prepared-pixel readiness require both the exact shared cache configuration and explicit `PREPARED_PIXELS` selection. Compatibility selection now reports prepared pixels not ready, refuses direct prepared-pixel calls, and cannot expose that plan through the transformation registry. Session reset clears selection. The exact prepared-pixel transformation remains fail-closed on the installed class.

## Performance interpretation

The OFF control and this accepted run are not timing comparators. Their trace durations and user dwell differed, and GraphicsLib generated substantially more normal-map work in the accepted run. The samples are directionally consistent with less image/raster work, but they do not support a wall-clock percentage claim.

This run establishes that compatibility-v2 can deliver thousands of exact cache hits while preserving the main-screen lifecycle in this profile. The remaining lower `BufferedImage -> ByteBuffer` conversion is still visible in samples. The prepared-pixel prototype is intended to address that lower work, but it must remain disabled until its exact installed color-transfer dataflow is modeled and manually reviewed.

## Artifact identities

| Artifact | SHA-256 |
| --- | --- |
| `run.json` | `f91a278c453d928484a44d12b83f7fc54757b0df733c37e0088f4e9d7546f2bb` |
| `adapter.json` | `5f43962359b7211964eb8769cc2c858bce20db10684d6b9e1df271624891dc19` |
| `adapter-analysis.json` | `042dd58d99fef0743328747ddd1c8467f3c8cdacc475dfe8e71f043f32427ace` |
| `profile.json` | `7bf7f56deb7f8dedcbf24265991d1331a6f661381e8ebcb9457f52fa227f5bf6` |
| `summary.json` | `709b3b332875d350e5106461c86614fdb365e8c35e26e37d5f239b646babfdaf` |
| `startup.jfr` | `570304102c418a7b6aac68f7a9957f6796e58011d382b31d28463b3d6e398b93` |

The raw artifacts remain local because they contain installation paths and profile details. This bounded report retains the manually reviewed conclusions and cryptographic identities.

## Safe repeatable pilot

The follow-up CLI adds an explicit `--texture-auto` convenience path for compatibility mode. It does not prepare, mutate, or select a merely recent cache. At launch it builds the current read-only resource identity, resolves only the matching fingerprint-named index and manifest, checks exact roots and provider maps against the selected installation, fully validates provider metadata, hashes both selected artifacts, and passes those exact paths to the agent. A changed profile or generated resource fails closed with an instruction to prepare again.

Automatic allowlist generation, analysis eligibility, prepared-pixel activation, audio cache reads/writes, and live audio transformation remain disabled.
