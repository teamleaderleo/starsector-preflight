package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.OggVorbisIdentification;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class InstalledJorbisFixtureIdentityTest {
    @Test
    void allFullProfileOggFixturesHavePinnedContainerAndFormatIdentity() throws Exception {
        assertFixture(
                "mono-22050.ogg",
                4_285,
                "2743d710c5df780d381664097a747bd4baf949f9721fbfa8a6e6c14477658b07",
                1,
                22_050);
        assertFixture(
                "stereo-44100.ogg",
                6_843,
                "83c01b0343243bbff24d9b6de9619a476ccdf4b8993db13805f9a86f191031c0",
                2,
                44_100);
        assertFixture(
                "silence-mono-8000.ogg",
                2_671,
                "fe0202cd86957a1c6af4eb37d7dc540e266f1a9d81aff9a56274dd36cd8bbab3",
                1,
                8_000);
        assertFixture(
                "clipping-stereo-48000.ogg",
                8_139,
                "2ad023bf52f6cc160cec003bdb63c93e2c82065efe9bd29b8e8019400c6ac41a",
                2,
                48_000);
        assertFixture(
                "packet-boundary-mono-44100.ogg",
                5_840,
                "3718112dc664b61bf6467eaf68d5c30a7b5884ee1540ce3e1866f59c7a35d70c",
                1,
                44_100);
    }

    @Test
    void silencePcmOracleIsPinned() throws Exception {
        byte[] pcm = fixture("silence-mono-8000-reference.s16le");
        assertEquals(3_584, pcm.length);
        assertEquals("6cf1b57d59e7111bc218dfb01dda93ac0f776715599a1c69f89035bd20c16a10",
                Hashes.sha256(pcm));
    }

    private static void assertFixture(
            String name,
            int expectedBytes,
            String expectedSha256,
            int expectedChannels,
            int expectedSampleRate) throws Exception {
        byte[] source = fixture(name);
        assertEquals(expectedBytes, source.length, name);
        assertEquals(expectedSha256, Hashes.sha256(source), name);
        OggVorbisIdentification.Result result = OggVorbisIdentification.inspect(source);
        assertEquals(OggVorbisIdentification.Status.SUPPORTED, result.status(), result.detail());
        assertEquals(expectedChannels, result.channels(), name);
        assertEquals(expectedSampleRate, result.sampleRate(), name);
    }

    private static byte[] fixture(String name) throws IOException {
        String base = "/audio/ogg-v1/" + name + ".b64";
        InputStream single = InstalledJorbisFixtureIdentityTest.class.getResourceAsStream(base);
        if (single != null) {
            try (single) {
                return Base64.getMimeDecoder().decode(single.readAllBytes());
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int parts = 0;
        for (int i = 0; i < 100; i++) {
            InputStream part = InstalledJorbisFixtureIdentityTest.class.getResourceAsStream(
                    base + ".part" + String.format("%02d", i));
            if (part == null) break;
            try (part) {
                part.transferTo(encoded);
            }
            parts++;
        }
        assertTrue(parts > 0, "Missing fixture " + base);
        return Base64.getMimeDecoder().decode(encoded.toByteArray());
    }
}
