package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OggVorbisGoldenFixtureTest {
    private static final String REFERENCE_DECODER_IDENTITY =
            "fd912b25752d09927e6aac99b3711a856263079f5fbb4ba6457830224687691a";
    private static final int OGG_CRC_POLYNOMIAL = 0x04c11db7;

    @TempDir
    Path temporaryDirectory;

    @Test
    void monoFixtureHasExactContainerPcmAndPreparedAudioIdentity() throws Exception {
        byte[] ogg = fixture("mono-22050.ogg");
        byte[] pcm = fixture("mono-22050-reference.s16le");

        assertEquals(4_285, ogg.length);
        assertEquals("2743d710c5df780d381664097a747bd4baf949f9721fbfa8a6e6c14477658b07",
                Hashes.sha256(ogg));
        assertEquals(3_584, pcm.length);
        assertEquals("bbe3d4cb25eb77c157a77091202dd0f4458aa18e50a4b59be018f22be8dc62e5",
                Hashes.sha256(pcm));

        OggVorbisIdentification.Result identification = OggVorbisIdentification.inspect(ogg);
        assertEquals(OggVorbisIdentification.Status.SUPPORTED, identification.status(), identification.detail());
        assertEquals("vorbis", identification.codec());
        assertEquals(1, identification.channels());
        assertEquals(22_050, identification.sampleRate());
        assertEquals(512, identification.smallBlockSize());
        assertEquals(1_024, identification.largeBlockSize());

        PreparedAudio prepared = prepared(ogg, pcm, 22_050, 1, 1_792);
        assertEquals(1_792, prepared.frameCount());
        assertEquals(1_792, prepared.sampleCount());
        assertEquals(2, prepared.frameSizeBytes());
        assertArrayEquals(pcm, prepared.pcmBytes());
        assertEquals(Hashes.sha256(pcm), prepared.pcmSha256());
        assertEquals(Hashes.sha256(ogg), prepared.sourceSha256());
        assertEquals(REFERENCE_DECODER_IDENTITY, prepared.decoderPolicyIdentitySha256());
    }

    @Test
    void stereoFixtureHasExactContainerPcmAndPreparedAudioIdentity() throws Exception {
        byte[] ogg = fixture("stereo-44100.ogg");
        byte[] pcm = fixture("stereo-44100-reference.s16le");

        assertEquals(6_843, ogg.length);
        assertEquals("83c01b0343243bbff24d9b6de9619a476ccdf4b8993db13805f9a86f191031c0",
                Hashes.sha256(ogg));
        assertEquals(15_872, pcm.length);
        assertEquals("ada77fe8b369053d7dd1b1ec9430bfec15886ece0be5768dcc4c8e2b17f9fbf8",
                Hashes.sha256(pcm));

        OggVorbisIdentification.Result identification = OggVorbisIdentification.inspect(ogg);
        assertEquals(OggVorbisIdentification.Status.SUPPORTED, identification.status(), identification.detail());
        assertEquals("vorbis", identification.codec());
        assertEquals(2, identification.channels());
        assertEquals(44_100, identification.sampleRate());
        assertEquals(256, identification.smallBlockSize());
        assertEquals(2_048, identification.largeBlockSize());

        PreparedAudio prepared = prepared(ogg, pcm, 44_100, 2, 3_968);
        assertEquals(3_968, prepared.frameCount());
        assertEquals(7_936, prepared.sampleCount());
        assertEquals(4, prepared.frameSizeBytes());
        assertArrayEquals(pcm, prepared.pcmBytes());
        assertEquals(Hashes.sha256(pcm), prepared.pcmSha256());
        assertEquals(Hashes.sha256(ogg), prepared.sourceSha256());
        assertEquals(REFERENCE_DECODER_IDENTITY, prepared.decoderPolicyIdentitySha256());
    }

    @Test
    void opusAndNonOggInputsAreIneligibleWithoutPcmMetadata() throws Exception {
        byte[] opus = fixture("mono-22050-opus.ogg");
        assertEquals(484, opus.length);
        assertEquals("d799bf51d2f8e4b81db45c636db59eabc619de2bf97256f8cb7709a35ca06831",
                Hashes.sha256(opus));

        OggVorbisIdentification.Result opusResult = OggVorbisIdentification.inspect(opus);
        assertEquals(OggVorbisIdentification.Status.UNSUPPORTED, opusResult.status());
        assertEquals("opus", opusResult.codec());
        assertFalse(opusResult.supported());
        assertNoAudioMetadata(opusResult);

        OggVorbisIdentification.Result text = OggVorbisIdentification.inspect(
                "not an ogg stream".getBytes(StandardCharsets.US_ASCII));
        assertEquals(OggVorbisIdentification.Status.UNSUPPORTED, text.status());
        assertEquals("unknown", text.codec());
        assertNoAudioMetadata(text);
    }

    @Test
    void checksumAndIdentificationMutationsAreRejected() throws Exception {
        byte[] original = fixture("mono-22050.ogg");

        byte[] checksumMismatch = original.clone();
        checksumMismatch[39] ^= 1;
        OggVorbisIdentification.Result checksum = OggVorbisIdentification.inspect(checksumMismatch);
        assertEquals(OggVorbisIdentification.Status.MALFORMED, checksum.status());
        assertTrue(checksum.detail().contains("checksum"), checksum.detail());

        byte[] zeroChannels = original.clone();
        zeroChannels[39] = 0;
        rewriteFirstPageChecksum(zeroChannels);
        OggVorbisIdentification.Result channels = OggVorbisIdentification.inspect(zeroChannels);
        assertEquals(OggVorbisIdentification.Status.MALFORMED, channels.status());
        assertTrue(channels.detail().contains("channel"), channels.detail());

        byte[] badBlockSize = original.clone();
        badBlockSize[56] = 0x55;
        rewriteFirstPageChecksum(badBlockSize);
        OggVorbisIdentification.Result blocks = OggVorbisIdentification.inspect(badBlockSize);
        assertEquals(OggVorbisIdentification.Status.MALFORMED, blocks.status());
        assertTrue(blocks.detail().contains("block-size"), blocks.detail());

        byte[] badFraming = original.clone();
        badFraming[57] = 0;
        rewriteFirstPageChecksum(badFraming);
        OggVorbisIdentification.Result framing = OggVorbisIdentification.inspect(badFraming);
        assertEquals(OggVorbisIdentification.Status.MALFORMED, framing.status());
        assertTrue(framing.detail().contains("framing"), framing.detail());
    }

    @Test
    void everyTruncatedFirstPageFailsWithoutThrowingOrBecomingEligible() throws Exception {
        byte[] original = fixture("stereo-44100.ogg");
        int firstPageBytes = 27 + Byte.toUnsignedInt(original[26]);
        for (int i = 27; i < firstPageBytes; i++) {
            firstPageBytes += Byte.toUnsignedInt(original[i]);
        }
        for (int length = 0; length < firstPageBytes; length++) {
            OggVorbisIdentification.Result result = OggVorbisIdentification.inspect(
                    Arrays.copyOf(original, length));
            assertFalse(result.supported(), "Truncated prefix became eligible at length " + length);
            assertTrue(result.status() == OggVorbisIdentification.Status.MALFORMED
                            || result.status() == OggVorbisIdentification.Status.UNSUPPORTED,
                    () -> "Unexpected status for prefix: " + result.status() + " " + result.detail());
        }
    }

    @Test
    void pathInspectionRejectsMissingDirectoriesAndReadsExactFixture() throws Exception {
        assertEquals(OggVorbisIdentification.Status.ERROR,
                OggVorbisIdentification.inspect(temporaryDirectory.resolve("missing.ogg")).status());
        Path directory = temporaryDirectory.resolve("directory.ogg");
        Files.createDirectory(directory);
        assertEquals(OggVorbisIdentification.Status.ERROR,
                OggVorbisIdentification.inspect(directory).status());

        byte[] mono = fixture("mono-22050.ogg");
        Path file = temporaryDirectory.resolve("mono.ogg");
        Files.write(file, mono);
        OggVorbisIdentification.Result result = OggVorbisIdentification.inspect(file);
        assertEquals(OggVorbisIdentification.Status.SUPPORTED, result.status(), result.detail());
        assertEquals(22_050, result.sampleRate());
    }

    private static PreparedAudio prepared(byte[] source, byte[] pcm, int sampleRate, int channels, long frames) {
        return new PreparedAudio(
                Hashes.sha256(source),
                REFERENCE_DECODER_IDENTITY,
                PreparedAudio.Policy.FULLY_DECODED_EFFECT,
                PreparedAudio.PcmEncoding.PCM_SIGNED,
                16,
                PreparedAudio.ByteOrder.LITTLE_ENDIAN,
                sampleRate,
                channels,
                frames,
                pcm);
    }

    private static byte[] fixture(String name) throws IOException {
        String base = "/audio/ogg-v1/" + name + ".b64";
        InputStream single = OggVorbisGoldenFixtureTest.class.getResourceAsStream(base);
        if (single != null) {
            try (single) {
                return Base64.getMimeDecoder().decode(single.readAllBytes());
            }
        }

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int parts = 0;
        for (int i = 0; i < 100; i++) {
            String partName = base + ".part" + String.format("%02d", i);
            InputStream part = OggVorbisGoldenFixtureTest.class.getResourceAsStream(partName);
            if (part == null) break;
            try (part) {
                part.transferTo(encoded);
            }
            parts++;
        }
        assertTrue(parts > 0, "Missing fixture resource " + base);
        return Base64.getMimeDecoder().decode(encoded.toByteArray());
    }

    private static void assertNoAudioMetadata(OggVorbisIdentification.Result result) {
        assertEquals(0, result.channels());
        assertEquals(0, result.sampleRate());
        assertEquals(0, result.smallBlockSize());
        assertEquals(0, result.largeBlockSize());
    }

    private static void rewriteFirstPageChecksum(byte[] ogg) {
        assertNotNull(ogg);
        int segmentCount = Byte.toUnsignedInt(ogg[26]);
        int pageLength = 27 + segmentCount;
        for (int i = 0; i < segmentCount; i++) {
            pageLength += Byte.toUnsignedInt(ogg[27 + i]);
        }
        ogg[22] = 0;
        ogg[23] = 0;
        ogg[24] = 0;
        ogg[25] = 0;
        int checksum = oggChecksum(ogg, pageLength);
        ogg[22] = (byte) checksum;
        ogg[23] = (byte) (checksum >>> 8);
        ogg[24] = (byte) (checksum >>> 16);
        ogg[25] = (byte) (checksum >>> 24);
    }

    private static int oggChecksum(byte[] bytes, int length) {
        int checksum = 0;
        for (int i = 0; i < length; i++) {
            checksum ^= Byte.toUnsignedInt(bytes[i]) << 24;
            for (int bit = 0; bit < 8; bit++) {
                checksum = (checksum & 0x80000000) != 0
                        ? (checksum << 1) ^ OGG_CRC_POLYNOMIAL
                        : checksum << 1;
            }
        }
        return checksum;
    }
}
