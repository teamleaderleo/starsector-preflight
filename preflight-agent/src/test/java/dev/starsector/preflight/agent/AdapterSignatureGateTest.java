package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterSignatureGateTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesDeterministicClassAndMethodSignatures() throws Exception {
        byte[] bytes = classBytes();
        ClassSignature first = ClassSignature.parse(bytes);
        ClassSignature second = ClassSignature.parse(bytes);

        assertEquals("dev/starsector/preflight/agent/AdapterSignatureGateTest", first.internalName());
        assertEquals(first, second);
        assertEquals(61, first.majorVersion());
        assertTrue(first.hasMethod("parsesDeterministicClassAndMethodSignatures", "()V"));
        assertThrows(IOException.class, () -> ClassSignature.parse(new byte[] {0, 1, 2, 3}));
    }

    @Test
    void targetRequiresExactHashAndMethods() throws Exception {
        ClassSignature signature = ClassSignature.parse(classBytes());
        AdapterTarget exact = new AdapterTarget(
                "synthetic",
                signature.internalName(),
                signature.sha256(),
                "synthetic-plan",
                List.of(new AdapterTarget.RequiredMethod(
                        "targetRequiresExactHashAndMethods", "()V")));
        assertTrue(exact.match(signature).exact());

        AdapterTarget wrongHash = new AdapterTarget(
                "wrong-hash",
                signature.internalName(),
                "0".repeat(64),
                "synthetic-plan",
                exact.requiredMethods());
        assertFalse(wrongHash.match(signature).exact());

        AdapterTarget missingMethod = new AdapterTarget(
                "missing-method",
                signature.internalName(),
                signature.sha256(),
                "synthetic-plan",
                List.of(new AdapterTarget.RequiredMethod("missing", "()V")));
        assertFalse(missingMethod.match(signature).exact());
    }

    @Test
    void probeAndEnabledModesAlwaysRetainOriginalBytesWithoutRegisteredPlan() throws Exception {
        byte[] bytes = classBytes();
        ClassSignature signature = ClassSignature.parse(bytes);
        Path targetFile = temporaryDirectory.resolve("targets.txt");
        Files.writeString(targetFile, """
                target synthetic
                class %s
                sha256 %s
                plan synthetic-plan
                method probeAndEnabledModesAlwaysRetainOriginalBytesWithoutRegisteredPlan ()V
                end
                """.formatted(signature.internalName(), signature.sha256()));
        AdapterTargetRegistry registry = AdapterTargetRegistry.load(targetFile);

        Path probeReportPath = temporaryDirectory.resolve("probe.json");
        AdapterReport probeReport = new AdapterReport(
                AdapterMode.PROBE, probeReportPath, targetFile, List.of("dev/starsector/"));
        AdapterProbeTransformer probe = new AdapterProbeTransformer(
                AdapterMode.PROBE, registry, List.of("dev/starsector/"), probeReport);
        assertNull(probe.transform(null, signature.internalName(), null, null, bytes));
        probeReport.write();
        String probeJson = Files.readString(probeReportPath);
        assertTrue(probeJson.contains("\"exactMatches\":1"));
        assertTrue(probeJson.contains("\"transformationsApplied\":0"));

        Path enabledReportPath = temporaryDirectory.resolve("enabled.json");
        AdapterReport enabledReport = new AdapterReport(
                AdapterMode.ENABLED, enabledReportPath, targetFile, List.of("dev/starsector/"));
        AdapterProbeTransformer enabled = new AdapterProbeTransformer(
                AdapterMode.ENABLED, registry, List.of("dev/starsector/"), enabledReport);
        assertNull(enabled.transform(null, signature.internalName(), null, null, bytes));
        enabledReport.write();
        String enabledJson = Files.readString(enabledReportPath);
        assertTrue(enabledJson.contains("\"transformationEligible\":1"));
        assertTrue(enabledJson.contains("not registered in this build"));
        assertTrue(enabledJson.contains("\"transformationsApplied\":0"));
    }

    @Test
    void emptyRegistryAndMalformedClassesRemainNonfatal() throws Exception {
        Path reportPath = temporaryDirectory.resolve("empty.json");
        AdapterReport report = new AdapterReport(
                AdapterMode.ENABLED, reportPath, null, List.of("dev/starsector/"));
        AdapterProbeTransformer transformer = new AdapterProbeTransformer(
                AdapterMode.ENABLED,
                AdapterTargetRegistry.empty(),
                List.of("dev/starsector/"),
                report);
        assertNull(transformer.transform(null, "dev/starsector/Broken", null, null, new byte[] {1, 2, 3}));
        assertNull(transformer.transform(null, "other/Unrelated", null, null, classBytes()));
        report.write();
        String json = Files.readString(reportPath);
        assertTrue(json.contains("\"malformedClasses\":1"));
        assertTrue(json.contains("\"transformationsApplied\":0"));
    }

    @Test
    void registryRejectsDuplicateIdsAndOversizedLines() throws Exception {
        ClassSignature signature = ClassSignature.parse(classBytes());
        String target = """
                target duplicate
                class %s
                sha256 %s
                method parsesDeterministicClassAndMethodSignatures ()V
                end
                """.formatted(signature.internalName(), signature.sha256());
        Path duplicates = temporaryDirectory.resolve("duplicates.txt");
        Files.writeString(duplicates, target + target);
        IOException duplicate = assertThrows(IOException.class, () -> AdapterTargetRegistry.load(duplicates));
        assertTrue(duplicate.getMessage().contains("Duplicate target ID"), duplicate.getMessage());

        Path oversizedLine = temporaryDirectory.resolve("oversized-line.txt");
        Files.writeString(oversizedLine, "#" + "x".repeat(4_096) + System.lineSeparator());
        IOException oversized = assertThrows(IOException.class, () -> AdapterTargetRegistry.load(oversizedLine));
        assertTrue(oversized.getMessage().contains("Line exceeds"), oversized.getMessage());
    }

    @Test
    void killSwitchAcceptsPropertyOrEnvironment() {
        Properties properties = new Properties();
        assertFalse(AdapterRuntime.killSwitch(Map.of(), properties));
        properties.setProperty("preflight.adapter.disabled", "true");
        assertTrue(AdapterRuntime.killSwitch(Map.of(), properties));
        assertTrue(AdapterRuntime.killSwitch(Map.of("PREFLIGHT_DISABLE_ADAPTER", "1"), new Properties()));
    }

    private static byte[] classBytes() throws IOException {
        String resource = "/" + AdapterSignatureGateTest.class.getName().replace('.', '/') + ".class";
        try (InputStream input = AdapterSignatureGateTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test class resource " + resource);
            }
            return input.readAllBytes();
        }
    }
}
