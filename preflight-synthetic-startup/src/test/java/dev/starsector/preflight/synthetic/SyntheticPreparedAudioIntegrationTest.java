package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedAudio;
import dev.starsector.preflight.core.PreparedAudioCache;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticPreparedAudioIntegrationTest {
    private static final String DECODER_POLICY_IDENTITY_SHA256 = Hashes.sha256(
            "synthetic-java-audio-pcm-signed-16le-v1".getBytes(StandardCharsets.UTF_8));

    @TempDir Path temporaryDirectory;

    @Test
    void syntheticDecoderRoundTripsThroughProductionCache() throws Exception {
        byte[] source = wave(2, 44_100, 17);
        String sourceHash = Hashes.sha256(source);
        PreparedAudio audio = SyntheticAudioDecoder.decodeWave(
                sourceHash,
                DECODER_POLICY_IDENTITY_SHA256,
                source);
        PreparedAudioCache.write(temporaryDirectory, audio);

        PreparedAudioCache.Lookup hit = PreparedAudioCache.lookup(
                temporaryDirectory,
                sourceHash,
                DECODER_POLICY_IDENTITY_SHA256,
                PreparedAudio.Policy.FULLY_DECODED_EFFECT);
        assertEquals(PreparedAudioCache.Status.HIT, hit.status());
        assertEquals(audio.sampleRateHz(), hit.audio().sampleRateHz());
        assertEquals(audio.channels(), hit.audio().channels());
        assertEquals(audio.frameCount(), hit.audio().frameCount());
        assertArrayEquals(audio.pcmBytes(), hit.audio().pcmBytes());

        assertEquals(PreparedAudioCache.Status.INELIGIBLE,
                PreparedAudioCache.lookup(
                        temporaryDirectory,
                        sourceHash,
                        DECODER_POLICY_IDENTITY_SHA256,
                        PreparedAudio.Policy.STREAMED).status());
    }

    @Test
    void copiedValidBlobUnderAnotherSourceIdentityIsRejected() throws Exception {
        byte[] source = wave(1, 22_050, 11);
        byte[] other = wave(1, 22_050, 12);
        String sourceHash = Hashes.sha256(source);
        String otherHash = Hashes.sha256(other);
        PreparedAudio audio = SyntheticAudioDecoder.decodeWave(
                sourceHash,
                DECODER_POLICY_IDENTITY_SHA256,
                source);
        PreparedAudioCache.write(temporaryDirectory, audio);

        Path original = PreparedAudioCache.path(
                temporaryDirectory,
                sourceHash,
                DECODER_POLICY_IDENTITY_SHA256,
                PreparedAudio.Policy.FULLY_DECODED_EFFECT);
        Path copied = PreparedAudioCache.path(
                temporaryDirectory,
                otherHash,
                DECODER_POLICY_IDENTITY_SHA256,
                PreparedAudio.Policy.FULLY_DECODED_EFFECT);
        Files.createDirectories(copied.getParent());
        Files.copy(original, copied);

        assertNotEquals(original, copied);
        assertEquals(PreparedAudioCache.Status.CORRUPT,
                PreparedAudioCache.lookup(
                        temporaryDirectory,
                        otherHash,
                        DECODER_POLICY_IDENTITY_SHA256,
                        PreparedAudio.Policy.FULLY_DECODED_EFFECT).status());

        byte[] corrupt = Files.readAllBytes(original);
        corrupt[corrupt.length / 2] ^= 1;
        Files.write(original, corrupt);
        assertEquals(PreparedAudioCache.Status.CORRUPT,
                PreparedAudioCache.lookup(
                        temporaryDirectory,
                        sourceHash,
                        DECODER_POLICY_IDENTITY_SHA256,
                        PreparedAudio.Policy.FULLY_DECODED_EFFECT).status());
    }

    private static byte[] wave(int channels, int sampleRate, int frames) throws Exception {
        int dataBytes = channels * frames * 2;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + dataBytes);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("RIFF");
            writeLeInt(output, 36 + dataBytes);
            output.writeBytes("WAVEfmt ");
            writeLeInt(output, 16);
            writeLeShort(output, 1);
            writeLeShort(output, channels);
            writeLeInt(output, sampleRate);
            writeLeInt(output, sampleRate * channels * 2);
            writeLeShort(output, channels * 2);
            writeLeShort(output, 16);
            output.writeBytes("data");
            writeLeInt(output, dataBytes);
            for (int i = 0; i < frames * channels; i++) {
                writeLeShort(output, i * 131);
            }
        }
        return bytes.toByteArray();
    }

    private static void writeLeShort(DataOutputStream output, int value) throws Exception {
        output.writeByte(value & 0xff);
        output.writeByte(value >>> 8 & 0xff);
    }

    private static void writeLeInt(DataOutputStream output, int value) throws Exception {
        output.writeByte(value & 0xff);
        output.writeByte(value >>> 8 & 0xff);
        output.writeByte(value >>> 16 & 0xff);
        output.writeByte(value >>> 24 & 0xff);
    }
}
