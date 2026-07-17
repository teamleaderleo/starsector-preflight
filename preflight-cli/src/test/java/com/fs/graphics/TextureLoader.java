package com.fs.graphics;

import java.awt.image.BufferedImage;

/** Repository-owned fixture with the reviewed decoded-image descriptor. */
public final class TextureLoader {
    private static int originalCalls;

    private BufferedImage Ô00000(String logicalPath) {
        originalCalls++;
        BufferedImage fallback = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        fallback.setRGB(0, 0, 0xffcc00cc);
        return fallback;
    }

    public BufferedImage loadForTest(String logicalPath) {
        return Ô00000(logicalPath);
    }

    public static int originalCalls() {
        return originalCalls;
    }

    public static void reset() {
        originalCalls = 0;
    }
}
