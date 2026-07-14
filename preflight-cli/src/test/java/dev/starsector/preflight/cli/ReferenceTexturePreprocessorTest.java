package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import org.junit.jupiter.api.Test;

class ReferenceTexturePreprocessorTest {
    private static final String HASH = "11".repeat(32);

    @Test
    void convertsRgbPixelsBottomUpAndMatchesDerivedColors() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(0, 1, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);

        assertEquals(2, texture.originalWidth());
        assertEquals(2, texture.originalHeight());
        assertEquals(3, texture.channels());
        assertFalse(texture.hasAlpha());
        assertArrayEquals(new byte[] {
                0, 0, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255,
                (byte) 255, 0, 0,
                0, (byte) 255, 0
        }, texture.pixels());
        assertEquals(PreparedTexture.rgba(127, 127, 127, 255), texture.color0Rgba());
        assertEquals(PreparedTexture.rgba(255, 255, 255, 255), texture.color1Rgba());
        assertEquals(PreparedTexture.rgba(0, 0, 127, 255), texture.color2Rgba());
    }

    @Test
    void zeroesTransparentTexelsAndExcludesThemFromStatistics() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(200, 100, 50, 0).getRGB());
        image.setRGB(1, 0, new Color(255, 0, 0, 255).getRGB());
        image.setRGB(0, 1, new Color(0, 0, 255, 128).getRGB());
        image.setRGB(1, 1, new Color(0, 255, 0, 255).getRGB());

        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);

        assertTrue(texture.hasAlpha());
        assertArrayEquals(new byte[] {
                0, 0, (byte) 255, (byte) 128,
                0, (byte) 255, 0, (byte) 255,
                0, 0, 0, 0,
                (byte) 255, 0, 0, (byte) 255
        }, texture.pixels());
        assertEquals(PreparedTexture.rgba(85, 85, 85, 255), texture.color0Rgba());
        assertEquals(PreparedTexture.rgba(170, 170, 170, 255), texture.color1Rgba());
        assertEquals(PreparedTexture.rgba(0, 0, 85, 255), texture.color2Rgba());
    }

    @Test
    void fullyTransparentImagesKeepWhiteDefaultColors() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(12, 34, 56, 0).getRGB());

        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);

        assertArrayEquals(new byte[] {0, 0, 0, 0}, texture.pixels());
        int white = PreparedTexture.rgba(255, 255, 255, 255);
        assertEquals(white, texture.color0Rgba());
        assertEquals(white, texture.color1Rgba());
        assertEquals(white, texture.color2Rgba());
    }

    @Test
    void preservesLiteralRasterBehaviorForGrayscaleImages() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        raster.setSample(0, 0, 0, 10);
        raster.setSample(1, 0, 0, 200);

        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);

        assertEquals(3, texture.channels());
        assertArrayEquals(new byte[] {10, 0, 0, (byte) 200, 0, 0}, texture.pixels());
        assertEquals(PreparedTexture.rgba(105, 0, 0, 255), texture.color0Rgba());
    }

    @Test
    void preservesLiteralRasterBehaviorForIndexedImages() {
        byte[] reds = {0, (byte) 255};
        byte[] greens = {0, 0};
        byte[] blues = {0, 0};
        IndexColorModel colors = new IndexColorModel(8, 2, reds, greens, blues);
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_INDEXED, colors);
        image.getRaster().setSample(0, 0, 0, 0);
        image.getRaster().setSample(1, 0, 0, 1);

        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);

        assertArrayEquals(new byte[] {0, 0, 0, 1, 0, 0}, texture.pixels());
        assertEquals(PreparedTexture.rgba(0, 0, 0, 255), texture.color0Rgba());
    }

    @Test
    void supportsOddDimensionsAndRejectsUnimplementedTransforms() {
        BufferedImage image = new BufferedImage(3, 5, BufferedImage.TYPE_INT_RGB);
        PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                image,
                HASH,
                PreparedTexture.Transformation.IDENTITY);
        assertEquals(45, texture.pixelBytes());

        assertThrows(
                UnsupportedOperationException.class,
                () -> ReferenceTexturePreprocessor.prepare(
                        image,
                        HASH,
                        PreparedTexture.Transformation.ALPHA_ADDER));
    }
}
