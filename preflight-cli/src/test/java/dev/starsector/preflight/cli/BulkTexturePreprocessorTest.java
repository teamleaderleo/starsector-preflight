package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulkTexturePreprocessorTest {
    private static final String HASH = "22".repeat(32);
    private static final int[] STANDARD_TYPES = {
            BufferedImage.TYPE_INT_RGB,
            BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_INT_ARGB_PRE,
            BufferedImage.TYPE_INT_BGR,
            BufferedImage.TYPE_3BYTE_BGR,
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_4BYTE_ABGR_PRE,
            BufferedImage.TYPE_USHORT_565_RGB,
            BufferedImage.TYPE_USHORT_555_RGB,
            BufferedImage.TYPE_BYTE_GRAY,
            BufferedImage.TYPE_USHORT_GRAY,
            BufferedImage.TYPE_BYTE_BINARY,
            BufferedImage.TYPE_BYTE_INDEXED
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void randomizedStandardImageTypesMatchReferenceExactly() {
        for (int type : STANDARD_TYPES) {
            for (int iteration = 0; iteration < 12; iteration++) {
                long seed = 0x5eed_0000L + type * 1_000L + iteration;
                Random random = new Random(seed);
                int width = 1 + random.nextInt(19);
                int height = 1 + random.nextInt(17);
                BufferedImage image = new BufferedImage(width, height, type);
                fillArgb(image, random);
                assertEquivalent(image, "type=" + type + " seed=" + seed + " size=" + width + "x" + height);
            }
        }
    }

    @Test
    void randomSubimagesMatchReferenceExactly() {
        Random random = new Random(0x51b1_4a9L);
        for (int type : new int[] {
                BufferedImage.TYPE_INT_RGB,
                BufferedImage.TYPE_INT_ARGB,
                BufferedImage.TYPE_3BYTE_BGR,
                BufferedImage.TYPE_4BYTE_ABGR
        }) {
            BufferedImage parent = new BufferedImage(23, 19, type);
            fillArgb(parent, random);
            BufferedImage subimage = parent.getSubimage(3, 4, 13, 11);
            assertEquivalent(subimage, "subimage type=" + type);
        }
    }

    @Test
    void preservesLiteralGrayAlphaBandBehavior() {
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ComponentColorModel colors = new ComponentColorModel(
                gray,
                new int[] {8, 8},
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster = Raster.createInterleavedRaster(
                DataBuffer.TYPE_BYTE, 5, 3, 2, null);
        BufferedImage image = new BufferedImage(colors, raster, false, null);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.setPixel(x, y, new int[] {(x * 31 + y * 7) & 255, 255});
            }
        }

        assertEquivalent(image, "gray-alpha");
    }

    @Test
    void preservesLiteralIndexedAlphaBandBehavior() {
        byte[] red = {0, (byte) 255, 40};
        byte[] green = {0, 20, (byte) 240};
        byte[] blue = {0, 30, 80};
        byte[] alpha = {0, (byte) 255, (byte) 128};
        IndexColorModel colors = new IndexColorModel(8, 3, red, green, blue, alpha);
        BufferedImage image = new BufferedImage(7, 5, BufferedImage.TYPE_BYTE_INDEXED, colors);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.getRaster().setSample(x, y, 0, (x + y) % 3);
            }
        }

        assertEquivalent(image, "indexed-alpha");
    }

    @Test
    void fileBackedPngAndJpegMatchReferenceAndSourceHashes() throws Exception {
        BufferedImage argb = new BufferedImage(31, 23, BufferedImage.TYPE_INT_ARGB);
        fillArgb(argb, new Random(71));
        Path png = temporaryDirectory.resolve("fixture image.png");
        ImageIO.write(argb, "png", png.toFile());
        assertEquals(
                ReferenceTexturePreprocessor.prepare(png, PreparedTexture.Transformation.IDENTITY),
                BulkTexturePreprocessor.prepare(png, PreparedTexture.Transformation.IDENTITY));

        BufferedImage rgb = new BufferedImage(29, 17, BufferedImage.TYPE_INT_RGB);
        fillArgb(rgb, new Random(93));
        Path jpeg = temporaryDirectory.resolve("fixture image.jpg");
        ImageIO.write(rgb, "jpg", jpeg.toFile());
        assertEquals(
                ReferenceTexturePreprocessor.prepare(jpeg, PreparedTexture.Transformation.IDENTITY),
                BulkTexturePreprocessor.prepare(jpeg, PreparedTexture.Transformation.IDENTITY));
    }

    @Test
    void snapshotConversionMatchesPathAndLiteralReference() throws Exception {
        BufferedImage image = new BufferedImage(17, 11, BufferedImage.TYPE_INT_ARGB);
        fillArgb(image, new Random(141));
        Path png = temporaryDirectory.resolve("snapshot.png");
        assertTrue(ImageIO.write(image, "png", png.toFile()));
        byte[] encoded = Files.readAllBytes(png);
        String sourceSha256 = Hashes.sha256(encoded);

        PreparedTexture snapshot = BulkTexturePreprocessor.prepareSnapshot(
                encoded,
                sourceSha256,
                PreparedTexture.Transformation.IDENTITY);

        assertEquals(sourceSha256, snapshot.sourceSha256());
        assertEquals(
                BulkTexturePreprocessor.prepare(png, PreparedTexture.Transformation.IDENTITY),
                snapshot);
        assertEquals(
                ReferenceTexturePreprocessor.prepare(png, PreparedTexture.Transformation.IDENTITY),
                snapshot);
    }

    @Test
    void rejectsSnapshotWhoseBytesDifferFromExpectedHash() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        fillArgb(image, new Random(151));
        Path png = temporaryDirectory.resolve("changed.png");
        assertTrue(ImageIO.write(image, "png", png.toFile()));
        byte[] encoded = Files.readAllBytes(png);

        IOException error = assertThrows(
                IOException.class,
                () -> BulkTexturePreprocessor.prepareSnapshot(
                        encoded,
                        "00".repeat(32),
                        PreparedTexture.Transformation.IDENTITY));

        assertTrue(error.getMessage().contains("Source snapshot SHA-256 mismatch"));
        assertTrue(error.getMessage().contains(Hashes.sha256(encoded)));
    }

    @Test
    void rejectsUnimplementedTransformsLikeReference() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertThrows(
                UnsupportedOperationException.class,
                () -> BulkTexturePreprocessor.prepare(
                        image,
                        HASH,
                        PreparedTexture.Transformation.ALPHA_ADDER));
    }

    private static void assertEquivalent(BufferedImage image, String context) {
        PreparedTexture reference = ReferenceTexturePreprocessor.prepare(
                image, HASH, PreparedTexture.Transformation.IDENTITY);
        BulkTexturePreprocessor.Conversion bulk = BulkTexturePreprocessor.prepareDetailed(
                image, HASH, PreparedTexture.Transformation.IDENTITY);
        assertEquals(BulkTexturePreprocessor.Backend.BULK_ROWS, bulk.backend(), context);
        assertEquals(reference, bulk.texture(), context);
    }

    private static void fillArgb(BufferedImage image, Random random) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = random.nextInt(256);
                int red = random.nextInt(256);
                int green = random.nextInt(256);
                int blue = random.nextInt(256);
                int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                image.setRGB(x, y, argb);
            }
        }
    }
}
