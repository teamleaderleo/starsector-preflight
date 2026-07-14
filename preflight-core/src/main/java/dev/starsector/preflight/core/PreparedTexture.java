package dev.starsector.preflight.core;

import java.util.Arrays;
import java.util.Objects;

/** Immutable output of the Starsector-compatible texture preparation pipeline. */
public final class PreparedTexture {
    public static final int FORMAT_VERSION = 1;

    private final String sourceSha256;
    private final Transformation transformation;
    private final int originalWidth;
    private final int originalHeight;
    private final int uploadWidth;
    private final int uploadHeight;
    private final int channels;
    private final int color0Rgba;
    private final int color1Rgba;
    private final int color2Rgba;
    private final byte[] pixels;

    public PreparedTexture(
            String sourceSha256,
            Transformation transformation,
            int originalWidth,
            int originalHeight,
            int uploadWidth,
            int uploadHeight,
            int channels,
            int color0Rgba,
            int color1Rgba,
            int color2Rgba,
            byte[] pixels) {
        Hashes.decodeSha256(sourceSha256);
        this.sourceSha256 = sourceSha256.toLowerCase(java.util.Locale.ROOT);
        this.transformation = Objects.requireNonNull(transformation, "transformation");
        this.originalWidth = positive(originalWidth, "originalWidth");
        this.originalHeight = positive(originalHeight, "originalHeight");
        this.uploadWidth = positive(uploadWidth, "uploadWidth");
        this.uploadHeight = positive(uploadHeight, "uploadHeight");
        if (channels != 3 && channels != 4) {
            throw new IllegalArgumentException("channels must be 3 or 4");
        }
        this.channels = channels;
        long expected = Math.multiplyExact(Math.multiplyExact((long) uploadWidth, uploadHeight), channels);
        if (expected > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Prepared texture exceeds the Java byte-array limit");
        }
        if (pixels == null || pixels.length != (int) expected) {
            throw new IllegalArgumentException(
                    "Pixel payload length is " + (pixels == null ? "null" : pixels.length) + "; expected " + expected);
        }
        this.color0Rgba = color0Rgba;
        this.color1Rgba = color1Rgba;
        this.color2Rgba = color2Rgba;
        this.pixels = pixels.clone();
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public Transformation transformation() {
        return transformation;
    }

    public int originalWidth() {
        return originalWidth;
    }

    public int originalHeight() {
        return originalHeight;
    }

    public int uploadWidth() {
        return uploadWidth;
    }

    public int uploadHeight() {
        return uploadHeight;
    }

    public int channels() {
        return channels;
    }

    public boolean hasAlpha() {
        return channels == 4;
    }

    public int color0Rgba() {
        return color0Rgba;
    }

    public int color1Rgba() {
        return color1Rgba;
    }

    public int color2Rgba() {
        return color2Rgba;
    }

    public int pixelBytes() {
        return pixels.length;
    }

    public byte[] pixels() {
        return pixels.clone();
    }

    public void copyPixelsTo(byte[] destination, int offset) {
        Objects.requireNonNull(destination, "destination");
        if (offset < 0 || offset > destination.length - pixels.length) {
            throw new IndexOutOfBoundsException("Pixel destination is too small");
        }
        System.arraycopy(pixels, 0, destination, offset, pixels.length);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (component(red) << 24)
                | (component(green) << 16)
                | (component(blue) << 8)
                | component(alpha);
    }

    public static int red(int rgba) {
        return (rgba >>> 24) & 0xff;
    }

    public static int green(int rgba) {
        return (rgba >>> 16) & 0xff;
    }

    public static int blue(int rgba) {
        return (rgba >>> 8) & 0xff;
    }

    public static int alpha(int rgba) {
        return rgba & 0xff;
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int component(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Color component must be between 0 and 255: " + value);
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PreparedTexture other)) {
            return false;
        }
        return originalWidth == other.originalWidth
                && originalHeight == other.originalHeight
                && uploadWidth == other.uploadWidth
                && uploadHeight == other.uploadHeight
                && channels == other.channels
                && color0Rgba == other.color0Rgba
                && color1Rgba == other.color1Rgba
                && color2Rgba == other.color2Rgba
                && sourceSha256.equals(other.sourceSha256)
                && transformation == other.transformation
                && Arrays.equals(pixels, other.pixels);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                sourceSha256,
                transformation,
                originalWidth,
                originalHeight,
                uploadWidth,
                uploadHeight,
                channels,
                color0Rgba,
                color1Rgba,
                color2Rgba);
        return 31 * result + Arrays.hashCode(pixels);
    }

    public enum Transformation {
        IDENTITY(0),
        ALPHA_ADDER(1);

        private final int id;

        Transformation(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Transformation fromId(int id) {
            for (Transformation value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown texture transformation id: " + id);
        }
    }
}
