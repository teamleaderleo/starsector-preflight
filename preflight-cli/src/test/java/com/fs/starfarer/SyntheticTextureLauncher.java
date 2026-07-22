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
        boolean uploadFailure = false;
        boolean originalUpperLayout = false;
        for (String argument : args) {
            pixels |= "prepared-pixels".equals(argument);
            preloadedMode |= "preloaded".equals(argument);
            uploadFailure |= "upload-failure".equals(argument);
            originalUpperLayout |= "original-upper-layout".equals(argument);
        }
        TextureLoader.reset();
        TextureLoader.setFailAfterConversion(uploadFailure);
        if (originalUpperLayout) {
            TextureLoader.setOriginalUpload(originalUpperLayout3x3Rgb());
        }
        L.reset();
        if (preloadedMode) {
            BufferedImage preloaded = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            preloaded.setRGB(0, 0, 0xffabcdef);
            L.preload(preloaded);
        }
        TextureLoader loader = new TextureLoader();
        if (pixels) {
            TextureLoader.Result result;
            try {
                result = loader.loadPixelsForTest(logicalPath);
            } catch (IllegalStateException error) {
                if (!uploadFailure) {
                    throw error;
                }
                System.out.printf(
                        "synthetic-upload-failure:%s:%s:decode=%d:convert=%d:cleanup=%d%n",
                        error.getClass().getSimpleName(),
                        error.getMessage(),
                        TextureLoader.originalCalls(),
                        TextureLoader.originalConversionCalls(),
                        TextureLoader.originalCleanupCalls());
                return;
            }
            System.out.printf(
                    "synthetic-pixels:%s:colors=%08x,%08x,%08x:decode=%d:convert=%d:cleanup=%d:preloaderCalls=%d:dimensions=%dx%d%n",
                    HexFormat.of().formatHex(result.pixels()),
                    result.color0(),
                    result.color1(),
                    result.color2(),
                    TextureLoader.originalCalls(),
                    TextureLoader.originalConversionCalls(),
                    TextureLoader.originalCleanupCalls(),
                    L.lookupCalls(),
                    result.uploadWidth(),
                    result.uploadHeight());
            return;
        }
        BufferedImage image = loader.loadForTest(logicalPath);
        System.out.printf(
                "synthetic-texture:%08x:originalCalls=%d:preloaderCalls=%d%n",
                image.getRGB(0, 0),
                TextureLoader.originalCalls(),
                L.lookupCalls());
    }

    private static byte[] originalUpperLayout3x3Rgb() {
        byte[] upload = new byte[4 * 4 * 3];
        int source = 1;
        int uploadStride = 4 * 3;
        for (int row = 1; row < 4; row++) {
            int offset = row * uploadStride;
            for (int index = 0; index < 3 * 3; index++) {
                upload[offset + index] = (byte) source++;
            }
        }
        return upload;
    }
}
