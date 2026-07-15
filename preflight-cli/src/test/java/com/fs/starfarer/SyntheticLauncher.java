package com.fs.starfarer;

import com.fs.graphics.TextureLoader;
import java.awt.image.BufferedImage;

/** Tiny child-JVM target used to verify packaged javaagent behavior. */
public final class SyntheticLauncher {
    private SyntheticLauncher() {
    }

    public static void main(String[] args) {
        System.out.println("synthetic-starsector-launcher");
        if (args.length == 0 || !args[0].equals("prepared-image")) {
            return;
        }
        String path = args.length > 1 ? args[1] : "graphics/test.png";
        BufferedImage image = new TextureLoader().load(path);
        System.out.printf("synthetic-texture-pixel=%08x%n", image.getRGB(0, 0));
    }
}
