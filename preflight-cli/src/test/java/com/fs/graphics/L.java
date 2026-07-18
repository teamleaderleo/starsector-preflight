package com.fs.graphics;

import java.awt.image.BufferedImage;

/** Repository-owned stand-in for Starsector's asynchronous image-preloader handoff. */
public final class L {
    private static BufferedImage preloaded;
    private static int lookupCalls;

    private L() {
    }

    public static BufferedImage clazz(String logicalPath) {
        lookupCalls++;
        BufferedImage image = preloaded;
        preloaded = null;
        return image;
    }

    public static void preload(BufferedImage image) {
        preloaded = image;
    }

    public static int lookupCalls() {
        return lookupCalls;
    }

    public static void reset() {
        preloaded = null;
        lookupCalls = 0;
    }
}
