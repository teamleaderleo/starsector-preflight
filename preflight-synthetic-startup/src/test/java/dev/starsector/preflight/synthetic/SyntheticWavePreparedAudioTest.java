package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.starsector.preflight.core.PreparedAudio;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticWavePreparedAudioTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedEffectWaveBecomesExactProductionPreparedAudio() throws Exception {
        Path profile = temporaryDirectory.resolve("profile");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                12_345,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedResourceIndex index = SyntheticExtendedResourceIndex.build(
                profile,
                manifest.fingerprintSha256()).index();

        String logical = "sounds/effects/audio-00000.wav";
        SyntheticExtendedResourceIndex.Provider provider = index.providers().get(logical);
        byte[] source = index.readBytes(logical);
        PreparedAudio first = SyntheticWavePreparedAudio.decode(source, provider.sha256());
        PreparedAudio second = SyntheticWavePreparedAudio.decode(source, provider.sha256());

        assertEquals(PreparedAudio.Policy.FULLY_DECODED_EFFECT, first.policy());
        assertEquals(PreparedAudio.PcmEncoding.PCM_SIGNED, first.encoding());
        assertEquals(PreparedAudio.ByteOrder.LITTLE_ENDIAN, first.byteOrder());
        assertEquals(16, first.bitsPerSample());
        assertEquals(22_050, first.sampleRateHz());
        assertEquals(2, first.channels());
        assertEquals(64, first.frameCount());
        assertEquals(128, first.sampleCount());
        assertEquals(provider.sha256(), first.sourceSha256());
        assertEquals(64, first.decoderPolicyIdentitySha256().length());
        assertEquals(first.decoderPolicyIdentitySha256(), second.decoderPolicyIdentitySha256());
        assertArrayEquals(first.pcmBytes(), second.pcmBytes());
    }

    @Test
    void sourceHashAndHeaderMutationsAreRejected() throws Exception {
        Path profile = temporaryDirectory.resolve("profile");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                7,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedResourceIndex index = SyntheticExtendedResourceIndex.build(
                profile,
                manifest.fingerprintSha256()).index();
        String logical = "sounds/effects/audio-00000.wav";
        SyntheticExtendedResourceIndex.Provider provider = index.providers().get(logical);
        byte[] source = index.readBytes(logical);

        assertThrows(
                IOException.class,
                () -> SyntheticWavePreparedAudio.decode(source, "0".repeat(64)));
        byte[] changed = source.clone();
        changed[34] = 8;
        assertNotEquals(provider.sha256(), dev.starsector.preflight.core.Hashes.sha256(changed));
        assertThrows(
                IOException.class,
                () -> SyntheticWavePreparedAudio.decode(
                        changed,
                        dev.starsector.preflight.core.Hashes.sha256(changed)));
    }
}
