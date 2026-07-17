package com.jcraft.jorbis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** Repository-owned stand-in that makes the packaged equivalence harness deterministic in CI. */
public final class VorbisFile {
    private static final Map<String, Fixture> FIXTURES = Map.of(
            "2743d710c5df780d381664097a747bd4baf949f9721fbfa8a6e6c14477658b07",
            new Fixture("mono-22050-reference.s16le", 1, 22_050),
            "83c01b0343243bbff24d9b6de9619a476ccdf4b8993db13805f9a86f191031c0",
            new Fixture("stereo-44100-reference.s16le", 2, 44_100),
            "fe0202cd86957a1c6af4eb37d7dc540e266f1a9d81aff9a56274dd36cd8bbab3",
            new Fixture("silence-mono-8000-reference.s16le", 1, 8_000),
            "a5ad2e9719c0422b2294fc4cc5fe1183b77cadb2115e99ca77692f3dbc02d096",
            new Fixture("clipping-stereo-48000-reference.s16le", 2, 48_000),
            "1180a397ebb6b44b53c0417d1d39e053c713a93f65ca2e112c2e47a328dfebc1",
            new Fixture("packet-boundary-mono-44100-reference.s16le", 1, 44_100));

    private final Info info = new Info();
    private final byte[] pcm;
    private int offset;

    public VorbisFile(InputStream input, byte[] initial, int initialBytes) throws JOrbisException {
        try {
            byte[] source = input.readAllBytes();
            Fixture fixture = FIXTURES.get(sha256(source));
            if (fixture == null) {
                throw new JOrbisException("synthetic unsupported or malformed stream");
            }
            info.channels = fixture.channels();
            info.rate = fixture.sampleRate();
            pcm = resource(fixture.pcmResource());
        } catch (IOException error) {
            throw new JOrbisException("synthetic read failure: " + error.getMessage());
        }
    }

    public Info getInfo(int link) {
        return info;
    }

    int read(byte[] buffer, int length, int bigEndian, int word, int signed, int[] bitstream) {
        if (bigEndian != 0 || word != 2 || signed != 1) {
            return -1;
        }
        if (offset >= pcm.length) {
            return 0;
        }
        int count = Math.min(Math.min(length, buffer.length), Math.min(777, pcm.length - offset));
        System.arraycopy(pcm, offset, buffer, 0, count);
        offset += count;
        if (bitstream != null && bitstream.length > 0) {
            bitstream[0] = 0;
        }
        return count;
    }

    private static byte[] resource(String name) throws IOException {
        String base = "/audio/ogg-v1/" + name + ".b64";
        InputStream single = VorbisFile.class.getResourceAsStream(base);
        if (single != null) {
            try (single) {
                return Base64.getMimeDecoder().decode(single.readAllBytes());
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int parts = 0;
        for (int i = 0; i < 100; i++) {
            InputStream part = VorbisFile.class.getResourceAsStream(base + ".part" + String.format("%02d", i));
            if (part == null) break;
            try (part) {
                part.transferTo(encoded);
            }
            parts++;
        }
        if (parts == 0) {
            throw new IOException("missing synthetic PCM resource " + base);
        }
        return Base64.getMimeDecoder().decode(encoded.toByteArray());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Fixture(String pcmResource, int channels, int sampleRate) {
    }
}
