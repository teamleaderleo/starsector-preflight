package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedAudio;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Exact parser for the canonical PCM WAV files emitted by {@link SyntheticExtendedProfile}. */
final class SyntheticWavePreparedAudio {
    private static final int HEADER_BYTES = 44;
    private static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_IMPLEMENTATION_BYTES = 1024 * 1024;
    private static final String IMPLEMENTATION_SUFFIX =
            "synthetic-wave-prepared-audio-v1/pcm-signed-16le/canonical-riff";

    private SyntheticWavePreparedAudio() {
    }

    static PreparedAudio decode(byte[] sourceBytes, String expectedSourceSha256) throws IOException {
        if (sourceBytes == null || sourceBytes.length > MAX_SOURCE_BYTES) {
            throw new IOException("Synthetic WAV source exceeds its byte limit");
        }
        if (!Hashes.sha256(sourceBytes).equals(expectedSourceSha256)) {
            throw new IOException("Synthetic WAV source identity mismatch");
        }
        if (sourceBytes.length < HEADER_BYTES
                || !ascii(sourceBytes, 0, "RIFF")
                || !ascii(sourceBytes, 8, "WAVE")
                || !ascii(sourceBytes, 12, "fmt ")
                || !ascii(sourceBytes, 36, "data")) {
            throw new IOException("Synthetic WAV header mismatch");
        }
        if (unsignedIntLittleEndian(sourceBytes, 4) != sourceBytes.length - 8L) {
            throw new IOException("Synthetic WAV RIFF length mismatch");
        }
        if (unsignedIntLittleEndian(sourceBytes, 16) != 16
                || unsignedShortLittleEndian(sourceBytes, 20) != 1) {
            throw new IOException("Synthetic WAV is not canonical PCM");
        }
        int channels = unsignedShortLittleEndian(sourceBytes, 22);
        long sampleRateLong = unsignedIntLittleEndian(sourceBytes, 24);
        long byteRate = unsignedIntLittleEndian(sourceBytes, 28);
        int blockAlign = unsignedShortLittleEndian(sourceBytes, 32);
        int bitsPerSample = unsignedShortLittleEndian(sourceBytes, 34);
        long dataBytesLong = unsignedIntLittleEndian(sourceBytes, 40);
        if (channels < 1 || channels > 2) {
            throw new IOException("Synthetic WAV channel count is outside its gate");
        }
        if (sampleRateLong <= 0 || sampleRateLong > 192_000) {
            throw new IOException("Synthetic WAV sample rate is outside its gate");
        }
        if (bitsPerSample != 16 || blockAlign != channels * 2) {
            throw new IOException("Synthetic WAV PCM layout mismatch");
        }
        if (byteRate != sampleRateLong * blockAlign) {
            throw new IOException("Synthetic WAV byte rate mismatch");
        }
        if (dataBytesLong != sourceBytes.length - HEADER_BYTES
                || dataBytesLong % blockAlign != 0) {
            throw new IOException("Synthetic WAV data length mismatch");
        }

        byte[] pcm = Arrays.copyOfRange(sourceBytes, HEADER_BYTES, sourceBytes.length);
        return new PreparedAudio(
                expectedSourceSha256,
                decoderPolicyIdentitySha256(),
                PreparedAudio.Policy.FULLY_DECODED_EFFECT,
                PreparedAudio.PcmEncoding.PCM_SIGNED,
                16,
                PreparedAudio.ByteOrder.LITTLE_ENDIAN,
                (int) sampleRateLong,
                channels,
                dataBytesLong / blockAlign,
                pcm);
    }

    static String decoderPolicyIdentitySha256() throws IOException {
        String entryName = '/' + SyntheticWavePreparedAudio.class.getName().replace('.', '/') + ".class";
        byte[] implementation;
        try (InputStream input = SyntheticWavePreparedAudio.class.getResourceAsStream(entryName)) {
            if (input == null) throw new IOException("Missing synthetic WAV implementation bytes");
            implementation = input.readNBytes(MAX_IMPLEMENTATION_BYTES + 1);
        }
        if (implementation.length > MAX_IMPLEMENTATION_BYTES) {
            throw new IOException("Synthetic WAV implementation exceeds its identity limit");
        }
        byte[] suffix = IMPLEMENTATION_SUFFIX.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream identity = new ByteArrayOutputStream(
                Integer.BYTES + implementation.length + Integer.BYTES + suffix.length);
        try (DataOutputStream output = new DataOutputStream(identity)) {
            output.writeInt(implementation.length);
            output.write(implementation);
            output.writeInt(suffix.length);
            output.write(suffix);
        }
        return Hashes.sha256(identity.toByteArray());
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] value = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || bytes.length - offset < value.length) return false;
        for (int i = 0; i < value.length; i++) {
            if (bytes[offset + i] != value[i]) return false;
        }
        return true;
    }

    private static int unsignedShortLittleEndian(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
    }

    private static long unsignedIntLittleEndian(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(bytes[offset])
                        | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(bytes[offset + 3]) << 24));
    }
}
