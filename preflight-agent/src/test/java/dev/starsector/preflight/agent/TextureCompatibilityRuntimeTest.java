package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureCompatibilityRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetRuntime() {
        TextureCompatibilityRuntime.beginSession();
    }

    @Test
    void validHitReconstructsPreparedPixelsAndReportsBytes() throws Exception {
        Fixture fixture = fixture();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));

        BufferedImage image = TextureCompatibilityRuntime.load("graphics/test.png");

        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xffff0000, image.getRGB(0, 0));
        assertEquals(0xff00ff00, image.getRGB(1, 0));
        assertEquals(0xff0000ff, image.getRGB(0, 1));
        assertEquals(0xffffffff, image.getRGB(1, 1));
        Map<String, Object> telemetry = TextureCompatibilityRuntime.telemetry();
        assertEquals(1L, telemetry.get("attempts"));
        assertEquals(1L, telemetry.get("hits"));
        assertEquals(0L, telemetry.get("fallbacks"));
        assertEquals(12L, telemetry.get("bytesServed"));
    }

    @Test
    void coldMissAndChangedSourceReturnNull() throws Exception {
        Fixture fixture = fixture();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));

        assertNull(TextureCompatibilityRuntime.load("graphics/missing.png"));
        Files.write(fixture.source(), new byte[] {9, 9, 9, 9});
        assertNull(TextureCompatibilityRuntime.load("graphics/test.png"));

        Map<String, Object> telemetry = TextureCompatibilityRuntime.telemetry();
        assertEquals(2L, telemetry.get("misses"));
        assertEquals(2L, telemetry.get("fallbacks"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reasons = (Map<String, Object>) telemetry.get("fallbackReasons");
        assertEquals(1L, reasons.get("entry-missing"));
        assertEquals(1L, reasons.get("source-changed"));
    }

    @Test
    void corruptBlobIsQuarantinedAndReturnsNull() throws Exception {
        Fixture fixture = fixture();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        Files.write(fixture.blob(), new byte[] {1, 2, 3});

        assertNull(TextureCompatibilityRuntime.load("graphics/test.png"));

        Map<String, Object> telemetry = TextureCompatibilityRuntime.telemetry();
        assertEquals(1L, telemetry.get("corruptions"));
        assertEquals(1L, telemetry.get("quarantined"));
        assertFalse(Files.exists(fixture.blob()));
        try (var files = Files.list(fixture.cache().resolve("quarantine"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void staleIndexDisablesPilotBeforeAnyClassRewrite() throws Exception {
        Fixture fixture = fixture();
        Files.write(fixture.source(), new byte[] {7});

        assertFalse(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        assertFalse(TextureCompatibilityRuntime.ready());

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) TextureCompatibilityRuntime.telemetry().get("disableReasons");
        assertTrue(reasons.contains("index-stale"), reasons.toString());
    }

    private Fixture fixture() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        Path sourceRoot = temporaryDirectory.resolve("game");
        Path source = sourceRoot.resolve("graphics/test.png");
        Files.createDirectories(source.getParent());
        byte[] encoded = {1, 2, 3, 4};
        Files.write(source, encoded);
        String sourceHash = Hashes.sha256(encoded);
        String profile = "ab".repeat(32);
        ResourceIndex index = new ResourceIndex(
                profile,
                List.of(new ResourceIndex.Root("core", sourceRoot, true)),
                Map.of("graphics/test.png", List.of(new ResourceIndex.Provider(
                        0,
                        "graphics/test.png",
                        Files.size(source),
                        Files.getLastModifiedTime(source).toMillis()))));
        Path indexPath = cache.resolve("indexes").resolve(profile + ".spfi");
        ResourceIndexIO.write(indexPath, index);

        byte[] bottomUpRgb = {
                0, 0, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255,
                (byte) 255, 0, 0,
                0, (byte) 255, 0
        };
        PreparedTexture texture = new PreparedTexture(
                sourceHash,
                PreparedTexture.Transformation.IDENTITY,
                2,
                2,
                2,
                2,
                3,
                0,
                0,
                0,
                bottomUpRgb);
        String blobRelative = "blobs/" + sourceHash.substring(0, 2) + "/" + sourceHash + "-identity.spft";
        Path blob = cache.resolve(blobRelative);
        PreparedTextureIO.write(blob, texture);
        TextureManifest manifest = new TextureManifest(profile, Map.of(
                "graphics/test.png",
                new TextureManifest.Entry(
                        sourceHash,
                        PreparedTexture.Transformation.IDENTITY,
                        blobRelative,
                        2,
                        2,
                        3,
                        bottomUpRgb.length)));
        Path manifestPath = cache.resolve("manifests").resolve(profile + ".spfm");
        TextureManifestIO.write(manifestPath, manifest);
        return new Fixture(cache, source, indexPath, manifestPath, blob);
    }

    private record Fixture(Path cache, Path source, Path index, Path manifest, Path blob) {
    }
}
