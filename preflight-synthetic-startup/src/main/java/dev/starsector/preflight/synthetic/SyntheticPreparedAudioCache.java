package dev.starsector.preflight.synthetic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Synthetic-only authenticated decoded-audio cache. */
final class SyntheticPreparedAudioCache {
    private static final byte[] MAGIC = {'S', 'P', 'X', 'A'};
    private static final int VERSION = 1;
    private static final int HASH_BYTES = 32;
    private static final int MAX_IDENTITY_BYTES = 4_096;
    private static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_PCM_BYTES = 64 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = MAX_PCM_BYTES + 256;
    static final String DECODER_IDENTITY = "java-audio-pcm-signed-16le-v1";

    private SyntheticPreparedAudioCache() {
    }

    enum Policy {
        FULLY_DECODED_EFFECT,
        STREAMED,
        UNSUPPORTED
    }

    enum Status {
        HIT,
        MISS,
        CORRUPT,
        ERROR
    }

    record PreparedAudio(int sampleRate, int channels, int sampleSizeBits, long frameCount, boolean bigEndian, byte[] pcm) {
        PreparedAudio {
            if (sampleRate < 1 || sampleRate > 384_000 || channels < 1 || channels > 8
                    || sampleSizeBits != 16 || frameCount < 0 || bigEndian) {
                throw new IllegalArgumentException("Invalid prepared synthetic audio metadata");
            }
            Objects.requireNonNull(pcm, "pcm");
            int frameSize = channels * (sampleSizeBits / 8);
            if (pcm.length < 1 || pcm.length > MAX_PCM_BYTES || pcm.length % frameSize != 0
                    || frameCount != pcm.length / frameSize) {
                throw new IllegalArgumentException("Invalid prepared synthetic PCM payload");
            }
            pcm = pcm.clone();
        }

        @Override
        public byte[] pcm() {
            return pcm.clone();
        }

        byte[] internalPcm() {
            return pcm;
        }
    }

    record Lookup(Status status, Path path, PreparedAudio audio, String detail) {
        Lookup {
            Objects.requireNonNull(status, "status");
            detail = detail == null ? "" : detail;
            if ((status == Status.HIT) != (audio != null)) {
                throw new IllegalArgumentException("Synthetic audio lookup invariant");
            }
        }

        static Lookup hit(Path path, PreparedAudio audio) {
            return new Lookup(Status.HIT, path, Objects.requireNonNull(audio), "");
        }

        static Lookup failure(Status status, Path path, String detail) {
            return new Lookup(status, path, null, detail);
        }
    }

    static Path cachePath(Path cacheRoot, String sourceSha256, Policy policy, String decoderIdentity) {
        String source = canonicalHash(sourceSha256);
        if (policy != Policy.FULLY_DECODED_EFFECT) {
            throw new IllegalArgumentException("Only fully decoded effects are cacheable");
        }
        byte[] identity = identityBytes(decoderIdentity);
        String key = hex(integrityDigest(source, policy, identity).digest());
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path target = root.resolve("synthetic-startup")
                .resolve("audio")
                .resolve(key.substring(0, 2))
                .resolve(key + ".spxa")
                .normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Synthetic audio path escaped cache root");
        return target;
    }

    static Lookup lookup(Path cacheRoot, String sourceSha256, Policy policy, String decoderIdentity) {
        final Path target;
        try {
            target = cachePath(cacheRoot, sourceSha256, policy, decoderIdentity);
        } catch (RuntimeException error) {
            return Lookup.failure(Status.ERROR, null, message(error));
        }
        if (!Files.exists(target)) return Lookup.failure(Status.MISS, target, "No prepared synthetic audio exists");
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return Lookup.failure(Status.ERROR, target, "Synthetic audio cache path is not a regular file");
        }
        try {
            long size = Files.size(target);
            if (size < minimumFileBytes() || size > MAX_FILE_BYTES) {
                return Lookup.failure(Status.CORRUPT, target, "Synthetic audio cache file size is invalid");
            }
            return Lookup.hit(target, decode(readBounded(target, MAX_FILE_BYTES), sourceSha256, policy, decoderIdentity));
        } catch (IOException | IllegalArgumentException | ArithmeticException error) {
            return Lookup.failure(Status.CORRUPT, target, message(error));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            return Lookup.failure(Status.ERROR, target, message(error));
        }
    }

    static PreparedAudio decodeWave(byte[] source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (source.length < 1 || source.length > MAX_SOURCE_BYTES) {
            throw new IOException("Synthetic audio source size is invalid");
        }
        try (AudioInputStream original = AudioSystem.getAudioInputStream(new ByteArrayInputStream(source))) {
            AudioFormat input = original.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    input.getSampleRate(),
                    16,
                    input.getChannels(),
                    input.getChannels() * 2,
                    input.getSampleRate(),
                    false);
            if (!AudioSystem.isConversionSupported(target, input) && !target.matches(input)) {
                throw new IOException("Synthetic audio conversion is unsupported: " + input);
            }
            try (AudioInputStream pcm = target.matches(input)
                    ? original
                    : AudioSystem.getAudioInputStream(target, original)) {
                byte[] payload = pcm.readNBytes(MAX_PCM_BYTES + 1);
                if (payload.length > MAX_PCM_BYTES) throw new IOException("Synthetic decoded PCM exceeds limit");
                int frameSize = target.getFrameSize();
                if (payload.length < 1 || frameSize < 1 || payload.length % frameSize != 0) {
                    throw new IOException("Synthetic decoded PCM has an incomplete frame");
                }
                return new PreparedAudio(
                        Math.round(target.getSampleRate()),
                        target.getChannels(),
                        target.getSampleSizeInBits(),
                        payload.length / frameSize,
                        target.isBigEndian(),
                        payload);
            }
        } catch (UnsupportedAudioFileException error) {
            throw new IOException("Synthetic encoded audio is unsupported", error);
        }
    }

    static void write(
            Path cacheRoot,
            String sourceSha256,
            Policy policy,
            String decoderIdentity,
            PreparedAudio audio) throws IOException {
        Path target = cachePath(cacheRoot, sourceSha256, policy, decoderIdentity);
        byte[] encoded = encode(sourceSha256, policy, decoderIdentity, audio);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static byte[] encode(
            String sourceSha256,
            Policy policy,
            String decoderIdentity,
            PreparedAudio audio) throws IOException {
        Objects.requireNonNull(audio, "audio");
        String source = canonicalHash(sourceSha256);
        byte[] sourceHash = HexFormat.of().parseHex(source);
        byte[] identity = identityBytes(decoderIdentity);
        byte[] pcm = audio.internalPcm();
        MessageDigest checksum = integrityDigest(source, policy, identity);
        updateMetadata(checksum, audio);
        checksum.update(pcm);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(minimumFileBytes() + identity.length + pcm.length);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.write(sourceHash);
            output.writeByte(policy.ordinal());
            output.writeInt(identity.length);
            output.write(identity);
            output.writeInt(audio.sampleRate());
            output.writeInt(audio.channels());
            output.writeInt(audio.sampleSizeBits());
            output.writeLong(audio.frameCount());
            output.writeBoolean(audio.bigEndian());
            output.writeInt(pcm.length);
            output.write(pcm);
            output.write(checksum.digest());
        }
        if (bytes.size() > MAX_FILE_BYTES) throw new IOException("Synthetic audio cache file exceeds limit");
        return bytes.toByteArray();
    }

    private static PreparedAudio decode(
            byte[] encoded,
            String expectedSourceSha256,
            Policy expectedPolicy,
            String expectedDecoderIdentity) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (!Arrays.equals(MAGIC, input.readNBytes(MAGIC.length))) throw new IOException("Synthetic audio magic mismatch");
            if (input.readInt() != VERSION) throw new IOException("Synthetic audio version mismatch");
            byte[] sourceHash = input.readNBytes(HASH_BYTES);
            if (sourceHash.length != HASH_BYTES
                    || !MessageDigest.isEqual(sourceHash, HexFormat.of().parseHex(canonicalHash(expectedSourceSha256)))) {
                throw new IOException("Synthetic audio source identity mismatch");
            }
            int policyOrdinal = input.readUnsignedByte();
            if (policyOrdinal >= Policy.values().length || Policy.values()[policyOrdinal] != expectedPolicy) {
                throw new IOException("Synthetic audio policy mismatch");
            }
            int identityLength = input.readInt();
            if (identityLength < 1 || identityLength > MAX_IDENTITY_BYTES) throw new IOException("Synthetic audio identity length");
            byte[] identity = input.readNBytes(identityLength);
            byte[] expectedIdentity = identityBytes(expectedDecoderIdentity);
            if (identity.length != identityLength || !MessageDigest.isEqual(identity, expectedIdentity)) {
                throw new IOException("Synthetic audio decoder identity mismatch");
            }
            int sampleRate = input.readInt();
            int channels = input.readInt();
            int bits = input.readInt();
            long frames = input.readLong();
            boolean bigEndian = input.readBoolean();
            int length = input.readInt();
            if (length < 1 || length > MAX_PCM_BYTES) throw new IOException("Synthetic audio payload length");
            byte[] pcm = input.readNBytes(length);
            byte[] checksum = input.readNBytes(HASH_BYTES);
            if (pcm.length != length || checksum.length != HASH_BYTES || input.available() != 0) throw new EOFException();
            PreparedAudio audio = new PreparedAudio(sampleRate, channels, bits, frames, bigEndian, pcm);
            MessageDigest actual = integrityDigest(expectedSourceSha256, expectedPolicy, expectedIdentity);
            updateMetadata(actual, audio);
            actual.update(pcm);
            if (!MessageDigest.isEqual(checksum, actual.digest())) throw new IOException("Synthetic audio checksum mismatch");
            return audio;
        }
    }

    private static MessageDigest integrityDigest(String sourceSha256, Policy policy, byte[] identity) {
        MessageDigest digest = sha256();
        digest.update(HexFormat.of().parseHex(canonicalHash(sourceSha256)));
        digest.update((byte) policy.ordinal());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(identity.length).array());
        digest.update(identity);
        return digest;
    }

    private static void updateMetadata(MessageDigest digest, PreparedAudio audio) {
        digest.update(ByteBuffer.allocate(Integer.BYTES * 3 + Long.BYTES + 1 + Integer.BYTES)
                .putInt(audio.sampleRate())
                .putInt(audio.channels())
                .putInt(audio.sampleSizeBits())
                .putLong(audio.frameCount())
                .put((byte) (audio.bigEndian() ? 1 : 0))
                .putInt(audio.internalPcm().length)
                .array());
    }

    private static byte[] identityBytes(String value) {
        Objects.requireNonNull(value, "decoderIdentity");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_IDENTITY_BYTES) {
            throw new IllegalArgumentException("Synthetic decoder identity length is invalid");
        }
        return bytes;
    }

    private static byte[] readBounded(Path file, int maximum) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Synthetic audio cache file exceeds limit");
            return bytes;
        }
    }

    private static int minimumFileBytes() {
        return MAGIC.length + Integer.BYTES + HASH_BYTES + 1 + Integer.BYTES
                + Integer.BYTES * 3 + Long.BYTES + 1 + Integer.BYTES + HASH_BYTES;
    }

    private static String canonicalHash(String value) {
        Objects.requireNonNull(value, "sourceSha256");
        String hash = value.toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected SHA-256");
        return hash;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
