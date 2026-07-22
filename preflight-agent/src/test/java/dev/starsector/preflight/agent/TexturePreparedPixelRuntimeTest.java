package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        configure(fixture);

        BufferedImage carrier = TexturePreparedPixelRuntime.load("graphics/test.png");
        assertTrue(TexturePreparedPixelRuntime.isCarrier(carrier));
        assertEquals(2, carrier.getWidth());
        assertEquals(2, carrier.getHeight());
        assertEquals("graphics/test.png", TexturePreparedPixelRuntime.originalPath(carrier));

        TexturePreparedPixelRuntime.PreparedPixel first = TexturePreparedPixelRuntime.prepare(carrier);
        TexturePreparedPixelRuntime.PreparedPixel second = TexturePreparedPixelRuntime.prepare(carrier);
        assertNotNull(first);
        assertNotNull(second);
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
        assertEquals(0L, active.get("paddedUploads"));
        assertEquals(0L, active.get("paddingBytes"));
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
    }

    @Test
    void padsObservedNpotRgbToExactPowerOfTwoBacking() throws Exception {
        int sourceWidth = 597;
        int sourceHeight = 373;
        int channels = 3;
        byte[] source = new byte[sourceWidth * sourceHeight * channels];
        setPixel(source, sourceWidth, channels, 0, 0, 1, 2, 3, 0);
        setPixel(source, sourceWidth, channels, sourceWidth - 1, 0, 4, 5, 6, 0);
        setPixel(source, sourceWidth, channels, 0, 1, 7, 8, 9, 0);
        setPixel(source, sourceWidth, channels, 0, sourceHeight - 1, 10, 11, 12, 0);
        Fixture fixture = fixture(sourceWidth, sourceHeight, channels, source);
        configure(fixture);

        assertEquals(1024, TexturePreparedPixelRuntime.expectedUploadDimension(sourceWidth));
        assertEquals(512, TexturePreparedPixelRuntime.expectedUploadDimension(sourceHeight));
        assertEquals(668_043, source.length);

        BufferedImage carrier = TexturePreparedPixelRuntime.load("graphics/test.png");
        assertNotNull(carrier);
        TexturePreparedPixelRuntime.PreparedPixel prepared = TexturePreparedPixelRuntime.prepare(carrier);
        assertNotNull(prepared);
        assertEquals(1024, prepared.width());
        assertEquals(512, prepared.height());
        assertEquals(1_572_864, prepared.pixelBytes());
        assertEquals(prepared.width() * prepared.height() * prepared.channels(), prepared.buffer().remaining());

        byte[] upload = bytes(prepared.buffer());
        assertPixel(upload, 1024, channels, 0, 0, 1, 2, 3, 0);
        assertPixel(upload, 1024, channels, sourceWidth - 1, 0, 4, 5, 6, 0);
        assertPixel(upload, 1024, channels, 0, 1, 7, 8, 9, 0);
        assertPixel(upload, 1024, channels, 0, sourceHeight - 1, 10, 11, 12, 0);
        assertPixel(upload, 1024, channels, sourceWidth, 0, 0, 0, 0, 0);
        assertPixel(upload, 1024, channels, 0, sourceHeight, 0, 0, 0, 0);

        Map<String, Object> active = TexturePreparedPixelRuntime.telemetry();
        assertEquals(1L, active.get("hits"));
        assertEquals(1L, active.get("paddedUploads"));
        assertEquals(904_821L, active.get("paddingBytes"));
        assertEquals(0L, active.get("dimensionFallbacks"));
        assertEquals(1_572_864L, active.get("activeDirectBytes"));
        assertEquals(1_572_864L, active.get("bytesBypassed"));

        TexturePreparedPixelRuntime.release(prepared.buffer());
        Map<String, Object> released = TexturePreparedPixelRuntime.telemetry();
        assertEquals(0, released.get("activeBuffers"));
        assertEquals(0L, released.get("activeDirectBytes"));
        assertEquals(1_572_864L, released.get("releasedBytes"));
    }

    @Test
    void padsNpotRgbaWithBottomLeftPlacementAndZeroRightAndTop() throws Exception {
        int sourceWidth = 3;
        int sourceHeight = 5;
        int channels = 4;
        byte[] source = new byte[sourceWidth * sourceHeight * channels];
        for (int index = 0; index < source.length; index++) {
            source[index] = (byte) (index + 1);
        }
        Fixture fixture = fixture(sourceWidth, sourceHeight, channels, source);
        configure(fixture);

        TexturePreparedPixelRuntime.PreparedPixel prepared = TexturePreparedPixelRuntime.prepare(
                TexturePreparedPixelRuntime.load("graphics/test.png"));
        assertNotNull(prepared);
        assertEquals(4, prepared.width());
        assertEquals(8, prepared.height());
        assertEquals(128, prepared.buffer().remaining());

        byte[] expected = new byte[4 * 8 * channels];
        int sourceStride = sourceWidth * channels;
        int uploadStride = 4 * channels;
        for (int row = 0; row < sourceHeight; row++) {
            System.arraycopy(source, row * sourceStride, expected, row * uploadStride, sourceStride);
        }
        assertArrayEquals(expected, bytes(prepared.buffer()));
        assertEquals(1L, TexturePreparedPixelRuntime.telemetry().get("paddedUploads"));
        assertEquals(68L, TexturePreparedPixelRuntime.telemetry().get("paddingBytes"));
    }

    @Test
    void declinesUnexpectedPrePaddedBlobContractBeforeCreatingCarrier() throws Exception {
        Fixture fixture = fixture(3, 5, 4, 4, 8, new byte[4 * 8 * 4]);
        configure(fixture);

        assertNull(TexturePreparedPixelRuntime.load("graphics/test.png"));
        Map<String, Object> telemetry = TexturePreparedPixelRuntime.telemetry();
        assertEquals(1L, telemetry.get("fallbacks"));
        assertEquals(1L, telemetry.get("dimensionFallbacks"));
        assertEquals(0L, telemetry.get("directAttempts"));
        assertEquals(0, telemetry.get("activeBuffers"));
        assertEquals(0L, telemetry.get("activeDirectBytes"));
    }

    @Test
    void exceptionalCallerReleaseReturnsPaddedAccountingToZero() throws Exception {
        byte[] source = new byte[3 * 5 * 4];
        Fixture fixture = fixture(3, 5, 4, source);
        configure(fixture);

        TexturePreparedPixelRuntime.PreparedPixel prepared = TexturePreparedPixelRuntime.prepare(
                TexturePreparedPixelRuntime.load("graphics/test.png"));
        assertNotNull(prepared);
        assertEquals(1, TexturePreparedPixelRuntime.telemetry().get("activeBuffers"));
        TexturePreparedPixelRuntime.releaseCurrentThreadBuffer();
        TexturePreparedPixelRuntime.releaseCurrentThreadBuffer();

        Map<String, Object> telemetry = TexturePreparedPixelRuntime.telemetry();
        assertEquals(0, telemetry.get("activeBuffers"));
        assertEquals(0L, telemetry.get("activeDirectBytes"));
        assertEquals(1L, telemetry.get("releases"));
        assertEquals(128L, telemetry.get("releasedBytes"));
    }

    @Test
    void compatibilitySelectionNeverReportsOrServesPreparedPixels() throws Exception {
        Fixture fixture = fixture();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.COMPATIBILITY);

        assertFalse(TexturePreparedPixelRuntime.ready());
        assertFalse(AdapterTransformationRegistry.hasPlan(TexturePreparedPixelRuntime.PLAN_ID));
        assertNull(TexturePreparedPixelRuntime.load("graphics/test.png"));
        assertEquals(Boolean.FALSE, TexturePreparedPixelRuntime.telemetry().get("ready"));
        assertEquals(0L, TextureCompatibilityRuntime.telemetry().get("attempts"));

        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);
        assertTrue(TexturePreparedPixelRuntime.ready());
        assertTrue(AdapterTransformationRegistry.hasPlan(TexturePreparedPixelRuntime.PLAN_ID));

        TexturePreparedPixelRuntime.beginSession();
        assertFalse(TexturePreparedPixelRuntime.ready());
    }

    private void configure(Fixture fixture) {
        TexturePreparedPixelRuntime.beginSession();
        assertTrue(TextureCompatibilityRuntime.configure(
                fixture.cache(), fixture.manifest(), fixture.index()));
        TexturePreparedPixelRuntime.select(TextureAdapterMode.PREPARED_PIXELS);
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

    private Fixture fixture(int width, int height, int channels, byte[] pixels) throws Exception {
        return fixture(width, height, channels, width, height, pixels);
    }

    private Fixture fixture(
            int originalWidth,
            int originalHeight,
            int channels,
            int uploadWidth,
            int uploadHeight,
            byte[] pixels) throws Exception {
        Path cache = temporaryDirectory.resolve("cache-" + System.nanoTime());
        Path sourceRoot = temporaryDirectory.resolve("game-" + System.nanoTime());
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
                originalWidth,
                originalHeight,
                uploadWidth,
                uploadHeight,
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
                        uploadWidth,
                        uploadHeight,
                        channels,
                        pixels.length)));
        Path manifestPath = cache.resolve("manifests").resolve(profile + ".spfm");
        TextureManifestIO.write(manifestPath, manifest);
        return new Fixture(cache, indexPath, manifestPath, pixels);
    }

    private static void setPixel(
            byte[] pixels,
            int width,
            int channels,
            int x,
            int y,
            int red,
            int green,
            int blue,
            int alpha) {
        int offset = (y * width + x) * channels;
        pixels[offset] = (byte) red;
        pixels[offset + 1] = (byte) green;
        pixels[offset + 2] = (byte) blue;
        if (channels == 4) {
            pixels[offset + 3] = (byte) alpha;
        }
    }

    private static void assertPixel(
            byte[] pixels,
            int width,
            int channels,
            int x,
            int y,
            int red,
            int green,
            int blue,
            int alpha) {
        int offset = (y * width + x) * channels;
        assertEquals(red, Byte.toUnsignedInt(pixels[offset]));
        assertEquals(green, Byte.toUnsignedInt(pixels[offset + 1]));
        assertEquals(blue, Byte.toUnsignedInt(pixels[offset + 2]));
        if (channels == 4) {
            assertEquals(alpha, Byte.toUnsignedInt(pixels[offset + 3]));
        }
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
