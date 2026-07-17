package com.fs.starfarer;

import com.fs.graphics.TextureLoader;
import java.awt.image.BufferedImage;
import java.util.HexFormat;

/** Child-JVM entry point for texture compatibility and prepared-pixel proofs. */
public final class SyntheticTextureLauncher {
    private SyntheticTextureLauncher() {
    }

    public static void main(String[] args) {
        String logicalPath = args.length == 0 ? "graphics/test.png" : args[0];
        boolean pixels = args.length > 1 && "prepared-pixels".equals(args[1]);
        TextureLoader.reset();
        TextureLoader loader = new TextureLoader();
        if (pixels) {
            TextureLoader.Result result = loader.loadPixelsForTest(logicalPath);
            System.out.printf(
                    "synthetic-pixels:%s:colors=%08x,%08x,%08x:decode=%d:convert=%d:cleanup=%d%n",
                    HexFormat.of().formatHex(result.pixels()),
                    result.color0(),
                    result.color1(),
                    result.color2(),
                    TextureLoader.originalCalls(),
                    TextureLoader.originalConversionCalls(),
                    TextureLoader.originalCleanupCalls());
            return;
        }
        BufferedImage image = loader.loadForTest(logicalPath);
        System.out.printf(
                "synthetic-texture:%08x:originalCalls=%d%n",
                image.getRGB(0, 0),
                TextureLoader.originalCalls());
    }
}
