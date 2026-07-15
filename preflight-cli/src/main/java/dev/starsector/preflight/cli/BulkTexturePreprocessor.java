package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Row-bulk implementation of the literal texture preparation loop.
 *
 * <p>The converter reads one complete raster row at a time, then preserves the reference loop's
 * pixel order, band indexing, float accumulation order, transparent-texel behavior, histograms,
 * and derived colors. Unsupported raster layouts fall back to {@link ReferenceTexturePreprocessor}.</p>
 */
final class BulkTexturePreprocessor {
    private static final long MAX_SNAPSHOT_BYTES = Integer.MAX_VALUE - 8L;

    private BulkTexturePreprocessor() {
    }

    static PreparedTexture prepare(Path source, PreparedTexture.Transformation transformation) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Expected an image file: " + absolute);
        }
        validateTransformation(transformation);

        long sourceBytes = Files.size(absolute);
        byte[] encoded = readSnapshot(absolute, sourceBytes, MAX_SNAPSHOT_BYTES);
        String sourceSha256 = Hashes.sha256(encoded);
        return decodeVerifiedSnapshot(encoded, sourceSha256, transformation);
    }

    static PreparedTexture prepareSnapshot(
            byte[] encoded,
            String expectedSha256,
            PreparedTexture.Transformation transformation) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        validateTransformation(transformation);
        Hashes.decodeSha256(expectedSha256);

        String snapshotSha256 = Hashes.sha256(encoded);
        if (!snapshotSha256.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("Source snapshot SHA-256 mismatch: expected "
                    + expectedSha256 + " but was " + snapshotSha256);
        }
        return decodeVerifiedSnapshot(encoded, snapshotSha256, transformation);
    }

    static byte[] readSnapshot(Path source, long expectedBytes, long maximumBytes) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Expected an image file: " + absolute);
        }
        if (expectedBytes < 0) {
            throw new IllegalArgumentException("Expected source size must be non-negative");
        }
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("Maximum snapshot size must be non-negative");
        }
        if (expectedBytes > maximumBytes) {
            throw new IOException("Encoded source is " + expectedBytes
                    + " bytes, exceeding the snapshot limit of " + maximumBytes + " bytes: " + absolute);
        }
        if (expectedBytes > MAX_SNAPSHOT_BYTES) {
            throw new IOException("Encoded source is too large for one byte-array snapshot: " + absolute);
        }

        byte[] encoded = new byte[Math.toIntExact(expectedBytes)];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(absolute))) {
            int offset = 0;
            while (offset < encoded.length) {
                int read = input.read(encoded, offset, encoded.length - offset);
                if (read < 0) {
                    throw new IOException("Encoded source shrank while snapshotting: " + absolute);
                }
                if (read == 0) {
                    int value = input.read();
                    if (value < 0) {
                        throw new IOException("Encoded source shrank while snapshotting: " + absolute);
                    }
                    encoded[offset++] = (byte) value;
                } else {
                    offset += read;
                }
            }
            if (input.read() >= 0) {
                throw new IOException("Encoded source grew while snapshotting: " + absolute);
            }
        }
        return encoded;
    }

    private static PreparedTexture decodeVerifiedSnapshot(
            byte[] encoded,
            String sourceSha256,
            PreparedTexture.Transformation transformation) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
        if (image == null) {
            throw new IOException("ImageIO could not decode the encoded source snapshot");
        }
        return prepareDetailed(image, sourceSha256, transformation).texture();
    }

    private static void validateTransformation(PreparedTexture.Transformation transformation) {
        Objects.requireNonNull(transformation, "transformation");
        if (transformation != PreparedTexture.Transformation.IDENTITY) {
            throw new UnsupportedOperationException(
                    "The bulk converter currently supports IDENTITY textures only");
        }
    }

    static PreparedTexture prepare(
            BufferedImage image,
            String sourceSha256,
            PreparedTexture.Transformation transformation) {
        return prepareDetailed(image, sourceSha256, transformation).texture();
    }

    static Conversion prepareDetailed(
            BufferedImage image,
            String sourceSha256,
            PreparedTexture.Transformation transformation) {
        validateTransformation(transformation);

        Raster raster = image.getData();
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int channels = hasAlpha ? 4 : 3;
        int bands = raster.getNumBands();
        long maximumX = (long) raster.getMinX() + raster.getWidth();
        long maximumY = (long) raster.getMinY() + raster.getHeight();
        if (raster.getMinX() > 0
                || raster.getMinY() > 0
                || maximumX < image.getWidth()
                || maximumY < image.getHeight()
                || bands < 1
                || bands > channels) {
            return reference(image, sourceSha256, transformation);
        }

        try {
            return new Conversion(
                    prepareRows(image, raster, sourceSha256, transformation, channels, bands),
                    Backend.BULK_ROWS);
        } catch (RuntimeException unsupportedLayout) {
            // The literal implementation remains the compatibility authority for unusual rasters.
            return reference(image, sourceSha256, transformation);
        }
    }

    private static Conversion reference(
            BufferedImage image,
            String sourceSha256,
            PreparedTexture.Transformation transformation) {
        return new Conversion(
                ReferenceTexturePreprocessor.prepare(image, sourceSha256, transformation),
                Backend.REFERENCE_FALLBACK);
    }

    private static PreparedTexture prepareRows(
            BufferedImage image,
            Raster raster,
            String sourceSha256,
            PreparedTexture.Transformation transformation,
            int channels,
            int bands) {
        int width = image.getWidth();
        int height = image.getHeight();
        int payloadLength = Math.toIntExact(
                Math.multiplyExact(Math.multiplyExact((long) width, height), channels));
        int rowSamples = Math.toIntExact(Math.multiplyExact((long) width, bands));
        byte[] pixels = new byte[payloadLength];
        int[] row = new int[rowSamples];

        float redSum = 0.0F;
        float greenSum = 0.0F;
        float blueSum = 0.0F;
        float countedPixels = 0.0F;
        float[] redHistogram = new float[256];
        float[] greenHistogram = new float[256];
        float[] blueHistogram = new float[256];

        if (channels == 4) {
            for (int outputY = 0; outputY < height; outputY++) {
                int sourceY = height - outputY - 1;
                row = raster.getPixels(0, sourceY, width, 1, row);
                for (int x = 0; x < width; x++) {
                    int sourceOffset = x * bands;
                    int red = row[sourceOffset];
                    int green = bands > 1 ? row[sourceOffset + 1] : 0;
                    int blue = bands > 2 ? row[sourceOffset + 2] : 0;
                    int alpha = bands > 3 ? row[sourceOffset + 3] : 0;
                    if (alpha != 0) {
                        int outputOffset = (outputY * width + x) * 4;
                        pixels[outputOffset] = (byte) red;
                        pixels[outputOffset + 1] = (byte) green;
                        pixels[outputOffset + 2] = (byte) blue;
                        pixels[outputOffset + 3] = (byte) alpha;
                        redSum += (float) red;
                        greenSum += (float) green;
                        blueSum += (float) blue;
                        ++redHistogram[red];
                        ++greenHistogram[green];
                        ++blueHistogram[blue];
                        ++countedPixels;
                    }
                }
            }
        } else {
            for (int outputY = 0; outputY < height; outputY++) {
                int sourceY = height - outputY - 1;
                row = raster.getPixels(0, sourceY, width, 1, row);
                for (int x = 0; x < width; x++) {
                    int sourceOffset = x * bands;
                    int red = row[sourceOffset];
                    int green = bands > 1 ? row[sourceOffset + 1] : 0;
                    int blue = bands > 2 ? row[sourceOffset + 2] : 0;
                    int outputOffset = (outputY * width + x) * 3;
                    pixels[outputOffset] = (byte) red;
                    pixels[outputOffset + 1] = (byte) green;
                    pixels[outputOffset + 2] = (byte) blue;
                    redSum += (float) red;
                    greenSum += (float) green;
                    blueSum += (float) blue;
                    if (red < 256 && green < 256 && blue < 256) {
                        ++redHistogram[red];
                        ++greenHistogram[green];
                        ++blueHistogram[blue];
                    }
                    ++countedPixels;
                }
            }
        }

        int color0 = packed(Color.WHITE);
        int color1 = packed(Color.WHITE);
        int color2 = packed(Color.WHITE);
        if (countedPixels > 0.0F) {
            int red = clamp((int) (redSum / countedPixels));
            int green = clamp((int) (greenSum / countedPixels));
            int blue = clamp((int) (blueSum / countedPixels));
            color0 = PreparedTexture.rgba(red, green, blue, 255);

            float topFraction = 0.5F;
            red = clamp((int) weightedDescending(redHistogram, countedPixels * topFraction));
            green = clamp((int) weightedDescending(greenHistogram, countedPixels * topFraction));
            blue = clamp((int) weightedDescending(blueHistogram, countedPixels * topFraction));
            color1 = PreparedTexture.rgba(red, green, blue, 255);

            red = clamp((int) median(redHistogram, countedPixels));
            green = clamp((int) median(greenHistogram, countedPixels));
            blue = clamp((int) weightedDescending(blueHistogram, countedPixels));
            color2 = PreparedTexture.rgba(red, green, blue, 255);
        }

        return new PreparedTexture(
                sourceSha256,
                transformation,
                width,
                height,
                width,
                height,
                channels,
                color0,
                color1,
                color2,
                pixels);
    }

    private static int packed(Color color) {
        return PreparedTexture.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float median(float[] histogram, float count) {
        float accumulated = 0.0F;
        float target = count * 0.5F;
        for (int value = 0; value <= 255; value++) {
            accumulated += histogram[value];
            if (accumulated >= target) {
                return (float) value;
            }
        }
        return 0.0F;
    }

    private static float weightedDescending(float[] histogram, float target) {
        float accumulated = 0.0F;
        float weighted = 0.0F;
        for (int value = 255; value >= 0; value--) {
            float available = histogram[value];
            float selected = available;
            if (accumulated + available >= target) {
                selected = target - accumulated;
            }
            accumulated += selected;
            weighted += (float) value * selected;
            if (accumulated >= target) {
                break;
            }
        }
        return accumulated > 0.0F ? weighted / accumulated : 0.0F;
    }

    enum Backend {
        BULK_ROWS,
        REFERENCE_FALLBACK
    }

    record Conversion(PreparedTexture texture, Backend backend) {
    }
}
