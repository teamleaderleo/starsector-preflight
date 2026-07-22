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
        Raster raster = image.getData();
        int[] pixel = raster.getPixel(0, 0, (int[]) null);
        int red = pixel.length > 0 ? pixel[0] : 0;
        int green = pixel.length > 1 ? pixel[1] : 0;
        int blue = pixel.length > 2 ? pixel[2] : 0;
        texture.derived0 = new Color(red, green, blue, 255);
        texture.derived1 = Color.GREEN;
        texture.derived2 = Color.BLUE;
        ByteBuffer buffer = ByteBuffer.allocateDirect(3);
        buffer.put((byte) red).put((byte) green).put((byte) blue).flip();
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
                texture.derived2 == null ? 0 : texture.derived2.getRGB());
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

    public static void reset() {
        originalCalls = 0;
        originalConversionCalls = 0;
        originalCleanupCalls = 0;
        failAfterConversion = false;
    }

    public record Result(byte[] pixels, int color0, int color1, int color2) {
        public Result {
            pixels = pixels.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }
}
