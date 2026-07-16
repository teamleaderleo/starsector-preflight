package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BytecodeShapeReportTest {
    private static final Pattern INSTRUCTION_FINGERPRINT =
            Pattern.compile("\\\"instructionFingerprintSha256\\\":\\\"([0-9a-f]{64})\\\"");

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactIdentityCapturesDeterministicStructuralEvidenceWithoutStringContents() throws Exception {
        byte[] bytes = classBytes(ShapeFixture.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        String loaderClass = getClass().getClassLoader().getClass().getName().replace('.', '/');
        AdapterSourceIdentity source = new AdapterSourceIdentity(
                "file:/game/Starsector.app/Contents/Resources/Java/fs.common_obf.jar",
                "/game/Starsector.app/Contents/Resources/Java/fs.common_obf.jar",
                "STARSECTOR_CORE",
                "",
                "",
                loaderClass,
                "app");
        BytecodeShapeReport.CaptureTarget target = new BytecodeShapeReport.CaptureTarget(
                "synthetic-shape",
                signature.internalName(),
                signature.sha256(),
                "STARSECTOR_CORE",
                "contents/resources/java/fs.common_obf.jar",
                loaderClass,
                "app",
                List.of(
                        new BytecodeShapeReport.MethodKey(
                                "target", "(Ljava/awt/image/BufferedImage;I)Ljava/nio/ByteBuffer;"),
                        new BytecodeShapeReport.MethodKey(
                                "helper", "(Ljava/nio/ByteBuffer;Ljava/lang/String;)Ljava/nio/ByteBuffer;")));

        Path firstPath = temporaryDirectory.resolve("first/shape.json");
        BytecodeShapeReport first = new BytecodeShapeReport(firstPath, target);
        first.observed(signature, source, bytes);
        assertTrue(first.captured());
        first.write();

        Path secondPath = temporaryDirectory.resolve("second/shape.json");
        BytecodeShapeReport second = new BytecodeShapeReport(secondPath, target);
        second.observed(signature, source, bytes);
        second.write();

        String firstJson = Files.readString(firstPath);
        String secondJson = Files.readString(secondPath);
        assertTrue(firstJson.contains("\"captured\":true"), firstJson);
        assertTrue(firstJson.contains("\"classBytesIncluded\":false"), firstJson);
        assertTrue(firstJson.contains("\"stringConstantsIncluded\":false"), firstJson);
        assertTrue(firstJson.contains("\"kind\":\"string-redacted\""), firstJson);
        assertFalse(firstJson.contains("shape-secret-content"), firstJson);
        assertTrue(firstJson.contains("dev/starsector/preflight/agent/BytecodeShapeReportTest$ShapeFixture.valueI"), firstJson);
        assertTrue(firstJson.contains(".helper(Ljava/nio/ByteBuffer;Ljava/lang/String;)Ljava/nio/ByteBuffer;"), firstJson);
        assertTrue(firstJson.contains("\"flowPoints\":[{"), firstJson);
        assertEquals(fingerprints(firstJson), fingerprints(secondJson));
    }

    @Test
    void anyIdentityMismatchDeclinesCaptureButStillWritesBoundedStatus() throws Exception {
        byte[] bytes = classBytes(ShapeFixture.class);
        ClassSignature signature = ClassSignature.parse(bytes);
        AdapterSourceIdentity source = new AdapterSourceIdentity(
                "file:/other.jar",
                "/other.jar",
                "OTHER",
                "",
                "",
                "example/Loader",
                "other");
        BytecodeShapeReport.CaptureTarget target = new BytecodeShapeReport.CaptureTarget(
                "wrong-target",
                signature.internalName(),
                "0".repeat(64),
                "STARSECTOR_CORE",
                "fs.common_obf.jar",
                "example/Loader",
                "app",
                List.of(new BytecodeShapeReport.MethodKey(
                        "target", "(Ljava/awt/image/BufferedImage;I)Ljava/nio/ByteBuffer;")));
        Path output = temporaryDirectory.resolve("mismatch.json");
        BytecodeShapeReport report = new BytecodeShapeReport(output, target);

        report.observed(signature, source, bytes);
        report.write();

        assertFalse(report.captured());
        String json = Files.readString(output);
        assertTrue(json.contains("\"exactIdentityObserved\":false"), json);
        assertTrue(json.contains("\"captured\":false"), json);
        assertTrue(json.contains("\"shape\":null"), json);
    }

    private static List<String> fingerprints(String json) {
        Matcher matcher = INSTRUCTION_FINGERPRINT.matcher(json);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (matcher.find()) values.add(matcher.group(1));
        return List.copyOf(values);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test class resource " + resource);
            return input.readAllBytes();
        }
    }

    static final class ShapeFixture {
        private int value;

        ByteBuffer target(BufferedImage image, int count) {
            value = image.getWidth();
            if (count > 0) {
                return helper(ByteBuffer.allocate(value), "shape-secret-content");
            }
            return ByteBuffer.allocate(0);
        }

        private ByteBuffer helper(ByteBuffer buffer, String ignored) {
            return buffer;
        }
    }
}
