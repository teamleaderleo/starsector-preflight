package com.fs.starfarer;

import com.fs.graphics.L;
import com.fs.graphics.TextureLoader;
import java.awt.image.BufferedImage;
import java.util.HexFormat;

/** Child-JVM entry point for texture compatibility and prepared-pixel proofs. */
public final class SyntheticTextureLauncher {
    private SyntheticTextureLauncher() {
    }

    public static void main(String[] args) {
        String logicalPath = args.length == 0 ? "graphics/test.png" : args[0];
        boolean pixels = false;
        boolean preloadedMode = false;
        for (String argument : args) {
            pixels |= "prepared-pixels".equals(argument);
            preloadedMode |= "preloaded".equals(argument);
        }
        TextureLoader.reset();
        L.reset();
        if (preloadedMode) {
            BufferedImage preloaded = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            preloaded.setRGB(0, 0, 0xffabcdef);
            L.preload(preloaded);
        }
        TextureLoader loader = new TextureLoader();
        if (pixels) {
            TextureLoader.Result result = loader.loadPixelsForTest(logicalPath);
            System.out.printf(
                    "synthetic-pixels:%s:colors=%08x,%08x,%08x:decode=%d:convert=%d:cleanup=%d:preloaderCalls=%d%n",
                    HexFormat.of().formatHex(result.pixels()),
                    result.color0(),
                    result.color1(),
                    result.color2(),
                    TextureLoader.originalCalls(),
                    TextureLoader.originalConversionCalls(),
                    TextureLoader.originalCleanupCalls(),
                    L.lookupCalls());
            return;
        }
        BufferedImage image = loader.loadForTest(logicalPath);
        System.out.printf(
                "synthetic-texture:%08x:originalCalls=%d:preloaderCalls=%d%n",
                image.getRGB(0, 0),
                TextureLoader.originalCalls(),
                L.lookupCalls());
    }
}
