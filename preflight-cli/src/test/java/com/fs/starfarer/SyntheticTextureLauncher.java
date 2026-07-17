package com.fs.starfarer;

import com.fs.graphics.TextureLoader;
import java.awt.image.BufferedImage;

/** Child-JVM entry point for cold, warm, corrupt, and fallback texture pilot proofs. */
public final class SyntheticTextureLauncher {
    private SyntheticTextureLauncher() {
    }

    public static void main(String[] args) {
        String logicalPath = args.length == 0 ? "graphics/test.png" : args[0];
        TextureLoader.reset();
        BufferedImage image = new TextureLoader().loadForTest(logicalPath);
        System.out.printf(
                "synthetic-texture:%08x:originalCalls=%d%n",
                image.getRGB(0, 0),
                TextureLoader.originalCalls());
    }
}
