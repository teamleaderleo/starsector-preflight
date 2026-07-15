package com.fs.starfarer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Synthetic Starsector-package frame used only by JFR attribution tests. */
public final class SyntheticImageReader {
    private SyntheticImageReader() {
    }

    public static byte[] loadTextureImage(Path path) throws IOException {
        return decodeResource(path);
    }

    private static byte[] decodeResource(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
