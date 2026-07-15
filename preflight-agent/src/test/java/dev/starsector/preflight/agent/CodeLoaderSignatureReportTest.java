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

class CodeLoaderSignatureReportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedJaninoClassIsCapturedWithoutEnteringTextureCandidateBudget() throws Exception {
        byte[] bytes = classBytes(org.codehaus.janino.JavaSourceClassLoader.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path archive = temporaryDirectory.resolve("Starsector/starsector-core/janino.jar");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "synthetic Janino archive identity");

        Path adapterPath = temporaryDirectory.resolve("adapter.json");
        Path codePath = temporaryDirectory.resolve("adapter-code-loader-signatures.json");
        AdapterReport adapter = new AdapterReport(
                AdapterMode.PROBE, adapterPath, null, List.of("com/fs/"));
        CodeLoaderSignatureReport code = new CodeLoaderSignatureReport(codePath);
        AdapterProbeTransformer transformer = new AdapterProbeTransformer(
                AdapterMode.PROBE,
                AdapterTargetRegistry.empty(),
                List.of("com/fs/"),
                adapter,
                code);

        assertTrue(code.interested(signature.internalName()));
        assertNull(transformer.transform(
                getClass().getClassLoader(),
                signature.internalName(),
                null,
                domain(archive),
                bytes));
        adapter.write();
        code.write();

        String adapterJson = Files.readString(adapterPath);
        String codeJson = Files.readString(codePath);
        assertFalse(adapterJson.contains(signature.internalName()), adapterJson);
        assertTrue(codeJson.contains("\"retainedIdentities\":1"), codeJson);
        assertTrue(codeJson.contains("\"entriesTruncated\":false"), codeJson);
        assertTrue(codeJson.contains("org/codehaus/janino/JavaSourceClassLoader"), codeJson);
        assertTrue(codeJson.contains(signature.sha256()), codeJson);
        assertTrue(codeJson.contains("generateBytecodes"), codeJson);
        assertTrue(codeJson.contains("(Ljava/lang/String;)Ljava/util/Map;"), codeJson);
        assertTrue(codeJson.contains("findClass"), codeJson);
        assertTrue(codeJson.contains("janino.jar"), codeJson);
        assertTrue(codeJson.matches("(?s).*\\\"sourceSha256\\\":\\\"[0-9a-f]{64}\\\".*"), codeJson);
        assertTrue(codeJson.contains("\"automaticTargetGenerated\":false"), codeJson);
        assertTrue(codeJson.contains("\"liveTransformationEligible\":false"), codeJson);
    }

    @Test
    void retainedIdentitiesAreBoundedAndIndependentOfObservationOrder() throws Exception {
        byte[] bytes = classBytes(org.codehaus.janino.JavaSourceClassLoader.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("bounded.json");
        CodeLoaderSignatureReport report = new CodeLoaderSignatureReport(output);

        int total = CodeLoaderSignatureReport.IDENTITY_LIMIT + 5;
        for (int index = total - 1; index >= 0; index--) {
            String variant = String.format(Locale.ROOT, "variant-%04d", index);
            report.observed(signature, new AdapterSourceIdentity(
                    "file:/" + variant + "/janino.jar",
                    "/" + variant + "/janino.jar",
                    "OTHER",
                    "",
                    "",
                    "example.Loader",
                    variant));
        }
        report.write();

        String json = Files.readString(output);
        assertTrue(json.contains("\"identityLimit\":" + CodeLoaderSignatureReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"retainedIdentities\":" + CodeLoaderSignatureReport.IDENTITY_LIMIT), json);
        assertTrue(json.contains("\"entriesTruncated\":true"), json);
        assertTrue(json.contains("variant-0000"), json);
        assertTrue(json.contains(String.format(Locale.ROOT, "variant-%04d", CodeLoaderSignatureReport.IDENTITY_LIMIT - 1)), json);
        assertFalse(json.contains(String.format(Locale.ROOT, "variant-%04d", total - 1)), json);
    }

    @Test
    void unrelatedClassesAreIgnored() throws Exception {
        byte[] bytes = classBytes(CodeLoaderSignatureReportTest.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("ignored.json");
        CodeLoaderSignatureReport report = new CodeLoaderSignatureReport(output);

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
