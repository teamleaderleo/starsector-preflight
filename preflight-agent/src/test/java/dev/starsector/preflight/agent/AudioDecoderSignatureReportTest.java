package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioDecoderSignatureReportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void slickDecoderClassIsCapturedWithoutEnteringTextureCandidateBudget() throws Exception {
        byte[] bytes = classBytes(org.newdawn.slick.openal.OggDecoder.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path archive = temporaryDirectory.resolve("Starsector/starsector-core/slick.jar");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "synthetic Slick archive identity");

        Path adapterPath = temporaryDirectory.resolve("adapter.json");
        Path audioPath = temporaryDirectory.resolve("adapter-audio-decoder-signatures.json");
        AdapterReport adapter = new AdapterReport(
                AdapterMode.PROBE, adapterPath, null, List.of("com/fs/"));
        AudioDecoderSignatureReport audio = new AudioDecoderSignatureReport(audioPath);
        AdapterProbeTransformer transformer = new AdapterProbeTransformer(
                AdapterMode.PROBE,
                AdapterTargetRegistry.empty(),
                List.of("com/fs/"),
                adapter,
                null,
                audio);

        assertTrue(audio.interested(signature.internalName()));
        assertNull(transformer.transform(
                getClass().getClassLoader(),
                signature.internalName(),
                null,
                domain(archive),
                bytes));
        adapter.write();
        audio.write();

        String adapterJson = Files.readString(adapterPath);
        String audioJson = Files.readString(audioPath);
        assertFalse(adapterJson.contains(signature.internalName()), adapterJson);
        assertTrue(audioJson.contains("\"retainedIdentities\":1"), audioJson);
        assertTrue(audioJson.contains("\"entriesTruncated\":false"), audioJson);
        assertTrue(audioJson.contains("org/newdawn/slick/openal/OggDecoder"), audioJson);
        assertTrue(audioJson.contains(signature.sha256()), audioJson);
        assertTrue(audioJson.contains("\"role\":\"SLICK_OPENAL\""), audioJson);
        assertTrue(audioJson.contains("\"name\":\"decode\""), audioJson);
        assertTrue(audioJson.contains("(Ljava/io/InputStream;)[B"), audioJson);
        assertTrue(audioJson.contains("slick.jar"), audioJson);
        assertTrue(audioJson.matches("(?s).*\"sourceSha256\":\"[0-9a-f]{64}\".*"), audioJson);
        assertTrue(audioJson.contains("\"originalClassBytesRetained\":true"), audioJson);
        assertTrue(audioJson.contains("\"decoderEquivalenceEstablished\":false"), audioJson);
        assertTrue(audioJson.contains("\"preparedAudioWritesEligible\":false"), audioJson);
    }

    @Test
    void jorbisJoggAndSlickPrefixesAreSpecific() {
        AudioDecoderSignatureReport report = new AudioDecoderSignatureReport(
                temporaryDirectory.resolve("prefixes.json"));

        assertTrue(report.interested("com/jcraft/jorbis/VorbisFile"));
        assertTrue(report.interested("com.jcraft.jogg.SyncState"));
        assertTrue(report.interested("org/newdawn/slick/openal/OggInputStream"));
        assertFalse(report.interested("org/newdawn/slick/openalish/Fake"));
        assertFalse(report.interested("com/fs/starfarer/Audio"));
        assertFalse(report.interested(null));
    }

    @Test
    void retainedIdentitiesAreBoundedAndIndependentOfObservationOrder() throws Exception {
        byte[] bytes = classBytes(org.newdawn.slick.openal.OggDecoder.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("bounded.json");
        AudioDecoderSignatureReport report = new AudioDecoderSignatureReport(output);

        int total = AudioDecoderSignatureReport.IDENTITY_LIMIT + 5;
        for (int index = total - 1; index >= 0; index--) {
            String variant = String.format(Locale.ROOT, "variant-%04d", index);
            report.observed(signature, new AdapterSourceIdentity(
                    "file:/" + variant + "/slick.jar",
                    "/" + variant + "/slick.jar",
                    "OTHER",
                    "",
                    "",
                    "example.Loader",
                    variant));
        }
        report.write();

        String json = Files.readString(output);
        assertTrue(json.contains("\"identityLimit\":" + AudioDecoderSignatureReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"retainedIdentities\":" + AudioDecoderSignatureReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"entriesTruncated\":true"), json);
        assertTrue(json.contains("variant-0000"), json);
        assertTrue(json.contains(String.format(
                Locale.ROOT,
                "variant-%04d",
                AudioDecoderSignatureReport.IDENTITY_LIMIT - 1)), json);
        assertFalse(json.contains(String.format(Locale.ROOT, "variant-%04d", total - 1)), json);
    }

    @Test
    void unrelatedClassesAreIgnored() throws Exception {
        byte[] bytes = classBytes(AudioDecoderSignatureReportTest.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("ignored.json");
        AudioDecoderSignatureReport report = new AudioDecoderSignatureReport(output);

        assertFalse(report.interested(signature.internalName()));
        report.observed(signature, AdapterSourceIdentity.unknown());
        report.write();

        String json = Files.readString(output);
        assertTrue(json.contains("\"retainedIdentities\":0"), json);
        assertTrue(json.contains("\"entriesTruncated\":false"), json);
        assertFalse(json.contains(signature.sha256()), json);
    }

    private static ProtectionDomain domain(Path path) throws Exception {
        return new ProtectionDomain(
                new CodeSource(path.toUri().toURL(), (Certificate[]) null),
                null);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test class resource " + resource);
            return input.readAllBytes();
        }
    }
}
