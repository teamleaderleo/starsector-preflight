package com.fs.graphics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.nio.ByteBuffer;

/** Repository-owned fixture with the reviewed decoded-image and prepared-pixel descriptors. */
public final class TextureLoader {
    private static int originalCalls;
    private static int originalConversionCalls;
    private static int originalCleanupCalls;
    private static boolean failAfterConversion;
    private static byte[] originalUpload;

    private BufferedImage Ô00000(String logicalPath) {
        BufferedImage preloaded = L.clazz(logicalPath);
        if (preloaded != null) {
            return preloaded;
        }
        originalCalls++;
        BufferedImage fallback = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        fallback.setRGB(0, 0, 0xffcc00cc);
        return fallback;
    }

    private ByteBuffer o00000(BufferedImage image, Object texture) {
        originalConversionCalls++;
        texture.Ô00000(nextPowerOfTwo(image.getWidth()));
        texture.Ó00000(nextPowerOfTwo(image.getHeight()));
        Raster raster = image.getData();
        int[] pixel = raster.getPixel(0, 0, (int[]) null);
        int red = pixel.length > 0 ? pixel[0] : 0;
        int green = pixel.length > 1 ? pixel[1] : 0;
        int blue = pixel.length > 2 ? pixel[2] : 0;
        texture.derived0 = new Color(red, green, blue, 255);
        texture.derived1 = Color.GREEN;
        texture.derived2 = Color.BLUE;
        byte[] configured = originalUpload;
        if (configured != null) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(configured.length);
            buffer.put(configured).flip();
            return buffer;
        }
        if (image.getWidth() > 1 || image.getHeight() > 1) {
            return convertPowerOfTwoUpload(image);
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(3);
        buffer.put((byte) red).put((byte) green).put((byte) blue).flip();
        return buffer;
    }

    private static ByteBuffer convertPowerOfTwoUpload(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int channels = image.getColorModel().hasAlpha() ? 4 : 3;
        int uploadWidth = nextPowerOfTwo(width);
        int uploadHeight = nextPowerOfTwo(height);
        int uploadStride = uploadWidth * channels;
        byte[] upload = new byte[uploadStride * uploadHeight];
        for (int uploadRow = 0; uploadRow < height; uploadRow++) {
            int imageY = height - 1 - uploadRow;
            int rowOffset = uploadRow * uploadStride;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, imageY);
                int offset = rowOffset + x * channels;
                upload[offset] = (byte) ((argb >>> 16) & 0xff);
                upload[offset + 1] = (byte) ((argb >>> 8) & 0xff);
                upload[offset + 2] = (byte) (argb & 0xff);
                if (channels == 4) {
                    upload[offset + 3] = (byte) ((argb >>> 24) & 0xff);
                }
            }
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(upload.length);
        buffer.put(upload).flip();
        return buffer;
    }

    public static void o00000(ByteBuffer buffer, String logicalPath) {
        originalCleanupCalls++;
    }

    public BufferedImage loadForTest(String logicalPath) {
        return Ô00000(logicalPath);
    }

    public Result loadPixelsForTest(String logicalPath) {
        BufferedImage image = Ô00000(logicalPath);
        Object texture = new Object();
        ByteBuffer buffer = o00000(image, texture);
        if (image.getWidth() > 1 || image.getHeight() > 1) {
            int channels = image.getColorModel().hasAlpha() ? 4 : 3;
            int required = Math.multiplyExact(
                    Math.multiplyExact(nextPowerOfTwo(image.getWidth()), nextPowerOfTwo(image.getHeight())),
                    channels);
            if (buffer.remaining() < required) {
                throw new IllegalArgumentException(
                        "Number of remaining buffer elements is " + buffer.remaining()
                                + ", must be at least " + required);
            }
        }
        if (failAfterConversion) {
            throw new IllegalStateException("synthetic upload failure");
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        o00000(buffer, logicalPath);
        return new Result(
                bytes,
                texture.derived0 == null ? 0 : texture.derived0.getRGB(),
                texture.derived1 == null ? 0 : texture.derived1.getRGB(),
                texture.derived2 == null ? 0 : texture.derived2.getRGB(),
                texture.uploadWidth,
                texture.uploadHeight);
    }

    public static int originalCalls() {
        return originalCalls;
    }

    public static int originalConversionCalls() {
        return originalConversionCalls;
    }

    public static int originalCleanupCalls() {
        return originalCleanupCalls;
    }

    public static void setFailAfterConversion(boolean value) {
        failAfterConversion = value;
    }

    public static void setOriginalUpload(byte[] value) {
        originalUpload = value == null ? null : value.clone();
    }

    public static void reset() {
        originalCalls = 0;
        originalConversionCalls = 0;
        originalCleanupCalls = 0;
        failAfterConversion = false;
        originalUpload = null;
    }

    private static int nextPowerOfTwo(int value) {
        int highest = Integer.highestOneBit(value);
        return highest == value ? value : highest << 1;
    }

    public record Result(
            byte[] pixels,
            int color0,
            int color1,
            int color2,
            int uploadWidth,
            int uploadHeight) {
        public Result {
            pixels = pixels.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }
}
