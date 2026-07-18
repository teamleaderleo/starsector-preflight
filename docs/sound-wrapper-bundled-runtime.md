# Sound-wrapper observation with Starsector's bundled Java

A standalone system JDK can load the exact `fs.sound_obf.jar` identity yet fail when `sound.J` constructs a dependent game class. The observed macOS failure was `java.lang.ClassFormatError` under Java 21 for every valid fixture, while archive hashes, class hashes, defining loaders, and malformed-input stability all passed.

Use the bundled-runtime launcher when this occurs:

```bash
java -cp preflight-cli/target/preflight.jar \
  dev.starsector.preflight.cli.SoundWrapperObservationRuntimeLauncher \
  --game "/Applications/Starsector.app" \
  --jogg "/Applications/Starsector.app/Contents/Resources/Java/jogg-0.0.7.jar" \
  --jorbis "/Applications/Starsector.app/Contents/Resources/Java/jorbis-0.0.15.jar" \
  --output sound-wrapper-observation.json
```

The launcher searches only below the explicit game root for a bounded set of `bin/java` candidates. Canonical macOS bundle paths are preferred deterministically. An exact executable may be supplied with:

```text
--java <path-to-game-java>
```

It then starts the unchanged shaded CLI with the selected runtime. The normal sound-wrapper command consequently launches its child from the same `java.home`.

The launcher adds these content-safe fields to the completed or incomplete report:

- `childJavaExecutable`
- `childJavaSelectionSource`
- `childJavaExecutableSha256`
- `childJavaVersionOutputLength`
- `childJavaVersionOutputSha256`

The command remains evidence only. It does not edit the game, transform a game class, read or write prepared-audio cache entries, generate an allowlist, or enable live audio reuse.
