package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);

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
        assertEquals(first.pixelBytes(), first.buffer().remaining());
        assertEquals(first.width() * first.height() * first.channels(), first.buffer().remaining());
        assertEquals(2, first.width());
        assertEquals(2, first.height());
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

    @Test
    void declinesObservedNpotRgbBeforeCreatingCarrierOrDirectBuffer() throws Exception {
        Fixture fixture = fixture(597, 373, 3);
        TexturePreparedPixelRuntime.beginSession();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);

        assertEquals(1024, TexturePreparedPixelRuntime.expectedUploadDimension(597));
        assertEquals(512, TexturePreparedPixelRuntime.expectedUploadDimension(373));
        assertEquals(668_043, fixture.pixels().length);
        assertEquals(1_572_864, 1024 * 512 * 3);
        assertNull(TexturePreparedPixelRuntime.load("graphics/test.png"));

        Map<String, Object> telemetry = TexturePreparedPixelRuntime.telemetry();
        assertEquals(1L, telemetry.get("fallbacks"));
        assertEquals(1L, telemetry.get("dimensionFallbacks"));
        assertEquals(0L, telemetry.get("directAttempts"));
        assertEquals(0, telemetry.get("activeBuffers"));
        assertEquals(0L, telemetry.get("activeDirectBytes"));
    }

    @Test
    void declinesNpotRgbaBeforeCreatingCarrierOrDirectBuffer() throws Exception {
        Fixture fixture = fixture(3, 5, 4);
        TexturePreparedPixelRuntime.beginSession();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);

        assertEquals(4, TexturePreparedPixelRuntime.expectedUploadDimension(3));
        assertEquals(8, TexturePreparedPixelRuntime.expectedUploadDimension(5));
        assertNull(TexturePreparedPixelRuntime.load("graphics/test.png"));

        Map<String, Object> telemetry = TexturePreparedPixelRuntime.telemetry();
        assertEquals(1L, telemetry.get("dimensionFallbacks"));
        assertEquals(0, telemetry.get("activeBuffers"));
        assertEquals(0L, telemetry.get("activeDirectBytes"));
    }

    @Test
    void exceptionalCallerReleaseReturnsAccountingToZero() throws Exception {
        Fixture fixture = fixture();
        TexturePreparedPixelRuntime.beginSession();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);

        TexturePreparedPixelRuntime.PreparedPixel prepared = TexturePreparedPixelRuntime.prepare(
                TexturePreparedPixelRuntime.load("graphics/test.png"));
        assertEquals(1, TexturePreparedPixelRuntime.telemetry().get("activeBuffers"));
        TexturePreparedPixelRuntime.releaseCurrentThreadBuffer();
        TexturePreparedPixelRuntime.releaseCurrentThreadBuffer();

        Map<String, Object> telemetry = TexturePreparedPixelRuntime.telemetry();
        assertEquals(0, telemetry.get("activeBuffers"));
        assertEquals(0L, telemetry.get("activeDirectBytes"));
        assertEquals(1L, telemetry.get("releases"));
        assertEquals((long) prepared.pixelBytes(), telemetry.get("releasedBytes"));
    }

    @Test
    void compatibilitySelectionNeverReportsOrServesPreparedPixels() throws Exception {
        Fixture fixture = fixture();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.COMPATIBILITY);

        assertFalse(TexturePreparedPixelRuntime.ready());
        assertFalse(AdapterTransformationRegistry.hasPlan(TexturePreparedPixelRuntime.PLAN_ID));
        assertEquals(null, TexturePreparedPixelRuntime.load("graphics/test.png"));
        assertEquals(Boolean.FALSE, TexturePreparedPixelRuntime.telemetry().get("ready"));
        assertEquals(0L, TextureCompatibilityRuntime.telemetry().get("attempts"));

        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);
        assertTrue(TexturePreparedPixelRuntime.ready());
        assertTrue(AdapterTransformationRegistry.hasPlan(TexturePreparedPixelRuntime.PLAN_ID));

        TexturePreparedPixelRuntime.beginSession();
        assertFalse(TexturePreparedPixelRuntime.ready());
    }

    private Fixture fixture() throws Exception {
        byte[] pixels = {
                0, 0, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255,
                (byte) 255, 0, 0,
                0, (byte) 255, 0
        };
        return fixture(2, 2, 3, pixels);
    }

    private Fixture fixture(int width, int height, int channels) throws Exception {
        int length = Math.multiplyExact(Math.multiplyExact(width, height), channels);
        return fixture(width, height, channels, new byte[length]);
    }

    private Fixture fixture(int width, int height, int channels, byte[] pixels) throws Exception {
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

        PreparedTexture texture = new PreparedTexture(
                sourceHash,
                PreparedTexture.Transformation.IDENTITY,
                width,
                height,
                width,
                height,
                channels,
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
                        width,
                        height,
                        channels,
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
