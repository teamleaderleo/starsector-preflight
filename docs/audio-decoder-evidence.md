# Audio decoder evidence collection

Prepared-audio reuse for Ogg/Vorbis remains disabled until the exact decoder path used by a real Starsector build has been observed and compared with the committed PCM oracle.

This evidence step is read-only. It does not decode or replace audio, does not modify the launcher or installation, and does not make Ogg/Vorbis inputs eligible for SPAU writes.

## What the probe records

A normal `--adapter-probe` launch writes `adapter-audio-decoder-signatures.json` beside `adapter.json`. The report observes only classes that were actually loaded during that process and whose internal names begin with one of these bounded prefixes:

- `org/newdawn/slick/openal/`
- `com/jcraft/jorbis/`
- `com/jcraft/jogg/`

For every retained source-and-loader identity it records:

- exact classfile SHA-256 and classfile major version;
- complete bounded method names, JVM descriptors, and access flags;
- raw and normalized code-source location;
- source kind and exact source archive SHA-256 when the source is a regular bounded file;
- source-hash failure detail when an archive cannot be hashed safely;
- defining classloader class and optional loader name.

The report is deterministic and bounded to 256 identities. It explicitly states:

```text
originalClassBytesRetained: true
automaticAdapterGenerated: false
decoderEquivalenceEstablished: false
preparedAudioWritesEligible: false
requiresHumanReview: true
```

An empty report means only that no matching decoder class was loaded during the observed process. It is not evidence that the game has no such decoder.

## Build the evidence branch

Use JDK 17 and Maven 3.9 or newer:

```bash
mvn --batch-mode --no-transfer-progress verify
```

The runnable agent and wrapper are produced at:

```text
preflight-cli/target/preflight.jar
```

## Run the read-only probe

Use the exact installation and launcher that will later be evaluated:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --game "/path/to/Starsector" \
  --adapter-probe
```

An explicit launcher may be used instead:

```bash
java -jar preflight-cli/target/preflight.jar run \
  --launcher "/path/to/the/existing/launcher" \
  --adapter-probe
```

Let the launch reach the main menu and allow menu music or another known Ogg/Vorbis sound to begin playing before exiting normally. Do not alter the installation merely to make a class appear.

The run directory should contain:

```text
run.json
profile.json
startup.jfr
summary.json
adapter.json
adapter-analysis.json
adapter-code-loader-signatures.json
adapter-audio-decoder-signatures.json
```

## Data to return for review

Return these text files from one run:

- `adapter-audio-decoder-signatures.json`
- `adapter-code-loader-signatures.json`
- `adapter.json`
- `adapter-analysis.json`
- `summary.json`
- `run.json`
- `profile.json`

The JFR recording is useful when method-level behavioral correlation is needed, but it may be substantially larger. Do not send Starsector JARs, game assets, mod files, saves, credentials, or other proprietary binaries. The signature report contains hashes and metadata, not source archive contents.

Before sharing, inspect the JSON for local paths or mod names you consider sensitive. Installation prefixes are evidence for local review but are not required in public issue text.

## Review gate after collection

The next implementation step remains blocked until review establishes all of the following:

1. The actual decoder implementation class and entry method are present with exact descriptors.
2. Its dependency classes, source archives, and defining loaders are identified without truncation.
3. Source archive hashing succeeded or the failure is understood and resolved safely.
4. A target-specific harness can invoke the exact shipped path without guessing constructor, stream, buffer, or error semantics.
5. The exact path matches the committed mono and stereo Ogg fixtures for PCM bytes, encoding, byte order, sample rate, channels, frame count, and malformed or unsupported behavior.

Only a later reviewed gate may make exact identities eligible for prepared-audio cache writes. Unknown, missing, changed, ambiguous, or partially observed identities must continue through the original decoder path.
