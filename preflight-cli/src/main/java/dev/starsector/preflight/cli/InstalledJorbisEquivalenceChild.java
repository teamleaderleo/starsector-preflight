package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.PreparedAudio;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs fixture decoding inside a child JVM whose application classpath contains the installed decoder jars. */
public final class InstalledJorbisEquivalenceChild {
    private static final int MAX_PCM_BYTES = PreparedAudio.MAX_PCM_BYTES;
    private static final int READ_BUFFER_BYTES = 4_096;
    private static final int MAX_FAILURE_DETAIL_CHARS = 1_024;
    private static final String LOADER_CLASS = "jdk.internal.loader.ClassLoaders$AppClassLoader";
    private static final String LOADER_NAME = "app";

    private static final List<Fixture> FULL_FIXTURES = List.of(
            new Fixture(
                    "mono-22050",
                    "mono-22050.ogg",
                    "bbe3d4cb25eb77c157a77091202dd0f4458aa18e50a4b59be018f22be8dc62e5",
                    3_584,
                    1,
                    22_050),
            new Fixture(
                    "stereo-44100",
                    "stereo-44100.ogg",
                    "ada77fe8b369053d7dd1b1ec9430bfec15886ece0be5768dcc4c8e2b17f9fbf8",
                    15_872,
                    2,
                    44_100),
            new Fixture(
                    "silence-mono-8000",
                    "silence-mono-8000.ogg",
                    "6cf1b57d59e7111bc218dfb01dda93ac0f776715599a1c69f89035bd20c16a10",
                    3_584,
                    1,
                    8_000),
            new Fixture(
                    "clipping-stereo-48000",
                    "clipping-stereo-48000.ogg",
                    "f5eba24d0166fadf4ac02cc423810afb596564615984b063c11e7740f4258e3d",
                    15_872,
                    2,
                    48_000),
            new Fixture(
                    "packet-boundary-mono-44100",
                    "packet-boundary-mono-44100.ogg",
                    "d4f78542dcdbe1774072343805516076f38fe6ba9edb8f1c36a60dfbbdb26d43",
                    16_640,
                    1,
                    44_100));

    private InstalledJorbisEquivalenceChild() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Map<String, Object> report = run(options);
        Path output = options.output().toAbsolutePath().normalize();
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, Json.object(report) + System.lineSeparator(), StandardCharsets.UTF_8);
        boolean equivalent = Boolean.TRUE.equals(report.get("equivalent"));
        System.out.println(equivalent ? "installed-jorbis-equivalent" : "installed-jorbis-mismatch");
        System.exit(equivalent ? 0 : 6);
    }

    static Map<String, Object> run(Options options) throws Exception {
        Class<?> joggClass = Class.forName("com.jcraft.jogg.SyncState");
        Class<?> vorbisFileClass = Class.forName("com.jcraft.jorbis.VorbisFile");
        Class<?> infoClass = Class.forName("com.jcraft.jorbis.Info");
        Identity jogg = identity(joggClass, options.expectedJoggSha256());
        Identity jorbis = identity(vorbisFileClass, options.expectedJorbisSha256());
        Identity info = identity(infoClass, options.expectedJorbisSha256());
        boolean identityExact = jogg.exact() && jorbis.exact() && info.exact()
                && appLoader(jogg) && appLoader(jorbis) && appLoader(info);

        String decoderPolicyIdentity = Hashes.sha256((
                "installed-jorbis-equivalence-v1\n"
                        + options.expectedJoggSha256() + "\n"
                        + options.expectedJorbisSha256() + "\n"
                        + LOADER_CLASS + "\n"
                        + LOADER_NAME + "\n"
                        + "pcm-signed-16-little-endian\n"
                        + "sound/J.o00000(Ljava/io/InputStream;)Lsound/F;\n"
                        + "fully-decoded-effect-only").getBytes(StandardCharsets.UTF_8));

        List<Fixture> fixtures = "ci".equals(options.fixtureProfile())
                ? FULL_FIXTURES.subList(0, 3)
                : FULL_FIXTURES;
        Decoder decoder = new Decoder(vorbisFileClass, infoClass);
        List<Map<String, Object>> cases = new ArrayList<>();
        boolean validEquivalent = true;
        for (Fixture fixture : fixtures) {
            ValidResult result = decodeValid(decoder, fixture, decoderPolicyIdentity);
            cases.add(result.toMap());
            validEquivalent &= result.equivalent();
        }

        List<InvalidFixture> invalidFixtures = invalidFixtures();
        boolean invalidStable = true;
        for (InvalidFixture fixture : invalidFixtures) {
            Observation first = observe(decoder, fixture.source());
            Observation second = observe(decoder, fixture.source());
            boolean stable = first.behaviorKey().equals(second.behaviorKey());
            Map<String, Object> value = new LinkedHashMap<>(first.toMap(fixture.id()));
            value.put("repeatDecoded", second.decoded());
            value.put("repeatFailureClass", second.failureClass());
            value.put("behaviorStable", stable);
            value.put("equivalent", stable);
            value.put("preparedAudioEligible", false);
            cases.add(Map.copyOf(value));
            invalidStable &= stable;
        }

        boolean equivalent = identityExact && validEquivalent && invalidStable;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "starsector-preflight-installed-jorbis-equivalence-v1");
        root.put("generatedAt", Instant.now());
        root.put("fixtureProfile", options.fixtureProfile());
        root.put("equivalent", equivalent);
        root.put("identityExact", identityExact);
        root.put("validPcmEquivalent", validEquivalent);
        root.put("invalidBehaviorStable", invalidStable);
        root.put("jogg", jogg.toMap());
        root.put("jorbis", jorbis.toMap());
        root.put("info", info.toMap());
        root.put("decoderPolicyIdentitySha256", decoderPolicyIdentity);
        root.put("pcmEncoding", "PCM_SIGNED");
        root.put("bitsPerSample", 16);
        root.put("byteOrder", "LITTLE_ENDIAN");
        root.put("primaryWrapperSeam", "sound/J.o00000(Ljava/io/InputStream;)Lsound/F;");
        root.put("fullyDecodedEffectsEligible", identityExact && validEquivalent);
        root.put("streamedMusicEligible", false);
        root.put("preparedAudioWritesEnabled", false);
        root.put("liveTransformEnabled", false);
        root.put("validCaseCount", fixtures.size());
        root.put("invalidCaseCount", invalidFixtures.size());
        root.put("caseCount", cases.size());
        root.put("cases", List.copyOf(cases));
        return Map.copyOf(root);
    }

    private static List<InvalidFixture> invalidFixtures() throws IOException {
        byte[] mono = fixture("mono-22050.ogg");
        byte[] stereo = fixture("stereo-44100.ogg");
        byte[] corrupt = stereo.clone();
        corrupt[Math.min(corrupt.length - 1, 192)] ^= 0x40;
        return List.of(
                new InvalidFixture("opus-unsupported", fixture("mono-22050-opus.ogg")),
                new InvalidFixture("non-ogg", "not an ogg stream".getBytes(StandardCharsets.US_ASCII)),
                new InvalidFixture("truncated-header", Arrays.copyOf(mono, 19)),
                new InvalidFixture("truncated-packet", Arrays.copyOf(mono, mono.length / 2)),
                new InvalidFixture("corrupt-packet", corrupt));
    }

    private static boolean appLoader(Identity identity) {
        return LOADER_CLASS.equals(identity.loaderClass()) && LOADER_NAME.equals(identity.loaderName());
    }

    private static ValidResult decodeValid(Decoder decoder, Fixture fixture, String decoderPolicyIdentity) {
        byte[] source;
        try {
            source = fixture(fixture.sourceResource());
        } catch (IOException failure) {
            return ValidResult.failure(fixture, new byte[0], failure, false, 0, 0, 0);
        }
        TrackingInputStream input = new TrackingInputStream(source);
        try {
            Decoded decoded = decoder.decode(input);
            boolean closedDuringDecode = input.closeCount() != 0;
            input.close();
            boolean ownershipExact = !closedDuringDecode && input.closeCount() == 1;
            int frameBytes = Math.multiplyExact(decoded.channels(), 2);
            long frames = decoded.pcm().length / frameBytes;
            PreparedAudio prepared = new PreparedAudio(
                    Hashes.sha256(source),
                    decoderPolicyIdentity,
                    PreparedAudio.Policy.FULLY_DECODED_EFFECT,
                    PreparedAudio.PcmEncoding.PCM_SIGNED,
                    16,
                    PreparedAudio.ByteOrder.LITTLE_ENDIAN,
                    decoded.sampleRate(),
                    decoded.channels(),
                    frames,
                    decoded.pcm());
            long expectedFrames = fixture.expectedPcmBytes() / Math.multiplyExact(fixture.channels(), 2);
            String actualPcmSha256 = prepared.pcmSha256();
            boolean exact = decoded.channels() == fixture.channels()
                    && decoded.sampleRate() == fixture.sampleRate()
                    && decoded.pcm().length == fixture.expectedPcmBytes()
                    && fixture.expectedPcmSha256().equals(actualPcmSha256)
                    && prepared.frameCount() == expectedFrames
                    && prepared.sampleCount() == expectedFrames * fixture.channels()
                    && ownershipExact
                    && input.bytesRead() == source.length;
            return ValidResult.success(
                    fixture, source, decoded, prepared, actualPcmSha256,
                    closedDuringDecode, input, ownershipExact, exact);
        } catch (Throwable failure) {
            boolean closedDuringDecode = input.closeCount() != 0;
            try {
                input.close();
            } catch (IOException ignored) {
            }
            return ValidResult.failure(
                    fixture, source, rootCause(failure), closedDuringDecode,
                    input.closeCount(), input.bytesRead(), input.readCalls());
        }
    }

    private static Observation observe(Decoder decoder, byte[] source) {
        TrackingInputStream input = new TrackingInputStream(source);
        try {
            Decoded decoded = decoder.decode(input);
            boolean closedDuringDecode = input.closeCount() != 0;
            input.close();
            return Observation.decoded(decoded, input, closedDuringDecode);
        } catch (Throwable failure) {
            boolean closedDuringDecode = input.closeCount() != 0;
            try {
                input.close();
            } catch (IOException ignored) {
            }
            return Observation.failed(rootCause(failure), input, closedDuringDecode);
        }
    }

    private static Identity identity(Class<?> type, String expectedSha256) throws Exception {
        ClassLoader loader = type.getClassLoader();
        CodeSource source = type.getProtectionDomain().getCodeSource();
        Path path = source == null || source.getLocation() == null
                ? null
                : Path.of(new URI(source.getLocation().toString())).toAbsolutePath().normalize();
        String actual = path != null && Files.isRegularFile(path) ? Hashes.sha256(path) : "";
        return new Identity(
                type.getName(),
                path == null ? "" : path.toString(),
                expectedSha256,
                actual,
                loader == null ? "<bootstrap>" : loader.getClass().getName(),
                loader == null ? "<bootstrap>" : String.valueOf(loader.getName()),
                expectedSha256.equals(actual));
    }

    private static byte[] fixture(String name) throws IOException {
        String base = "/audio/ogg-v1/" + name + ".b64";
        InputStream single = InstalledJorbisEquivalenceChild.class.getResourceAsStream(base);
        if (single != null) {
            try (single) {
                return Base64.getMimeDecoder().decode(single.readAllBytes());
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int parts = 0;
        for (int i = 0; i < 100; i++) {
            InputStream part = InstalledJorbisEquivalenceChild.class.getResourceAsStream(
                    base + ".part" + String.format("%02d", i));
            if (part == null) break;
            try (part) {
                part.transferTo(encoded);
            }
            parts++;
        }
        if (parts == 0) throw new IOException("Missing packaged audio fixture " + base);
        return Base64.getMimeDecoder().decode(encoded.toByteArray());
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof InvocationTargetException || current.getCause() != null)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String bounded(String value) {
        String text = value == null ? "" : value;
        return text.length() <= MAX_FAILURE_DETAIL_CHARS
                ? text
                : text.substring(0, MAX_FAILURE_DETAIL_CHARS);
    }

    record Options(String expectedJoggSha256, String expectedJorbisSha256, String fixtureProfile, Path output) {
        Options {
            Hashes.decodeSha256(expectedJoggSha256);
            Hashes.decodeSha256(expectedJorbisSha256);
            if (!"full".equals(fixtureProfile) && !"ci".equals(fixtureProfile)) {
                throw new IllegalArgumentException("fixtureProfile must be full or ci");
            }
            if (output == null) throw new IllegalArgumentException("output is required");
        }

        static Options parse(String[] args) {
            String jogg = null;
            String jorbis = null;
            String profile = "full";
            Path output = null;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--expected-jogg-sha256" -> jogg = value(args, ++i);
                    case "--expected-jorbis-sha256" -> jorbis = value(args, ++i);
                    case "--fixture-profile" -> profile = value(args, ++i);
                    case "--output" -> output = Path.of(value(args, ++i));
                    default -> throw new IllegalArgumentException("Unknown child option: " + args[i]);
                }
            }
            return new Options(
                    required(jogg, "--expected-jogg-sha256"),
                    required(jorbis, "--expected-jorbis-sha256"),
                    profile,
                    output);
        }

        private static String value(String[] args, int index) {
            if (index >= args.length) throw new IllegalArgumentException("Missing child option value");
            return args[index];
        }

        private static String required(String value, String name) {
            if (value == null) throw new IllegalArgumentException("Missing " + name);
            return value;
        }
    }

    private static final class Decoder {
        private final Constructor<?> constructor;
        private final Method read;
        private final Method getInfo;
        private final Field channels;
        private final Field rate;

        private Decoder(Class<?> vorbisFile, Class<?> info) throws Exception {
            constructor = vorbisFile.getConstructor(InputStream.class, byte[].class, int.class);
            read = vorbisFile.getDeclaredMethod(
                    "read", byte[].class, int.class, int.class, int.class, int.class, int[].class);
            read.setAccessible(true);
            getInfo = vorbisFile.getMethod("getInfo", int.class);
            channels = info.getField("channels");
            rate = info.getField("rate");
        }

        private Decoded decode(InputStream input) throws Exception {
            Object decoder = constructor.newInstance(input, null, 0);
            Object info = getInfo.invoke(decoder, 0);
            if (info == null) info = getInfo.invoke(decoder, -1);
            if (info == null) throw new IOException("JOrbis returned no stream info");
            int channelCount = channels.getInt(info);
            int sampleRate = rate.getInt(info);
            if (channelCount < 1 || channelCount > PreparedAudio.MAX_CHANNELS
                    || sampleRate < 1 || sampleRate > PreparedAudio.MAX_SAMPLE_RATE_HZ) {
                throw new IOException("JOrbis metadata is outside supported bounds");
            }

            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int[] bitstream = new int[1];
            int readCalls = 0;
            while (true) {
                int count = (Integer) read.invoke(decoder, buffer, buffer.length, 0, 2, 1, bitstream);
                readCalls++;
                if (count == 0) break;
                if (count < 0) throw new IOException("JOrbis read returned " + count);
                if (count > buffer.length || pcm.size() > MAX_PCM_BYTES - count) {
                    throw new IOException("Decoded PCM exceeds the safety limit");
                }
                pcm.write(buffer, 0, count);
            }
            byte[] bytes = pcm.toByteArray();
            int frameBytes = Math.multiplyExact(channelCount, 2);
            if (bytes.length % frameBytes != 0) throw new IOException("Decoded PCM is not frame aligned");
            return new Decoded(bytes, channelCount, sampleRate, readCalls, bitstream[0]);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private long bytesRead;
        private long readCalls;
        private long eofReads;
        private int closeCount;

        private TrackingInputStream(byte[] source) {
            super(source);
        }

        @Override
        public synchronized int read() {
            readCalls++;
            int value = super.read();
            if (value < 0) eofReads++; else bytesRead++;
            return value;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            readCalls++;
            int count = super.read(bytes, offset, length);
            if (count < 0) eofReads++; else bytesRead += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
        }

        private long bytesRead() { return bytesRead; }
        private long readCalls() { return readCalls; }
        private long eofReads() { return eofReads; }
        private int closeCount() { return closeCount; }
    }

    private record Fixture(
            String id,
            String sourceResource,
            String expectedPcmSha256,
            int expectedPcmBytes,
            int channels,
            int sampleRate) {
        private Fixture {
            Hashes.decodeSha256(expectedPcmSha256);
            if (expectedPcmBytes < 0 || expectedPcmBytes % Math.multiplyExact(channels, 2) != 0) {
                throw new IllegalArgumentException("Fixture PCM size is invalid: " + id);
            }
        }
    }

    private record InvalidFixture(String id, byte[] source) {
        private InvalidFixture { source = source.clone(); }
        @Override public byte[] source() { return source.clone(); }
    }

    private record Decoded(byte[] pcm, int channels, int sampleRate, int readCalls, int bitstream) {
        private Decoded { pcm = pcm.clone(); }
        @Override public byte[] pcm() { return pcm.clone(); }
    }

    private record Identity(
            String className,
            String source,
            String expectedSha256,
            String actualSha256,
            String loaderClass,
            String loaderName,
            boolean exact) {
        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("className", className);
            values.put("source", source);
            values.put("expectedSha256", expectedSha256);
            values.put("actualSha256", actualSha256);
            values.put("loaderClass", loaderClass);
            values.put("loaderName", loaderName);
            values.put("exact", exact);
            return Map.copyOf(values);
        }
    }

    private record ValidResult(
            String id,
            boolean equivalent,
            boolean decoded,
            String sourceSha256,
            int sourceBytes,
            String expectedPcmSha256,
            String actualPcmSha256,
            int expectedPcmBytes,
            int actualPcmBytes,
            int expectedChannels,
            int actualChannels,
            int expectedSampleRate,
            int actualSampleRate,
            long frameCount,
            long sampleCount,
            int decoderReadCalls,
            int bitstream,
            long sourceBytesRead,
            long sourceReadCalls,
            boolean streamClosedDuringDecode,
            int finalCloseCount,
            boolean streamOwnershipExact,
            String failureClass,
            String failureDetail) {

        private static ValidResult success(
                Fixture fixture,
                byte[] source,
                Decoded decoded,
                PreparedAudio prepared,
                String actualPcmSha256,
                boolean closedDuring,
                TrackingInputStream input,
                boolean ownership,
                boolean exact) {
            return new ValidResult(
                    fixture.id(), exact, true, Hashes.sha256(source), source.length,
                    fixture.expectedPcmSha256(), actualPcmSha256,
                    fixture.expectedPcmBytes(), decoded.pcm().length,
                    fixture.channels(), decoded.channels(), fixture.sampleRate(), decoded.sampleRate(),
                    prepared.frameCount(), prepared.sampleCount(), decoded.readCalls(), decoded.bitstream(),
                    input.bytesRead(), input.readCalls(), closedDuring, input.closeCount(), ownership, "", "");
        }

        private static ValidResult failure(
                Fixture fixture,
                byte[] source,
                Throwable failure,
                boolean closedDuring,
                int closeCount,
                long bytesRead,
                long readCalls) {
            return new ValidResult(
                    fixture.id(), false, false, source.length == 0 ? "" : Hashes.sha256(source), source.length,
                    fixture.expectedPcmSha256(), "", fixture.expectedPcmBytes(), 0,
                    fixture.channels(), 0, fixture.sampleRate(), 0, 0, 0, 0, 0,
                    bytesRead, readCalls, closedDuring, closeCount, !closedDuring && closeCount == 1,
                    failure.getClass().getName(), bounded(failure.getMessage()));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            values.put("validInput", true);
            values.put("equivalent", equivalent);
            values.put("decoded", decoded);
            values.put("preparedAudioEligible", equivalent);
            values.put("sourceSha256", sourceSha256);
            values.put("sourceBytes", sourceBytes);
            values.put("expectedPcmSha256", expectedPcmSha256);
            values.put("actualPcmSha256", actualPcmSha256);
            values.put("expectedPcmBytes", expectedPcmBytes);
            values.put("actualPcmBytes", actualPcmBytes);
            values.put("expectedChannels", expectedChannels);
            values.put("actualChannels", actualChannels);
            values.put("expectedSampleRate", expectedSampleRate);
            values.put("actualSampleRate", actualSampleRate);
            values.put("frameCount", frameCount);
            values.put("sampleCount", sampleCount);
            values.put("decoderReadCalls", decoderReadCalls);
            values.put("bitstream", bitstream);
            values.put("sourceBytesRead", sourceBytesRead);
            values.put("sourceReadCalls", sourceReadCalls);
            values.put("streamClosedDuringDecode", streamClosedDuringDecode);
            values.put("finalCloseCount", finalCloseCount);
            values.put("streamOwnershipExact", streamOwnershipExact);
            values.put("failureClass", failureClass);
            values.put("failureDetail", failureDetail);
            return Map.copyOf(values);
        }
    }

    private record Observation(
            boolean decoded,
            String pcmSha256,
            int pcmBytes,
            int channels,
            int sampleRate,
            int decoderReadCalls,
            long sourceBytesRead,
            long sourceReadCalls,
            long sourceEofReads,
            boolean streamClosedDuringDecode,
            int finalCloseCount,
            String failureClass,
            String failureDetail) {

        private static Observation decoded(Decoded decoded, TrackingInputStream input, boolean closedDuring) {
            return new Observation(
                    true, Hashes.sha256(decoded.pcm()), decoded.pcm().length,
                    decoded.channels(), decoded.sampleRate(), decoded.readCalls(), input.bytesRead(),
                    input.readCalls(), input.eofReads(), closedDuring, input.closeCount(), "", "");
        }

        private static Observation failed(Throwable failure, TrackingInputStream input, boolean closedDuring) {
            return new Observation(
                    false, "", 0, 0, 0, 0, input.bytesRead(), input.readCalls(), input.eofReads(),
                    closedDuring, input.closeCount(), failure.getClass().getName(), bounded(failure.getMessage()));
        }

        private String behaviorKey() {
            return decoded + "|" + pcmSha256 + "|" + pcmBytes + "|" + channels + "|" + sampleRate
                    + "|" + sourceBytesRead + "|" + sourceReadCalls + "|" + sourceEofReads
                    + "|" + streamClosedDuringDecode + "|" + finalCloseCount + "|" + failureClass;
        }

        private Map<String, Object> toMap(String id) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            values.put("validInput", false);
            values.put("decoded", decoded);
            values.put("actualPcmSha256", pcmSha256);
            values.put("actualPcmBytes", pcmBytes);
            values.put("actualChannels", channels);
            values.put("actualSampleRate", sampleRate);
            values.put("decoderReadCalls", decoderReadCalls);
            values.put("sourceBytesRead", sourceBytesRead);
            values.put("sourceReadCalls", sourceReadCalls);
            values.put("sourceEofReads", sourceEofReads);
            values.put("streamClosedDuringDecode", streamClosedDuringDecode);
            values.put("finalCloseCount", finalCloseCount);
            values.put("streamOwnershipExact", !streamClosedDuringDecode && finalCloseCount == 1);
            values.put("failureClass", failureClass);
            values.put("failureDetail", failureDetail);
            return Map.copyOf(values);
        }
    }
}
