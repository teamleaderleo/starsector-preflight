# Starsector sound-wrapper observation

The installed-JOrbis gate proved the exact decoder archive and loader identities on the reviewed macOS installation, but its PCM output differed from the FFmpeg/libvorbis reference oracle. This command answers the narrower real question: what does Starsector's exact `sound/J` wrapper return for the same deterministic fixtures?

## Command

```bash
java -jar preflight-cli/target/preflight.jar \
  audio sound-wrapper-observe \
  --game "/Applications/Starsector.app" \
  --jogg "/Applications/Starsector.app/Contents/Resources/Java/jogg-0.0.7.jar" \
  --jorbis "/Applications/Starsector.app/Contents/Resources/Java/jorbis-0.0.15.jar" \
  --output sound-wrapper-observation.json
```

The parent process scans a bounded set of JARs below the explicit game root and requires exactly one archive containing both:

```text
sound/J.class
sound/F.class
```

It then launches a separate child JVM with the shaded Preflight JAR first, the exact sound-wrapper archive, the exact pinned Jogg/JOrbis archives, and the bounded sibling core-JAR set. The child requires the application classloader for all retained classes.

## What the report retains

For `sound/J`, `sound/F`, Jogg, JOrbis, and `Info`, the report records source archive SHA-256, class SHA-256, source path, and defining loader identity.

For each valid Ogg/Vorbis fixture it invokes:

```text
sound/J.o00000(Ljava/io/InputStream;)Lsound/F;
```

and compares the returned object's bounded payload candidates against direct installed-JOrbis PCM. It records only payload lengths and SHA-256 values. `byte[]`, `short[]`, and `ByteBuffer` fields are supported. Scalar fields equal to the direct channel count or sample rate are retained as metadata candidates for human review.

Unsupported, truncated, corrupt, and non-Ogg inputs run twice through the wrapper to retain stable return/failure class and stream-lifetime behavior. Literal exception messages are not included; only bounded length and SHA-256 evidence is retained.

## Fixed safety state

This is an observation harness, not an activation gate. Even a complete candidate match reports:

```text
equivalenceEstablished: false
requiresHumanReview: true
automaticAllowlistGenerated: false
identityPinnedForActivation: false
fullyDecodedEffectsEligible: false
streamedMusicEligible: false
preparedAudioWritesEnabled: false
cacheReadsEnabled: false
liveTransformEnabled: false
```

The command does not edit Starsector, install an agent, transform a game class, write SPAU data, read prepared-audio cache entries, or export decoded audio bytes.

## Review decision

A maintainer must review the exact returned `sound/F` field candidates and the invalid-input behavior. A later exact-pinned gate may be designed only after one payload field and the channel/rate fields are unambiguous. The observed archive and class hashes must not be promoted automatically into an activation allowlist.
