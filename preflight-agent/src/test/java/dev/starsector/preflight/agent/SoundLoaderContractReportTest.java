package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundLoaderContractReportTest {
    private static final String SYNTHETIC_LITERAL = "repository-owned-sound-contract-literal";

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactTargetsAreIncludedAndUnrelatedClassesAreExcluded() {
        SoundLoaderContractReport report = new SoundLoaderContractReport(
                temporaryDirectory.resolve("targets.json"));

        assertTrue(report.interested("sound/J"));
        assertTrue(report.interested("sound.F"));
        assertTrue(report.interested("sound/ooOO"));
        assertTrue(report.interested("sound/D"));
        assertTrue(report.interested("sound/Sound"));
        assertTrue(report.interested("sound/void"));
        assertTrue(report.interested("com/fs/starfarer/loading/A"));
        assertFalse(report.interested("sound/JExtra"));
        assertFalse(report.interested("org/newdawn/slick/openal/OggDecoder"));
        assertFalse(report.interested(null));
    }

    @Test
    void exactSeamProducesBoundedRedactedStructuralEvidenceAndPreservesBytes() throws Exception {
        byte[] bytes = classBytes(sound.J.class);
        byte[] original = bytes.clone();
        ClassSignature signature = ClassSignature.parse(bytes);
        Path archive = temporaryDirectory.resolve("Starsector/contents/resources/java/fs.common_obf.jar");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "repository-owned source archive fixture");

        Path adapterPath = temporaryDirectory.resolve("adapter.json");
        Path soundPath = temporaryDirectory.resolve("adapter-sound-loader-contract.json");
        AdapterReport adapter = new AdapterReport(
                AdapterMode.PROBE, adapterPath, null, List.of("com/fs/"));
        SoundLoaderContractReport sound = new SoundLoaderContractReport(soundPath);
        AdapterProbeTransformer transformer = new AdapterProbeTransformer(
                AdapterMode.PROBE,
                AdapterTargetRegistry.empty(),
                List.of("com/fs/"),
                adapter,
                null,
                null,
                sound);

        assertNull(transformer.transform(
                getClass().getClassLoader(),
                signature.internalName(),
                null,
                domain(archive),
                bytes));
        assertArrayEquals(original, bytes);
        adapter.write();
        sound.write();

        String adapterJson = Files.readString(adapterPath);
        String json = Files.readString(soundPath);
        assertFalse(adapterJson.contains("sound/J"), adapterJson);
        assertTrue(json.contains("starsector-preflight-sound-loader-contract-v1"), json);
        assertTrue(json.contains("\"retainedIdentities\":1"), json);
        assertTrue(json.contains("\"className\":\"sound/J\""), json);
        assertTrue(json.contains(signature.sha256()), json);
        assertTrue(json.contains("\"majorVersion\":" + signature.majorVersion()), json);
        assertTrue(json.contains("\"descriptor\":\"(Ljava/io/InputStream;)Lsound/F;\""), json);
        assertTrue(json.contains("\"primarySeam\":true"), json);
        assertTrue(json.contains("\"sourceKind\":\"STARSECTOR_CORE\""), json);
        assertTrue(json.contains("\"sourceSuffix\":\"contents/resources/java/fs.common_obf.jar\""), json);
        assertTrue(json.matches("(?s).*\"sourceSha256\":\"[0-9a-f]{64}\".*"), json);
        assertTrue(json.contains("\"loaderClass\":"), json);
        assertTrue(json.contains("\"loaderName\":"), json);
        assertTrue(json.matches("(?s).*\"identityKey\":\"sound-loader-source-loader-v1:[0-9a-f]{64}\".*"), json);

        assertTrue(json.contains("\"owner\":\"sound/J\""), json);
        assertTrue(json.contains("\"name\":\"normalize\""), json);
        assertTrue(json.contains("\"owner\":\"com/jcraft/jorbis/Info\""), json);
        assertTrue(json.contains("\"name\":\"synthesis_headerin\""), json);
        assertTrue(json.contains("\"owner\":\"sound/D\""), json);
        assertTrue(json.contains("\"constructor\":true"), json);
        assertTrue(json.contains("\"caughtType\":\"java/io/IOException\""), json);
        assertTrue(json.contains("\"kind\":\"field-write\""), json);
        assertTrue(json.contains("\"kind\":\"jogg-jorbis-call\""), json);
        assertTrue(json.contains("\"kind\":\"constructor-consuming-sound-f\""), json);
        assertTrue(json.contains("\"kind\":\"return\""), json);
        assertTrue(json.contains("\"kind\":\"throw\""), json);
        assertTrue(json.contains("\"maxStack\":"), json);
        assertTrue(json.contains("\"maxLocals\":"), json);
        assertTrue(json.contains("\"frameValueLimit\":" + SoundLoaderContractReport.FRAME_VALUE_LIMIT), json);
        assertTrue(json.contains("\"flowPointsTruncated\":false"), json);

        assertFalse(json.contains(SYNTHETIC_LITERAL), json);
        assertTrue(json.contains(sha256(SYNTHETIC_LITERAL)), json);
        assertFalse(json.contains(Base64.getEncoder().encodeToString(original)), json);
        assertFalse(json.contains(HexFormat.of().formatHex(original)), json);
        assertTrue(json.contains("\"literalStringsIncluded\":false"), json);
        assertTrue(json.contains("\"classBytesIncluded\":false"), json);
        assertTrue(json.contains("\"bytecodeListingsIncluded\":false"), json);
        assertTrue(json.contains("\"originalClassBytesRetained\":true"), json);
        assertTrue(json.contains("\"automaticAllowlistGenerated\":false"), json);
        assertTrue(json.contains("\"transformationPlanGenerated\":false"), json);
        assertTrue(json.contains("\"transformRegistered\":false"), json);
        assertTrue(json.contains("\"cacheReadsEnabled\":false"), json);
        assertTrue(json.contains("\"cacheWritesEnabled\":false"), json);
        assertTrue(json.contains("\"preparedAudioWritesEligible\":false"), json);
        assertTrue(json.contains("\"requiresHumanReview\":true"), json);
    }

    @Test
    void distinctSourceAndLoaderIdentitiesAreRetainedInDeterministicOrder() throws Exception {
        byte[] bytes = classBytes(sound.J.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        AdapterSourceIdentity alpha = source("/alpha/contents/resources/java/fs.common_obf.jar", "alpha.Loader", "a");
        AdapterSourceIdentity omega = source("/omega/contents/resources/java/fs.common_obf.jar", "omega.Loader", "z");

        Path firstPath = temporaryDirectory.resolve("first.json");
        SoundLoaderContractReport first = new SoundLoaderContractReport(firstPath);
        first.observed(signature, omega, bytes);
        first.observed(signature, alpha, bytes);
        first.write();

        Path secondPath = temporaryDirectory.resolve("second.json");
        SoundLoaderContractReport second = new SoundLoaderContractReport(secondPath);
        second.observed(signature, alpha, bytes);
        second.observed(signature, omega, bytes);
        second.write();

        String firstJson = Files.readString(firstPath);
        String secondJson = Files.readString(secondPath);
        assertTrue(firstJson.contains("\"retainedIdentities\":2"), firstJson);
        assertTrue(firstJson.indexOf("/alpha/") < firstJson.indexOf("/omega/"), firstJson);
        assertEquals(entriesAndDiagnostics(firstJson), entriesAndDiagnostics(secondJson));
    }

    @Test
    void typedIdentityEncodingResistsConcatenationCollisions() {
        String first = SoundLoaderContractReport.typedIdentityKey("sound/J", "ab", "c", "d", "e");
        String second = SoundLoaderContractReport.typedIdentityKey("sound/J", "a", "bc", "d", "e");
        String third = SoundLoaderContractReport.typedIdentityKey("sound/J", "ab", "c", "de", "");

        assertNotEquals(first, second);
        assertNotEquals(first, third);
        assertTrue(first.matches("sound-loader-source-loader-v1:[0-9a-f]{64}"), first);
    }

    @Test
    void identityRetentionIsBoundedAndMethodInventoryIsCompleteWithinCeiling() throws Exception {
        byte[] bytes = classBytes(sound.J.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("bounded.json");
        SoundLoaderContractReport report = new SoundLoaderContractReport(output);

        int total = SoundLoaderContractReport.IDENTITY_LIMIT + 5;
        for (int index = total - 1; index >= 0; index--) {
            String variant = String.format(Locale.ROOT, "variant-%04d", index);
            report.observed(signature, source("/" + variant + "/fs.common_obf.jar", "example.Loader", variant), bytes);
        }
        report.write();

        String json = Files.readString(output);
        assertTrue(json.contains("\"identityLimit\":" + SoundLoaderContractReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"retainedIdentities\":" + SoundLoaderContractReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"entriesTruncated\":true"), json);
        assertTrue(json.contains("variant-0000"), json);
        assertFalse(json.contains(String.format(Locale.ROOT, "variant-%04d", total - 1)), json);
        assertTrue(json.contains("\"methodLimitPerIdentity\":" + SoundLoaderContractReport.METHOD_LIMIT), json);
        assertTrue(json.contains("\"methodsTruncated\":false"), json);
        assertTrue(json.contains("\"descriptor\":\"(Ljava/io/InputStream;)Lsound/F;\""), json);
    }

    private static AdapterSourceIdentity source(String path, String loaderClass, String loaderName) {
        return new AdapterSourceIdentity(
                "file:" + path,
                path,
                "STARSECTOR_CORE",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "",
                loaderClass,
                loaderName);
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

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String entriesAndDiagnostics(String json) {
        return json.substring(json.indexOf("\"entries\":"));
    }
}
