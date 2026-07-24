package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ImageHeaderReader.ImageDimensions;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageHeaderReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTruecolorPngDimensionsAndChannels() throws Exception {
        Path png = temporaryDirectory.resolve("rgb.png");
        ImageIO.write(new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB), "png", png.toFile());

        Optional<ImageDimensions> dimensions = ImageHeaderReader.read(png);
        assertTrue(dimensions.isPresent(), "truecolor PNG should be measurable");
        assertEquals(10, dimensions.get().width());
        assertEquals(20, dimensions.get().height());
        assertEquals(3, dimensions.get().channels(), "TYPE_INT_RGB is a 3-channel PNG");
        assertEquals(10L * 20L * 3L, dimensions.get().decodedBytes());
    }

    @Test
    void readsTruecolorAlphaPngAsFourChannels() throws Exception {
        Path png = temporaryDirectory.resolve("rgba.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());

        ImageDimensions dimensions = ImageHeaderReader.read(png).orElseThrow();
        assertEquals(8, dimensions.width());
        assertEquals(8, dimensions.height());
        assertEquals(4, dimensions.channels(), "TYPE_INT_ARGB is a 4-channel PNG");
    }

    @Test
    void readsJpegDimensions() throws Exception {
        Path jpeg = temporaryDirectory.resolve("photo.jpg");
        assertTrue(ImageIO.write(new BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB), "jpg", jpeg.toFile()),
                "environment can write JPEG");

        ImageDimensions dimensions = ImageHeaderReader.read(jpeg).orElseThrow();
        assertEquals(16, dimensions.width());
        assertEquals(9, dimensions.height());
        assertEquals(3, dimensions.channels(), "baseline JPEG is 3-component YCbCr");
    }

    @Test
    void treatsIndexedPngAsFourChannels() {
        // Craft a minimal PNG header (signature + IHDR) with color type 3 (indexed/palette).
        byte[] header = new byte[26];
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, header, 0, signature.length);
        writeInt32BE(header, 16, 64);  // width
        writeInt32BE(header, 20, 32);  // height
        header[24] = 8;                // bit depth
        header[25] = 3;                // color type: indexed

        ImageDimensions dimensions = ImageHeaderReader.readBytes(header).orElseThrow();
        assertEquals(64, dimensions.width());
        assertEquals(32, dimensions.height());
        assertEquals(4, dimensions.channels(), "indexed PNG conservatively expands to RGBA");
    }

    @Test
    void returnsEmptyForNonImageContent() throws Exception {
        Path text = temporaryDirectory.resolve("notes.txt");
        Files.writeString(text, "this is not an image");
        assertTrue(ImageHeaderReader.read(text).isEmpty());

        assertTrue(ImageHeaderReader.readBytes(new byte[] {1, 2, 3}).isEmpty());
        assertFalse(ImageHeaderReader.readBytes(new byte[0]).isPresent());
    }

    private static void writeInt32BE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
