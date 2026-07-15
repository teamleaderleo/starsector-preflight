package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedImageBridgeTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetBridge() {
        PreparedImageBridge.resetForTests();
    }

    @Test
    void returnsEquivalentPixelsForLogicalAndPhysicalWinnerPaths() throws Exception {
        Fixture fixture = fixture("profile-a");
        PreparedImageBridge.configure(fixture.cache(), fixture.manifest(), fixture.index());

        BufferedImage logical = PreparedImageBridge.lookup(fixture.logicalPath());
        BufferedImage physical = PreparedImageBridge.lookup(fixture.source().toString());

        assertPixels(logical);
        assertPixels(physical);
        PreparedImageBridge.Snapshot snapshot = PreparedImageBridge.snapshot();
        assertTrue(snapshot.enabled());
        assertEquals(2, snapshot.hits());
        assertEquals(0, snapshot.fallbacks());
        assertEquals(2, snapshot.statuses().get("HIT"));
        assertEquals(2, totalStatuses(snapshot));
    }

    @Test
    void changedSourceMetadataFallsBackWithoutReadingCacheAsAHit() throws Exception {
        Fixture fixture = fixture("profile-a");
        PreparedImageBridge.configure(fixture.cache(), fixture.manifest(), fixture.index());
        Files.writeString(fixture.source(), "changed source size");

        assertNull(PreparedImageBridge.lookup(fixture.logicalPath()));

        PreparedImageBridge.Snapshot snapshot = PreparedImageBridge.snapshot();
        assertEquals(0, snapshot.hits());
        assertEquals(1, snapshot.fallbacks());
        assertEquals(1, snapshot.statuses().get("STALE"));
        assertEquals(1, totalStatuses(snapshot));
    }

    @Test
    void corruptBlobAndUnknownPathBothFallThrough() throws Exception {
        Fixture fixture = fixture("profile-a");
        PreparedImageBridge.configure(fixture.cache(), fixture.manifest(), fixture.index());
        Files.write(fixture.blob(), new byte[] {1, 2, 3});

        assertNull(PreparedImageBridge.lookup(fixture.logicalPath()));
        assertNull(PreparedImageBridge.lookup("graphics/missing.png"));

        PreparedImageBridge.Snapshot snapshot = PreparedImageBridge.snapshot();
        assertEquals(2, snapshot.fallbacks());
        assertEquals(1, snapshot.statuses().get("CORRUPT"));
        assertEquals(1, snapshot.statuses().get("MISS"));
        assertEquals(0, snapshot.statuses().get("HIT"));
        assertEquals(2, totalStatuses(snapshot));
    }

    @Test
    void unsupportedPreparedShapeIsNotAlsoCountedAsAHit() throws Exception {
        Fixture fixture = unsupportedFixture("profile-a");
        PreparedImageBridge.configure(fixture.cache(), fixture.manifest(), fixture.index());

        assertNull(PreparedImageBridge.lookup(fixture.logicalPath()));

        PreparedImageBridge.Snapshot snapshot = PreparedImageBridge.snapshot();
        assertEquals(0, snapshot.hits());
        assertEquals(1, snapshot.fallbacks());
        assertEquals(0, snapshot.statuses().get("HIT"));
        assertEquals(1, snapshot.statuses().get("UNSUPPORTED"));
        assertEquals(1, totalStatuses(snapshot));
    }

    @Test
    void mismatchedManifestAndIndexFingerprintsDeclineConfiguration() throws Exception {
        Fixture fixture = fixture("profile-a");
        TextureManifest mismatched = new TextureManifest("profile-b", Map.of());
        TextureManifestIO.write(fixture.manifest(), mismatched);

        IOException error = assertThrows(IOException.class, () -> PreparedImageBridge.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));

        assertTrue(error.getMessage().contains("fingerprints differ"));
        assertFalse(PreparedImageBridge.ready());
    }

    private Fixture fixture(String fingerprint) throws Exception {
        byte[] bottomUpRgb = {
                0, 0, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255,
                (byte) 255, 0, 0,
                0, (byte) 255, 0
        };
        PreparedTexture texture = new PreparedTexture(
                "a".repeat(64),
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
        return fixture(fingerprint, texture);
    }

    private Fixture unsupportedFixture(String fingerprint) throws Exception {
        PreparedTexture texture = new PreparedTexture(
                "b".repeat(64),
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                2,
                2,
                3,
                0,
                0,
                0,
                new byte[12]);
        return fixture(fingerprint, texture);
    }

    private Fixture fixture(String fingerprint, PreparedTexture texture) throws Exception {
        Path root = temporaryDirectory.resolve("game/core");
        Path source = root.resolve("graphics/test.png");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "encoded source placeholder");
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        String logicalPath = "graphics/test.png";
        ResourceIndex.Provider provider = new ResourceIndex.Provider(
                0,
                logicalPath,
                attributes.size(),
                Math.max(0, attributes.lastModifiedTime().toMillis()));
        ResourceIndex index = new ResourceIndex(
                fingerprint,
                List.of(new ResourceIndex.Root("core", root, true)),
                Map.of(logicalPath, List.of(provider)));

        Path cache = temporaryDirectory.resolve("cache");
        Path blob = cache.resolve("blobs/test.spft");
        PreparedTextureIO.write(blob, texture);
        TextureManifest manifest = new TextureManifest(fingerprint, Map.of(
                logicalPath,
                new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        "blobs/test.spft",
                        texture.uploadWidth(),
                        texture.uploadHeight(),
                        texture.channels(),
                        texture.pixelBytes())));
        Path manifestPath = temporaryDirectory.resolve("manifest.spfm");
        Path indexPath = temporaryDirectory.resolve("index.spfi");
        TextureManifestIO.write(manifestPath, manifest);
        ResourceIndexIO.write(indexPath, index);
        return new Fixture(cache, manifestPath, indexPath, source, blob, logicalPath);
    }

    private static long totalStatuses(PreparedImageBridge.Snapshot snapshot) {
        return snapshot.statuses().values().stream().mapToLong(Long::longValue).sum();
    }

    private static void assertPixels(BufferedImage image) {
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xffff0000, image.getRGB(0, 0));
        assertEquals(0xff00ff00, image.getRGB(1, 0));
        assertEquals(0xff0000ff, image.getRGB(0, 1));
        assertEquals(0xffffffff, image.getRGB(1, 1));
    }

    private record Fixture(
            Path cache,
            Path manifest,
            Path index,
            Path source,
            Path blob,
            String logicalPath) {
    }
}
