# 2026-07-16 real-install audio and startup evidence

Related: #48, #51, #54, #59, #75, #76, #77, #78, #79, #80.

## Evidence identity

- Probe source commit: `b532ffaed955eb14c1b8b15056c57eaf876e36cf`
- Uploaded result ZIP SHA-256: `a1a787bb0e818964c6a0391908047928fa2419ad9e72a6f7ae477c0a738a47e9`
- Run start/end: `2026-07-16T12:47:46.141672Z` / `2026-07-16T12:52:58.919563Z`
- Exit code: `0`
- Adapter mode: read-only `PROBE`
- JFR analysis window: `123.020 s`
- Profile fingerprint: `63166a7e7360eb4c926d974bb9232f55a1024ad1c5b41ec8c60171157362c6f0`
- Resolved mods: `75`
- Profile: 47,323 files / 1,799,634,328 bytes
- Images: 24,382 / 1,117,167,000 bytes
- Sounds: 1,396 / 485,036,607 bytes
- Loose Java: 3,838 / 24,987,975 bytes
- JARs: 78 / 47,989,974 bytes

The profile report retained two missing enabled IDs (`forge_production` and `unthemedweapons`) as diagnostics rather than failing the run.

## Decision

The run closes the **decoder identity discovery** part of the audio milestone. It proves the exact Jogg/JOrbis archives, loaded class identities, method surfaces, defining loader, and substantial real decoding activity.

It does not establish byte-for-byte decoder output equivalence, the exact Starsector `sound/*` object contract, streaming policy, or a safe live interception seam. Prepared-audio writes and live transformation therefore remain disabled.

A battle is not required to repeat this identity collection. Menu and campaign activity already exercised the decoder pipeline heavily.

## Exact audio decoder identities

The bounded report retained 32 identities with no truncation or diagnostics:

- 27 `com/jcraft/jorbis/*` codec classes
- 5 `com/jcraft/jogg/*` container classes
- every class was defined by `jdk/internal/loader/ClassLoaders$AppClassLoader`, loader name `app`
- every retained class had classfile major version 46

Exact Starsector-core archives:

- `jogg-0.0.7.jar` SHA-256 `ed7946260897d97c468a4749b3d9d5e436a268fa948bc32e75a7487130e89379`
- `jorbis-0.0.15.jar` SHA-256 `d049b2a1c6ddefde3a5cbff320c96fdd5aefa09b0d3bbea3fe44839f7e6713f9`

The retained method inventory includes the expected real decode stages:

- Jogg `SyncState.buffer/wrote/pageout`
- Jogg `StreamState.pagein/packetout`
- JOrbis `Info.synthesis_headerin`
- JOrbis `Block.synthesis`
- JOrbis `DspState.synthesis_init/synthesis_blockin/synthesis_pcmout/synthesis_read`

No `org/newdawn/slick/openal/*` identity was retained. This does not invalidate the decoder result; it indicates that the next useful target is Starsector's own `sound/*` wrapper contract rather than an assumed Slick OpenAL layer.

## CPU-domain evidence

Execution sampling is statistical evidence, not exact removable wall time.

| Category | Samples | Share |
| --- | ---: | ---: |
| Audio decode | 1,886 | 22.05% |
| Janino | 1,822 | 21.30% |
| Texture/image | 1,491 | 17.43% |
| Combined | 5,199 | 60.79% |

Top audio-attributed methods:

| Method | Samples |
| --- | ---: |
| `com/jcraft/jorbis/Mdct.mdct_kernel` | 398 |
| `sound/J.o00000(InputStream) -> sound/F` | 279 |
| `com/jcraft/jorbis/CodeBook.decode` | 194 |
| `com/jcraft/jorbis/Mdct.backward` | 150 |
| `com/jcraft/jorbis/CodeBook.decodevv_add` | 148 |
| `sound/void.return()` | 147 |

The two main audio worker threads contained 955 and 876 audio-decode samples, with another 54 on a third thread.

## Janino evidence from the same run

The run also materially advances #54:

- 589 class-definition events attributed to `org/codehaus/janino/JavaSourceClassLoader`
- 589 unique Janino-defined classes
- 497 Janino compilation events / 5,531.230 ms aggregate event duration
- exact complete-map seam `generateBytecodes(Ljava/lang/String;)Ljava/util/Map;`
- exact definition seam `defineBytecode(Ljava/lang/String;[B)Ljava/lang/Class;`
- exact lookup seam `findClass(Ljava/lang/String;)Ljava/lang/Class;`

Exact compiler archives:

- `janino.jar` SHA-256 `60f05562c22b6de06641a1f76148692ef336ad1f6712fe6a76f9e2611f766344`
- `commons-compiler.jar` SHA-256 `69094456b227ec07d908938c8f90eb57e51ca6d0e82f96475770af7224b508b2`

Important exact class identities include:

- `JavaSourceClassLoader` class SHA-256 `6b0eea7994ab4c314f1bc7cdefaa99b66897d500c2cad6fd2d97cd08b134c4b8`
- `JavaSourceIClassLoader` class SHA-256 `2083ff79c4d57f6d8d8277eaea701e0acd4f757cbdf597c705587475cc1b7f20`
- `Parser` class SHA-256 `8be516c10d28720e39f2c9fdc989634117f1eea2e3173793268746c8a2d6d84a`
- `UnitCompiler` class SHA-256 `fa941e557931e9ff3e0902a11c336777c435784103411046b15cb43b38af1931`

These identities are enough to bind the existing synthetic SPJB substrate to the reviewed compiler version. Live reuse remains blocked until transitive source/resource dependencies, ordered provider identity, parent loader, compiler settings, and protection-domain behavior are captured and proven complete. A context that cannot prove dependency completeness must be a miss.

## Graphics evidence and limitation

The run observed 1,491 texture/image samples and startup image activity through Starsector loading plus GraphicsLib `ShaderModPlugin.onApplicationLoad` and `TextureData.autoGenMissingNormalMaps`.

The generic adapter retained 500 candidates and truncated the candidate set but registered zero transformation plans. This result did not emit the dedicated `adapter-bytecode-shape.json` required by #51. It therefore does not replace the dedicated `TextureLoader` shape probe or the real prepared-image OFF-versus-ENABLED pilot.

## Gate table

| Gate | Status |
| --- | --- |
| Exact Jogg/JOrbis archive identity | PASS |
| Exact decoder class/method inventory | PASS |
| Exact decoder loader identity | PASS |
| Real-profile decoder activity | PASS |
| Exact Starsector `sound/*` wrapper identities | OPEN |
| PCM and metadata equivalence | OPEN |
| Streaming policy equivalence | OPEN |
| Prepared-audio writes | BLOCKED |
| Live audio transformation | BLOCKED |
| Real acceleration claim | BLOCKED |

## Ordered next work

### 1. Exact Starsector sound-loader contract — #75

Capture bounded exact class/source/loader hashes, all descriptors, field effects, call edges, exception regions, and dataflow around:

- `sound/J`
- `sound/F`
- `sound/ooOO`
- `sound/D`
- `sound/Sound`
- `sound/void`
- `com/fs/starfarer/loading/A`

The primary seam is `sound/J.o00000(InputStream) -> sound/F` and its consumers. Do not export class bytes, asset contents, or proprietary literal strings.

### 2. Installed-JOrbis equivalence — #75

Decode the committed deterministic Ogg/Vorbis fixtures through the exact installed archive identities. Require byte-for-byte PCM equality and exact `sound/F` metadata equality. Add silence, clipping, packet-boundary, truncated/corrupt, and supported mono/stereo-rate cases. Preserve original exception behavior.

### 3. Target-specific prepared-audio wrapper

Only after the first two gates pass:

- key by encoded source SHA-256 plus exact decoder, wrapper, archive, loader, Starsector build, and policy identities;
- reconstruct the reviewed fully decoded effect contract only on an exact SPAU hit;
- execute the untouched original decoder on every miss/error/mismatch;
- keep streamed music ineligible unless exact evidence proves otherwise;
- enforce memory/direct-buffer ceilings and a session error budget.

### 4. Janino dependency binding — #77

Capture every source/resource lookup and complete transitive dependency set around the exact `generateBytecodes(String) -> Map` seam, then bind the existing production SPJB wrapper to this reviewed installation.

### 5. Dedicated graphics shape run — #78

Finish and run the exact `TextureLoader` bytecode/dataflow shape collector. Human-review a lower upload-ready prepared-pixel seam before any new rewrite.

### 6. Unified pre-launch build — #76

The intended user-facing result is one exact-profile pre-launch preparation step:

1. scan/fingerprint installation and mod order once;
2. build or validate the provider index;
3. prepare selected images;
4. prepare only equivalence-proven decoded effects;
5. compile/capture exact-key generated class maps;
6. write one authenticated launch context;
7. start Starsector with independently gated adapters and kill switches.

Do not aggregate runtime consumers until at least one live subsystem has passed a reviewed real safety A/B pilot.

### 7. Repeated OFF-versus-ENABLED evidence — #80

At least five launches per mode on the same machine/profile/save action. Report time to menu and save readiness separately, exact hit/miss/fallback/error counters, bytes/work bypassed, preparation cost, cache sizes, and JFR attribution changes. Only this stage can support an acceleration claim.

## Final conclusion

The evidence collection succeeded. It unblocks the next audio contract/equivalence work and substantially advances exact Janino binding. It does not enable an optimization yet, and the repository should continue to report that honestly. The shortest safe path forward is #75, followed by a fail-open target-specific prepared-audio wrapper and real A/B evidence.