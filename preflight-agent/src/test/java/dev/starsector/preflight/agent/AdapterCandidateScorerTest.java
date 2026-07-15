package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterCandidateScorerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void textureLoaderOutranksUnrelatedCampaignClassDeterministically() throws Exception {
        ClassSignature texture = signature(TextureLoaderFixture.class);
        ClassSignature campaign = signature(CampaignManagerFixture.class);

        AdapterCandidateScorer.Score first = AdapterCandidateScorer.score(
                texture, "file:/game/starsector-core/starfarer_obf.jar");
        AdapterCandidateScorer.Score second = AdapterCandidateScorer.score(
                texture, "file:/game/starsector-core/starfarer_obf.jar");
        AdapterCandidateScorer.Score unrelated = AdapterCandidateScorer.score(
                campaign, "file:/game/starsector-core/starfarer_obf.jar");

        assertEquals(first, second);
        assertTrue(first.value() > unrelated.value() + 100,
                () -> "texture=" + first.value() + " unrelated=" + unrelated.value());
        assertEquals("STARSECTOR_CORE", first.sourceKind());
        assertTrue(first.relevantMethods().stream().anyMatch(method ->
                method.name().equals("loadTexture")
                        && method.descriptor().contains("Ljava/nio/ByteBuffer;")));
        assertTrue(first.evidence().stream().anyMatch(value -> value.contains("loadTexture")));
    }

    @Test
    void obfuscatedLegacyGraphicsDecoderReceivesRetentionPriority() {
        ClassSignature.Method decoder = new ClassSignature.Method(
                "o00000",
                "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;",
                0);
        ClassSignature graphics = new ClassSignature(
                "com/fs/graphics/L",
                "0".repeat(64),
                61,
                0,
                List.of(decoder));
        ClassSignature generic = new ClassSignature(
                "com/fs/starfarer/L",
                "1".repeat(64),
                61,
                0,
                List.of(decoder));

        AdapterCandidateScorer.Score graphicsScore = AdapterCandidateScorer.score(
                graphics, "file:/game/starsector-core/starfarer_obf.jar");
        AdapterCandidateScorer.Score genericScore = AdapterCandidateScorer.score(
                generic, "file:/game/starsector-core/starfarer_obf.jar");

        assertEquals(genericScore.value() + 80, graphicsScore.value());
        assertTrue(graphicsScore.evidence().contains("legacy Starsector graphics package"));
        assertTrue(graphicsScore.relevantMethods().stream().anyMatch(method ->
                method.name().equals("o00000")
                        && method.descriptor().equals("(Ljava/lang/String;)Ljava/awt/image/BufferedImage;")));
    }

    @Test
    void reportRanksCandidatesAndIncludesExactMethodDescriptors() throws Exception {
        Path reportPath = temporaryDirectory.resolve("adapter.json");
        AdapterReport report = new AdapterReport(
                AdapterMode.PROBE,
                reportPath,
                null,
                java.util.List.of("dev/starsector/preflight/agent/"));
        ProtectionDomain core = domain("file:/game/starsector-core/starfarer_obf.jar");
        AdapterSourceIdentity identity = AdapterSourceIdentity.capture(
                getClass().getClassLoader(), core, false);
        report.observed(signature(CampaignManagerFixture.class), identity);
        report.observed(signature(TextureLoaderFixture.class), identity);
        report.write();

        String json = Files.readString(reportPath);
        int ranked = json.indexOf("\"rankedCandidates\"");
        int texture = json.indexOf("TextureLoaderFixture", ranked);
        int campaign = json.indexOf("CampaignManagerFixture", ranked);
        assertTrue(ranked >= 0, json);
        assertTrue(texture > ranked, json);
        assertTrue(campaign > texture, json);
        assertTrue(json.contains("\"name\":\"loadTexture\""), json);
        assertTrue(json.contains("Ljava/nio/ByteBuffer;"), json);
        assertTrue(json.contains("\"sourceKind\":\"STARSECTOR_CORE\""), json);
        assertTrue(json.contains("\"loaderClass\":"), json);
    }

    @Test
    void sourceOwnershipSeparatesFastRenderingAndMods() throws Exception {
        ClassSignature signature = signature(TextureLoaderFixture.class);
        assertEquals("FAST_RENDERING", AdapterCandidateScorer.score(
                signature, "file:/game/mods/Fast-Rendering/fast-rendering.jar").sourceKind());
        assertEquals("MOD", AdapterCandidateScorer.score(
                signature, "file:/game/mods/example/example.jar").sourceKind());
        assertEquals("UNKNOWN", AdapterCandidateScorer.score(signature, null).sourceKind());
    }

    private static ClassSignature signature(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test class resource " + resource);
            }
            return ClassSignature.parse(input.readAllBytes());
        }
    }

    private static ProtectionDomain domain(String location) throws Exception {
        return new ProtectionDomain(
                new CodeSource(new URL(location), (Certificate[]) null),
                null);
    }

    static final class TextureLoaderFixture {
        ByteBuffer loadTexture(String logicalPath) {
            return ByteBuffer.wrap(logicalPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        BufferedImage decodeImage(InputStream input) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
    }

    static final class CampaignManagerFixture {
        void advance(float amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("amount");
            }
        }
    }
}
