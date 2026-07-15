package dev.starsector.preflight.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic profile manifest for prepared, streamed, and unsupported audio resources. */
public final class PreparedAudioManifest {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_ENTRIES = 100_000;
    public static final int MAX_LOGICAL_PATH_CHARS = 4_096;
    public static final long MAX_ENCODED_SOURCE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final String IDENTITY_SCHEMA = "starsector-preflight-prepared-audio-manifest-v1";

    private final String profileFingerprintSha256;
    private final String starsectorBuildSha256;
    private final String decoderPolicyIdentitySha256;
    private final NavigableMap<String, Entry> entries;
    private final String manifestSha256;

    public PreparedAudioManifest(
            String profileFingerprintSha256,
            String starsectorBuildSha256,
            String decoderPolicyIdentitySha256,
            Map<String, Entry> entries) {
        this.profileFingerprintSha256 = canonicalHash(profileFingerprintSha256, "profileFingerprintSha256");
        this.starsectorBuildSha256 = canonicalHash(starsectorBuildSha256, "starsectorBuildSha256");
        this.decoderPolicyIdentitySha256 = canonicalHash(
                decoderPolicyIdentitySha256,
                "decoderPolicyIdentitySha256");
        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Prepared audio manifest exceeds " + MAX_ENTRIES + " entries");
        }
        TreeMap<String, Entry> ordered = new TreeMap<>();
        for (Map.Entry<String, Entry> mapEntry : entries.entrySet()) {
            Entry entry = Objects.requireNonNull(mapEntry.getValue(), "entry");
            String key = ResourceIndex.normalizeLogicalPath(mapEntry.getKey());
            if (!key.equals(entry.logicalPath())) {
                throw new IllegalArgumentException("Prepared audio manifest key differs from entry path: " + key);
            }
            if (entry.policy().cacheEligible()) {
                String expected = PreparedAudioCache.cacheKeySha256(
                        entry.sourceSha256(),
                        this.decoderPolicyIdentitySha256,
                        entry.policy());
                if (!expected.equals(entry.cacheKeySha256())) {
                    throw new IllegalArgumentException("Prepared audio entry cache key is invalid: " + key);
                }
            } else if (!entry.cacheKeySha256().isEmpty()) {
                throw new IllegalArgumentException("Ineligible prepared audio entry contains a cache key: " + key);
            }
            if (ordered.put(key, entry) != null) {
                throw new IllegalArgumentException("Duplicate prepared audio logical path: " + key);
            }
        }
        this.entries = Collections.unmodifiableNavigableMap(ordered);
        this.manifestSha256 = computeIdentity();
    }

    public String profileFingerprintSha256() {
        return profileFingerprintSha256;
    }

    public String starsectorBuildSha256() {
        return starsectorBuildSha256;
    }

    public String decoderPolicyIdentitySha256() {
        return decoderPolicyIdentitySha256;
    }

    public NavigableMap<String, Entry> entries() {
        return entries;
    }

    public int entryCount() {
        return entries.size();
    }

    public long preparedEntryCount() {
        return entries.values().stream().filter(entry -> entry.policy().cacheEligible()).count();
    }

    public long streamedEntryCount() {
        return entries.values().stream().filter(entry -> entry.policy() == PreparedAudio.Policy.STREAMED).count();
    }

    public long unsupportedEntryCount() {
        return entries.values().stream().filter(entry -> entry.policy() == PreparedAudio.Policy.UNSUPPORTED).count();
    }

    public String manifestSha256() {
        return manifestSha256;
    }

    private String computeIdentity() {
        MessageDigest digest = sha256Digest();
        updateString(digest, IDENTITY_SCHEMA);
        updateString(digest, profileFingerprintSha256);
        updateString(digest, starsectorBuildSha256);
        updateString(digest, decoderPolicyIdentitySha256);
        updateInt(digest, entries.size());
        for (Entry entry : entries.values()) {
            updateString(digest, entry.logicalPath());
            updateString(digest, entry.sourceSha256());
            updateLong(digest, entry.sourceBytes());
            updateLong(digest, entry.sourceModifiedMillis());
            updateInt(digest, entry.policy().id());
            updateString(digest, entry.cacheKeySha256());
            Metadata metadata = entry.metadata();
            updateInt(digest, metadata == null ? 0 : 1);
            if (metadata != null) {
                updateInt(digest, metadata.sampleRateHz());
                updateInt(digest, metadata.channels());
                updateLong(digest, metadata.frameCount());
                updateLong(digest, metadata.sampleCount());
                updateInt(digest, metadata.encoding().id());
                updateInt(digest, metadata.bitsPerSample());
                updateInt(digest, metadata.byteOrder().id());
                updateInt(digest, metadata.pcmBytes());
                updateString(digest, metadata.pcmSha256());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String canonicalHash(String value, String name) {
        Objects.requireNonNull(value, name);
        Hashes.decodeSha256(value);
        return value.toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    public record Entry(
            String logicalPath,
            String sourceSha256,
            long sourceBytes,
            long sourceModifiedMillis,
            PreparedAudio.Policy policy,
            String cacheKeySha256,
            Metadata metadata) {
        public Entry {
            logicalPath = ResourceIndex.normalizeLogicalPath(logicalPath);
            if (logicalPath.length() > MAX_LOGICAL_PATH_CHARS) {
                throw new IllegalArgumentException("Prepared audio logical path exceeds its character limit");
            }
            sourceSha256 = canonicalHash(sourceSha256, "sourceSha256");
            if (sourceBytes < 0 || sourceBytes > MAX_ENCODED_SOURCE_BYTES) {
                throw new IllegalArgumentException("Prepared audio source byte count is invalid");
            }
            if (sourceModifiedMillis < 0) {
                throw new IllegalArgumentException("Prepared audio source modification time is invalid");
            }
            policy = Objects.requireNonNull(policy, "policy");
            cacheKeySha256 = cacheKeySha256 == null ? "" : cacheKeySha256.toLowerCase(Locale.ROOT);
            if (policy.cacheEligible()) {
                canonicalHash(cacheKeySha256, "cacheKeySha256");
                metadata = Objects.requireNonNull(metadata, "metadata");
            } else {
                if (!cacheKeySha256.isEmpty()) {
                    throw new IllegalArgumentException("Ineligible prepared audio entry may not contain a cache key");
                }
                if (metadata != null) {
                    throw new IllegalArgumentException("Ineligible prepared audio entry may not contain PCM metadata");
                }
            }
        }

        public static Entry prepared(
                String logicalPath,
                long sourceBytes,
                long sourceModifiedMillis,
                PreparedAudio audio) {
            Objects.requireNonNull(audio, "audio");
            return new Entry(
                    logicalPath,
                    audio.sourceSha256(),
                    sourceBytes,
                    sourceModifiedMillis,
                    audio.policy(),
                    PreparedAudioCache.cacheKeySha256(
                            audio.sourceSha256(),
                            audio.decoderPolicyIdentitySha256(),
                            audio.policy()),
                    Metadata.from(audio));
        }

        public static Entry ineligible(
                String logicalPath,
                String sourceSha256,
                long sourceBytes,
                long sourceModifiedMillis,
                PreparedAudio.Policy policy) {
            if (policy == null || policy.cacheEligible()) {
                throw new IllegalArgumentException("Ineligible entry requires STREAMED or UNSUPPORTED policy");
            }
            return new Entry(
                    logicalPath,
                    sourceSha256,
                    sourceBytes,
                    sourceModifiedMillis,
                    policy,
                    "",
                    null);
        }
    }

    public record Metadata(
            int sampleRateHz,
            int channels,
            long frameCount,
            long sampleCount,
            PreparedAudio.PcmEncoding encoding,
            int bitsPerSample,
            PreparedAudio.ByteOrder byteOrder,
            int pcmBytes,
            String pcmSha256) {
        public Metadata {
            if (sampleRateHz < 1 || sampleRateHz > PreparedAudio.MAX_SAMPLE_RATE_HZ) {
                throw new IllegalArgumentException("Prepared audio manifest sample rate is invalid");
            }
            if (channels < 1 || channels > PreparedAudio.MAX_CHANNELS) {
                throw new IllegalArgumentException("Prepared audio manifest channel count is invalid");
            }
            if (frameCount < 0 || sampleCount != Math.multiplyExact(frameCount, channels)) {
                throw new IllegalArgumentException("Prepared audio manifest frame/sample count is invalid");
            }
            encoding = Objects.requireNonNull(encoding, "encoding");
            if (!PreparedAudio.supportsFormat(encoding, bitsPerSample)) {
                throw new IllegalArgumentException("Prepared audio manifest PCM format is invalid");
            }
            byteOrder = Objects.requireNonNull(byteOrder, "byteOrder");
            int bytesPerSample = bitsPerSample / Byte.SIZE;
            long expectedBytes = Math.multiplyExact(sampleCount, bytesPerSample);
            if (pcmBytes < 0 || pcmBytes > PreparedAudio.MAX_PCM_BYTES || expectedBytes != pcmBytes) {
                throw new IllegalArgumentException("Prepared audio manifest PCM byte count is invalid");
            }
            pcmSha256 = canonicalHash(pcmSha256, "pcmSha256");
        }

        public static Metadata from(PreparedAudio audio) {
            return new Metadata(
                    audio.sampleRateHz(),
                    audio.channels(),
                    audio.frameCount(),
                    audio.sampleCount(),
                    audio.encoding(),
                    audio.bitsPerSample(),
                    audio.byteOrder(),
                    audio.pcmByteCount(),
                    audio.pcmSha256());
        }
    }
}
