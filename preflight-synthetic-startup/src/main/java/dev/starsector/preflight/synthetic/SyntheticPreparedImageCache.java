package dev.starsector.preflight.synthetic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Synthetic-only prepared RGBA cache used to prove cross-process startup work reduction. */
final class SyntheticPreparedImageCache {
    private static final byte[] MAGIC = {'S', 'P', 'X', 'I'};
    private static final int VERSION = 1;
    private static final int SHA256_BYTES = 32;
    private static final long MAX_SOURCE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_DIMENSION = 16_384;
    private static final long MAX_PIXELS = 16L * 1024 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + 128;

    private SyntheticPreparedImageCache() {
    }

    static Path cachePath(Path cacheRoot, String sourceSha256) {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        String hash = canonicalSha256(sourceSha256);
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path target = root.resolve("synthetic-startup")
                .resolve("images")
                .resolve(hash.substring(0, 2))
                .resolve(hash + ".spxi")
                .normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Synthetic image cache path escaped its root");
        }
        return target;
    }

    static Lookup lookup(Path cacheRoot, String sourceSha256) {
        final Path target;
        try {
            target = cachePath(cacheRoot, sourceSha256);
        } catch (RuntimeException error) {
            return Lookup.failure(Status.ERROR, null, message(error));
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return Lookup.failure(Status.MISS, target, "No prepared synthetic image exists");
        }
        try {
            long size = Files.size(target);
            if (size < minimumFileBytes() || size > MAX_FILE_BYTES) {
                throw new IOException("Prepared synthetic image file size is invalid: " + size);
            }
            return Lookup.hit(target, decode(readBounded(target, MAX_FILE_BYTES), sourceSha256));
        } catch (IOException | IllegalArgumentException | ArithmeticException error) {
            return Lookup.failure(Status.CORRUPT, target, message(error));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            return Lookup.failure(Status.ERROR, target, message(error));
        }
    }

    static PreparedImage decodePng(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        long sourceBytes = Files.size(source);
        if (sourceBytes < 1 || sourceBytes > MAX_SOURCE_BYTES) {
            throw new IOException("Synthetic PNG source size is invalid: " + sourceBytes);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw new IOException("Could not open synthetic PNG: " + source);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No image reader accepts synthetic PNG: " + source);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) {
                    throw new IOException("Synthetic PNG decoder returned inconsistent dimensions");
                }
                byte[] rgba = new byte[payloadLength(width, height)];
                int[] row = new int[width];
                int offset = 0;
                for (int y = 0; y < height; y++) {
                    image.getRGB(0, y, width, 1, row, 0, width);
                    for (int pixel : row) {
                        rgba[offset++] = (byte) ((pixel >>> 16) & 0xff);
                        rgba[offset++] = (byte) ((pixel >>> 8) & 0xff);
                        rgba[offset++] = (byte) (pixel & 0xff);
                        rgba[offset++] = (byte) ((pixel >>> 24) & 0xff);
                    }
                }
                return new PreparedImage(width, height, rgba);
            } finally {
                reader.dispose();
            }
        }
    }

    static void write(Path cacheRoot, String sourceSha256, PreparedImage image) throws IOException {
        Path target = cachePath(cacheRoot, sourceSha256);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        byte[] encoded = encode(sourceSha256, image);
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static byte[] encode(String sourceSha256, PreparedImage image) throws IOException {
        Objects.requireNonNull(image, "image");
        validateDimensions(image.width(), image.height());
        byte[] payload = image.internalRgba();
        if (payload.length != payloadLength(image.width(), image.height())) {
            throw new IOException("Prepared synthetic image payload length differs from its dimensions");
        }
        byte[] sourceHash = HexFormat.of().parseHex(canonicalSha256(sourceSha256));
        byte[] checksum = integritySha256(sourceHash, image.width(), image.height(), payload);
        int total = Math.addExact(minimumFileBytes(), payload.length);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(total);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.write(sourceHash);
            output.writeInt(image.width());
            output.writeInt(image.height());
            output.writeInt(payload.length);
            output.write(payload);
            output.write(checksum);
        }
        return bytes.toByteArray();
    }

    private static PreparedImage decode(byte[] encoded, String expectedSourceSha256) throws IOException {
        if (encoded.length < minimumFileBytes() || encoded.length > MAX_FILE_BYTES) {
            throw new IOException("Prepared synthetic image length is invalid");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (!Arrays.equals(MAGIC, input.readNBytes(MAGIC.length))) {
                throw new IOException("Prepared synthetic image magic header is invalid");
            }
            if (input.readInt() != VERSION) {
                throw new IOException("Prepared synthetic image version is unsupported");
            }
            byte[] sourceHash = input.readNBytes(SHA256_BYTES);
            if (sourceHash.length != SHA256_BYTES) {
                throw new EOFException("Prepared synthetic image ended inside its source hash");
            }
            byte[] expectedHash = HexFormat.of().parseHex(canonicalSha256(expectedSourceSha256));
            if (!MessageDigest.isEqual(sourceHash, expectedHash)) {
                throw new IOException("Prepared synthetic image source identity mismatch");
            }
            int width = input.readInt();
            int height = input.readInt();
            validateDimensions(width, height);
            int length = input.readInt();
            if (length != payloadLength(width, height)) {
                throw new IOException("Prepared synthetic image payload length is invalid: " + length);
            }
            byte[] payload = input.readNBytes(length);
            byte[] checksum = input.readNBytes(SHA256_BYTES);
            if (payload.length != length || checksum.length != SHA256_BYTES) {
                throw new EOFException("Prepared synthetic image ended inside its payload");
            }
            if (input.available() != 0) {
                throw new IOException("Prepared synthetic image contains trailing data");
            }
            byte[] actualChecksum = integritySha256(sourceHash, width, height, payload);
            if (!MessageDigest.isEqual(checksum, actualChecksum)) {
                throw new IOException("Prepared synthetic image checksum mismatch");
            }
            return new PreparedImage(width, height, payload);
        }
    }

    private static byte[] integritySha256(byte[] sourceHash, int width, int height, byte[] payload) {
        MessageDigest digest = sha256Digest();
        digest.update(sourceHash);
        digest.update(ByteBuffer.allocate(Integer.BYTES * 3)
                .putInt(width)
                .putInt(height)
                .putInt(payload.length)
                .array());
        digest.update(payload);
        return digest.digest();
    }

    private static byte[] readBounded(Path source, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) {
                throw new IOException("Prepared synthetic image grew beyond its file safety limit");
            }
            return bytes;
        }
    }

    private static void validateDimensions(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("Synthetic image dimensions are invalid: " + width + "x" + height);
        }
        long pixels = Math.multiplyExact((long) width, height);
        if (pixels > MAX_PIXELS) {
            throw new IOException("Synthetic image exceeds the pixel safety limit: " + pixels);
        }
    }

    private static int payloadLength(int width, int height) throws IOException {
        long bytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (bytes > MAX_PAYLOAD_BYTES) {
            throw new IOException("Synthetic image exceeds the payload safety limit: " + bytes);
        }
        return Math.toIntExact(bytes);
    }

    private static String canonicalSha256(String value) {
        Objects.requireNonNull(value, "sourceSha256");
        String hash = value.toLowerCase(Locale.ROOT);
        if (hash.length() != 64) {
            throw new IllegalArgumentException("SHA-256 must contain 64 hexadecimal characters");
        }
        try {
            HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("SHA-256 contains a non-hexadecimal character", error);
        }
        return hash;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static int minimumFileBytes() {
        return MAGIC.length + Integer.BYTES * 4 + SHA256_BYTES * 2;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    enum Status {
        HIT,
        MISS,
        CORRUPT,
        ERROR
    }

    record Lookup(Status status, Path path, PreparedImage image, String detail) {
        Lookup {
            status = Objects.requireNonNull(status, "status");
            detail = detail == null ? "" : detail;
            if (status == Status.HIT && image == null) {
                throw new IllegalArgumentException("A synthetic image cache hit requires an image");
            }
            if (status != Status.HIT && image != null) {
                throw new IllegalArgumentException("Only a synthetic image cache hit may contain an image");
            }
        }

        static Lookup hit(Path path, PreparedImage image) {
            return new Lookup(Status.HIT, path, Objects.requireNonNull(image, "image"), "");
        }

        static Lookup failure(Status status, Path path, String detail) {
            if (status == Status.HIT) throw new IllegalArgumentException("Use hit() for cache hits");
            return new Lookup(status, path, null, detail);
        }
    }

    record PreparedImage(int width, int height, byte[] rgba) {
        PreparedImage {
            Objects.requireNonNull(rgba, "rgba");
            rgba = rgba.clone();
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }

        byte[] internalRgba() {
            return rgba;
        }
    }
}
