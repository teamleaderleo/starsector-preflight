package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.PreparedAudio;
import dev.starsector.preflight.core.PreparedAudioCache;
import dev.starsector.preflight.core.PreparedAudioManifest;
import dev.starsector.preflight.core.PreparedAudioManifestIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Synthetic-only consumer of the production prepared-audio cache. */
public final class SyntheticPreparedAudioWorker {
    static final String DECODER_POLICY_IDENTITY = hash("synthetic-prepared-audio-decoder-v1");
    private static final String PROFILE_FINGERPRINT_SCHEMA = "synthetic-prepared-audio-profile-v1";
    private static final String STARSECTOR_BUILD_IDENTITY = hash("synthetic-starsector-build-v1");
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final List<Resource> RESOURCES = List.of(
            new Resource("effects/laser.bin", PreparedAudio.Policy.FULLY_DECODED_EFFECT),
            new Resource("effects/impact.bin", PreparedAudio.Policy.FULLY_DECODED_EFFECT),
            new Resource("music/theme.bin", PreparedAudio.Policy.STREAMED),
            new Resource("unsupported/format.bin", PreparedAudio.Policy.UNSUPPORTED));

    private SyntheticPreparedAudioWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: SyntheticPreparedAudioWorker <profile-root> <cache-root> <report.json>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(Path profileRoot, Path cacheRoot, Path reportPath) throws Exception {
        Path profile = profileRoot.toAbsolutePath().normalize();
        Path cache = cacheRoot.toAbsolutePath().normalize();
        TreeMap<String, Source> sources = loadSources(profile);
        String profileFingerprint = profileFingerprint(sources);
        TreeMap<String, PreparedAudioManifest.Entry> manifestEntries = new TreeMap<>();
        MessageDigest outputDigest = sha256Digest();

        int eligibleResources = 0;
        int eligibleDecoderCalls = 0;
        int cacheHits = 0;
        int cacheMisses = 0;
        int cacheCorruptFallbacks = 0;
        int cacheReadErrors = 0;
        int cacheWriteErrors = 0;
        int ineligibleLookups = 0;
        int streamedSelections = 0;
        int unsupportedSelections = 0;
        long preparedBytesServed = 0;
        long preparedBytesWritten = 0;

        for (Resource resource : RESOURCES) {
            Source source = sources.get(resource.logicalPath());
            if (source == null) throw new IOException("Missing synthetic audio source " + resource.logicalPath());
            if (!resource.policy().cacheEligible()) {
                PreparedAudioCache.Lookup ineligible = PreparedAudioCache.lookup(
                        cache,
                        source.sha256(),
                        DECODER_POLICY_IDENTITY,
                        resource.policy());
                if (ineligible.status() != PreparedAudioCache.Status.INELIGIBLE) {
                    throw new IOException("Synthetic ineligible policy produced " + ineligible.status());
                }
                ineligibleLookups++;
                if (resource.policy() == PreparedAudio.Policy.STREAMED) streamedSelections++;
                if (resource.policy() == PreparedAudio.Policy.UNSUPPORTED) unsupportedSelections++;
                manifestEntries.put(resource.logicalPath(), PreparedAudioManifest.Entry.ineligible(
                        resource.logicalPath(),
                        source.sha256(),
                        source.bytes().length,
                        source.modifiedMillis(),
                        resource.policy()));
                continue;
            }

            eligibleResources++;
            PreparedAudioCache.Lookup lookup = PreparedAudioCache.lookup(
                    cache,
                    source.sha256(),
                    DECODER_POLICY_IDENTITY,
                    resource.policy());
            PreparedAudio audio;
            switch (lookup.status()) {
                case HIT -> {
                    cacheHits++;
                    audio = lookup.audio();
                    preparedBytesServed = Math.addExact(preparedBytesServed, audio.pcmByteCount());
                }
                case MISS -> {
                    cacheMisses++;
                    eligibleDecoderCalls++;
                    audio = decode(source);
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, audio.pcmByteCount());
                    if (!write(cache, audio)) cacheWriteErrors++;
                }
                case CORRUPT -> {
                    cacheCorruptFallbacks++;
                    eligibleDecoderCalls++;
                    audio = decode(source);
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, audio.pcmByteCount());
                    if (!write(cache, audio)) cacheWriteErrors++;
                }
                case ERROR -> {
                    cacheReadErrors++;
                    eligibleDecoderCalls++;
                    audio = decode(source);
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, audio.pcmByteCount());
                    if (!write(cache, audio)) cacheWriteErrors++;
                }
                case INELIGIBLE -> throw new IOException("Eligible effect was classified ineligible");
                default -> throw new IOException("Unknown prepared-audio cache status " + lookup.status());
            }
            updateLengthPrefixed(outputDigest, resource.logicalPath().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(outputDigest, audio.pcmBytes());
            manifestEntries.put(resource.logicalPath(), PreparedAudioManifest.Entry.prepared(
                    resource.logicalPath(),
                    source.bytes().length,
                    source.modifiedMillis(),
                    audio));
        }

        PreparedAudioManifest manifest = new PreparedAudioManifest(
                profileFingerprint,
                STARSECTOR_BUILD_IDENTITY,
                DECODER_POLICY_IDENTITY,
                manifestEntries);
        Path manifestPath = cache.resolve("prepared-audio").resolve("synthetic-profile.spam");
        PreparedAudioManifestIO.write(manifestPath, manifest);

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-prepared-audio-v1");
        report.put("processId", ProcessHandle.current().pid());
        report.put("eligibleResources", eligibleResources);
        report.put("eligibleDecoderCalls", eligibleDecoderCalls);
        report.put("cacheHits", cacheHits);
        report.put("cacheMisses", cacheMisses);
        report.put("cacheCorruptFallbacks", cacheCorruptFallbacks);
        report.put("cacheReadErrors", cacheReadErrors);
        report.put("cacheWriteErrors", cacheWriteErrors);
        report.put("ineligibleLookups", ineligibleLookups);
        report.put("streamedSelections", streamedSelections);
        report.put("unsupportedSelections", unsupportedSelections);
        report.put("preparedBytesServed", preparedBytesServed);
        report.put("preparedBytesWritten", preparedBytesWritten);
        report.put("preparedOutputsSha256", HexFormat.of().formatHex(outputDigest.digest()));
        report.put("profileFingerprintSha256", profileFingerprint);
        report.put("manifestSha256", manifest.manifestSha256());
        report.put("manifestEntries", manifest.entryCount());
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, Json.object(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static TreeMap<String, Source> loadSources(Path profile) throws IOException {
        TreeMap<String, Source> sources = new TreeMap<>();
        for (Resource resource : RESOURCES) {
            Path sourcePath = profile.resolve(resource.logicalPath()).normalize();
            if (!sourcePath.startsWith(profile)
                    || Files.isSymbolicLink(sourcePath)
                    || !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Synthetic audio source is not a regular file: " + sourcePath);
            }
            byte[] bytes = readBounded(sourcePath);
            sources.put(resource.logicalPath(), new Source(
                    sourcePath,
                    bytes,
                    Hashes.sha256(bytes),
                    Files.getLastModifiedTime(sourcePath, LinkOption.NOFOLLOW_LINKS).toMillis()));
        }
        return sources;
    }

    private static PreparedAudio decode(Source source) {
        byte[] encoded = source.bytes();
        ByteBuffer pcm = ByteBuffer.allocate(Math.multiplyExact(encoded.length, 4))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (byte value : encoded) {
            short left = (short) ((Byte.toUnsignedInt(value) - 128) << 8);
            short right = (short) -left;
            pcm.putShort(left).putShort(right);
        }
        return new PreparedAudio(
                source.sha256(),
                DECODER_POLICY_IDENTITY,
                PreparedAudio.Policy.FULLY_DECODED_EFFECT,
                PreparedAudio.PcmEncoding.PCM_SIGNED,
                16,
                PreparedAudio.ByteOrder.LITTLE_ENDIAN,
                44_100,
                2,
                encoded.length,
                pcm.array());
    }

    private static boolean write(Path cache, PreparedAudio audio) {
        try {
            PreparedAudioCache.write(cache, audio);
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static String profileFingerprint(Map<String, Source> sources) {
        MessageDigest digest = sha256Digest();
        updateLengthPrefixed(digest, PROFILE_FINGERPRINT_SCHEMA.getBytes(StandardCharsets.UTF_8));
        sources.forEach((logicalPath, source) -> {
            updateLengthPrefixed(digest, logicalPath.getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, source.sha256().getBytes(StandardCharsets.UTF_8));
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] readBounded(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] bytes = input.readNBytes(MAX_SOURCE_BYTES + 1);
            if (bytes.length > MAX_SOURCE_BYTES) {
                throw new IOException("Synthetic audio source exceeds its byte limit: " + source);
            }
            return bytes;
        }
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hash(String value) {
        return Hashes.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private record Resource(String logicalPath, PreparedAudio.Policy policy) {
    }

    private record Source(Path path, byte[] bytes, String sha256, long modifiedMillis) {
        private Source {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
