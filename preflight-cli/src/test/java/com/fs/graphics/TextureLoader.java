package com.fs.graphics;

import java.awt.image.BufferedImage;

/** Synthetic exact-method target used only by packaged child-JVM adapter tests. */
public final class TextureLoader {
    public static final int ORIGINAL_PIXEL = 0xffaabbcc;

    public BufferedImage load(String path) {
        return Ô00000(path);
    }

    private BufferedImage Ô00000(String path) {
        if (path.equals("explode")) {
            throw new IllegalStateException("synthetic original failure");
        }
        BufferedImage result = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, ORIGINAL_PIXEL);
        return result;
    }
}
