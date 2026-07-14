package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Literal reference implementation of the current Starsector/Fast Rendering texture preparation
 * loop. Optimization work should prove byte-for-byte equivalence against this implementation.
 */
final class ReferenceTexturePreprocessor {
    private ReferenceTexturePreprocessor() {
    }

    static PreparedTexture prepare(Path source, PreparedTexture.Transformation transformation) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Expected an image file: " + absolute);
        }
        if (transformation != PreparedTexture.Transformation.IDENTITY) {
            throw new UnsupportedOperationException(
                    "The reference converter currently supports IDENTITY textures only");
        }

        BufferedImage image;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(absolute))) {
            image = ImageIO.read(input);
        }
        if (image == null) {
            throw new IOException("ImageIO could not decode: " + absolute);
        }
        return prepare(image, Hashes.sha256(absolute), transformation);
    }

    static PreparedTexture prepare(
            BufferedImage image,
            String sourceSha256,
            PreparedTexture.Transformation transformation) {
        if (transformation != PreparedTexture.Transformation.IDENTITY) {
            throw new UnsupportedOperationException(
                    "The reference converter currently supports IDENTITY textures only");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        Raster raster = image.getData();
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int channels = hasAlpha ? 4 : 3;
        int payloadLength = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) width, height), channels));
        byte[] pixels = new byte[payloadLength];

        float redSum = 0.0F;
        float greenSum = 0.0F;
        float blueSum = 0.0F;
        float countedPixels = 0.0F;
        float[] redHistogram = new float[256];
        float[] greenHistogram = new float[256];
        float[] blueHistogram = new float[256];

        if (hasAlpha) {
            int[] sample = new int[4];
            for (int outputY = 0; outputY < height; outputY++) {
                int sourceY = height - outputY - 1;
                for (int x = 0; x < width; x++) {
                    raster.getPixel(x, sourceY, sample);
                    if (sample[3] != 0) {
                        int offset = (outputY * width + x) * 4;
                        pixels[offset] = (byte) sample[0];
                        pixels[offset + 1] = (byte) sample[1];
                        pixels[offset + 2] = (byte) sample[2];
                        pixels[offset + 3] = (byte) sample[3];
                        redSum += (float) sample[0];
                        greenSum += (float) sample[1];
                        blueSum += (float) sample[2];
                        ++redHistogram[sample[0]];
                        ++greenHistogram[sample[1]];
                        ++blueHistogram[sample[2]];
                        ++countedPixels;
                    }
                }
            }
        } else {
            int[] sample = new int[3];
            for (int outputY = 0; outputY < height; outputY++) {
                int sourceY = height - outputY - 1;
                for (int x = 0; x < width; x++) {
                    raster.getPixel(x, sourceY, sample);
                    int offset = (outputY * width + x) * 3;
                    pixels[offset] = (byte) sample[0];
                    pixels[offset + 1] = (byte) sample[1];
                    pixels[offset + 2] = (byte) sample[2];
                    redSum += (float) sample[0];
                    greenSum += (float) sample[1];
                    blueSum += (float) sample[2];
                    if (sample[0] < 256 && sample[1] < 256 && sample[2] < 256) {
                        ++redHistogram[sample[0]];
                        ++greenHistogram[sample[1]];
                        ++blueHistogram[sample[2]];
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
}
