package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TexturePreparedPixelRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetRuntime() {
        TexturePreparedPixelRuntime.beginSession();
        TextureCompatibilityRuntime.beginSession();
    }

    @Test
    void suppliesDirectBottomUpPixelsStoredColorsAndBoundedOwnership() throws Exception {
        Fixture fixture = fixture();
        TexturePreparedPixelRuntime.beginSession();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));

        BufferedImage carrier = TexturePreparedPixelRuntime.load("graphics/test.png");
        assertTrue(TexturePreparedPixelRuntime.isCarrier(carrier));
        assertEquals(2, carrier.getWidth());
        assertEquals(2, carrier.getHeight());
        assertEquals("graphics/test.png", TexturePreparedPixelRuntime.originalPath(carrier));

        TexturePreparedPixelRuntime.PreparedPixel first = TexturePreparedPixelRuntime.prepare(carrier);
        TexturePreparedPixelRuntime.PreparedPixel second = TexturePreparedPixelRuntime.prepare(carrier);
        assertTrue(first.buffer().isDirect());
        assertTrue(second.buffer().isDirect());
        assertNotSame(first.buffer(), second.buffer());
        assertEquals(0, first.buffer().position());
        assertEquals(fixture.pixels().length, first.buffer().limit());
        assertArrayEquals(fixture.pixels(), bytes(first.buffer()));
        assertEquals(0xff0a141e, first.color0().getRGB());
        assertEquals(0xff28323c, first.color1().getRGB());
        assertEquals(0xff46505a, first.color2().getRGB());

        Map<String, Object> active = TexturePreparedPixelRuntime.telemetry();
        assertEquals(2L, active.get("hits"));
        assertEquals(2, active.get("activeBuffers"));
        assertEquals(0, active.get("pendingBuffers"));
        assertEquals(24L, active.get("activeDirectBytes"));
        assertEquals(24L, active.get("peakDirectBytes"));
        assertEquals(24L, active.get("bytesBypassed"));

        TexturePreparedPixelRuntime.release(first.buffer());
        TexturePreparedPixelRuntime.release(second.buffer());
        Map<String, Object> released = TexturePreparedPixelRuntime.telemetry();
        assertEquals(0, released.get("activeBuffers"));
        assertEquals(0L, released.get("activeDirectBytes"));
        assertEquals(2L, released.get("releases"));
        assertEquals(24L, released.get("releasedBytes"));

        Map<String, Object> cache = TextureCompatibilityRuntime.telemetry();
        assertEquals(1L, cache.get("attempts"));
        assertEquals(1L, cache.get("hits"));
        assertEquals(12L, cache.get("bytesServed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reportedPrepared = (Map<String, Object>) cache.get("preparedPixels");
        assertEquals(2L, reportedPrepared.get("hits"));
        assertEquals(2L, reportedPrepared.get("conversionCallsBypassed"));
        assertEquals(0, reportedPrepared.get("activeBuffers"));
    }

    private Fixture fixture() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        Path sourceRoot = temporaryDirectory.resolve("game");
        Path source = sourceRoot.resolve("graphics/test.png");
        Files.createDirectories(source.getParent());
        byte[] encoded = {1, 2, 3, 4};
        Files.write(source, encoded);
        String sourceHash = Hashes.sha256(encoded);
        String profile = "cd".repeat(32);
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

        byte[] pixels = {
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
                PreparedTexture.rgba(10, 20, 30, 255),
                PreparedTexture.rgba(40, 50, 60, 255),
                PreparedTexture.rgba(70, 80, 90, 255),
                pixels);
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
                        pixels.length)));
        Path manifestPath = cache.resolve("manifests").resolve(profile + ".spfm");
        TextureManifestIO.write(manifestPath, manifest);
        return new Fixture(cache, indexPath, manifestPath, pixels);
    }

    private static byte[] bytes(ByteBuffer source) {
        ByteBuffer copy = source.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private record Fixture(Path cache, Path index, Path manifest, byte[] pixels) {
        private Fixture {
            pixels = pixels.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }
}
