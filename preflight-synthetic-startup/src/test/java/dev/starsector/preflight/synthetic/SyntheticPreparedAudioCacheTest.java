package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.starsector.preflight.core.Hashes;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticPreparedAudioCacheTest {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripAuthenticatesSourcePolicyDecoderMetadataAndPayload() throws Exception {
        byte[] source = wave(2, 44_100, 17);
        String sourceHash = Hashes.sha256(source);
        var audio = SyntheticPreparedAudioCache.decodeWave(source);
        SyntheticPreparedAudioCache.write(
                temporaryDirectory,
                sourceHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY,
                audio);
        var hit = SyntheticPreparedAudioCache.lookup(
                temporaryDirectory,
                sourceHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY);
        assertEquals(SyntheticPreparedAudioCache.Status.HIT, hit.status());
        assertEquals(audio.sampleRate(), hit.audio().sampleRate());
        assertEquals(audio.channels(), hit.audio().channels());
        assertEquals(audio.frameCount(), hit.audio().frameCount());
        assertArrayEquals(audio.pcm(), hit.audio().pcm());
        assertThrows(IllegalArgumentException.class, () -> SyntheticPreparedAudioCache.cachePath(
                temporaryDirectory,
                sourceHash,
                SyntheticPreparedAudioCache.Policy.STREAMED,
                SyntheticPreparedAudioCache.DECODER_IDENTITY));
    }

    @Test
    void copiedValidFileForAnotherIdentityIsCorruptAndContentPathsDeduplicate() throws Exception {
        byte[] source = wave(1, 22_050, 11);
        byte[] other = wave(1, 22_050, 12);
        String sourceHash = Hashes.sha256(source);
        String otherHash = Hashes.sha256(other);
        var audio = SyntheticPreparedAudioCache.decodeWave(source);
        SyntheticPreparedAudioCache.write(temporaryDirectory, sourceHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY, audio);
        Path original = SyntheticPreparedAudioCache.cachePath(temporaryDirectory, sourceHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY);
        Path copied = SyntheticPreparedAudioCache.cachePath(temporaryDirectory, otherHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY);
        Files.createDirectories(copied.getParent());
        Files.copy(original, copied);
        assertEquals(SyntheticPreparedAudioCache.Status.CORRUPT,
                SyntheticPreparedAudioCache.lookup(temporaryDirectory, otherHash,
                        SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                        SyntheticPreparedAudioCache.DECODER_IDENTITY).status());
        assertEquals(original, SyntheticPreparedAudioCache.cachePath(temporaryDirectory, sourceHash,
                SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                SyntheticPreparedAudioCache.DECODER_IDENTITY));
        assertNotEquals(original, copied);
        byte[] corrupt = Files.readAllBytes(original);
        corrupt[corrupt.length / 2] ^= 1;
        Files.write(original, corrupt);
        assertEquals(SyntheticPreparedAudioCache.Status.CORRUPT,
                SyntheticPreparedAudioCache.lookup(temporaryDirectory, sourceHash,
                        SyntheticPreparedAudioCache.Policy.FULLY_DECODED_EFFECT,
                        SyntheticPreparedAudioCache.DECODER_IDENTITY).status());
    }

    private static byte[] wave(int channels, int sampleRate, int frames) throws Exception {
        int dataBytes = channels * frames * 2;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + dataBytes);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("RIFF"); leInt(out, 36 + dataBytes); out.writeBytes("WAVEfmt "); leInt(out, 16);
            leShort(out, 1); leShort(out, channels); leInt(out, sampleRate); leInt(out, sampleRate * channels * 2);
            leShort(out, channels * 2); leShort(out, 16); out.writeBytes("data"); leInt(out, dataBytes);
            for (int i = 0; i < frames * channels; i++) leShort(out, i * 131);
        }
        return bytes.toByteArray();
    }
    private static void leShort(DataOutputStream out,int v)throws Exception{out.writeByte(v&255);out.writeByte(v>>>8&255);}
    private static void leInt(DataOutputStream out,int v)throws Exception{out.writeByte(v&255);out.writeByte(v>>>8&255);out.writeByte(v>>>16&255);out.writeByte(v>>>24&255);}
}
