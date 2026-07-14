package dev.starsector.preflight.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;

/** Versioned raw blob persistence for upload-ready texture data. */
public final class PreparedTextureIO {
    private static final byte[] MAGIC = {'S', 'P', 'F', 'T'};
    private static final int CHECKSUM_BYTES = 32;
    private static final int SHA256_BYTES = 32;
    private static final int CODEC_RAW = 0;
    private static final int MAX_FILE_BYTES = 512 * 1024 * 1024;

    private PreparedTextureIO() {
    }

    public static void write(Path target, PreparedTexture texture) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = toBytes(texture);
        Path temporary = absolute.resolveSibling(
                absolute.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static PreparedTexture read(Path source) throws IOException {
        long size = Files.size(source);
        if (size < minimumFileBytes()) {
            throw new IOException("Prepared texture blob is too small: " + source);
        }
        if (size > MAX_FILE_BYTES) {
            throw new IOException("Prepared texture blob exceeds the " + MAX_FILE_BYTES + " byte safety limit: " + source);
        }
        return fromBytes(Files.readAllBytes(source));
    }

    public static byte[] toBytes(PreparedTexture texture) throws IOException {
        byte[] payload = encodePayload(texture);
        long total = minimumFileBytes() + payload.length;
        if (total > MAX_FILE_BYTES) {
            throw new IOException("Prepared texture blob exceeds the " + MAX_FILE_BYTES + " byte safety limit");
        }
        byte[] checksum = Hashes.sha256Bytes(payload);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) total);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeInt(PreparedTexture.FORMAT_VERSION);
            output.writeInt(payload.length);
            output.write(payload);
            output.write(checksum);
        }
        return bytes.toByteArray();
    }

    public static PreparedTexture fromBytes(byte[] bytes) throws IOException {
        if (bytes.length < minimumFileBytes()) {
            throw new IOException("Prepared texture blob is too small");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Prepared texture blob exceeds the " + MAX_FILE_BYTES + " byte safety limit");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IOException("Prepared texture magic header is invalid");
            }
            int version = input.readInt();
            if (version != PreparedTexture.FORMAT_VERSION) {
                throw new IOException("Unsupported prepared texture version: " + version);
            }
            int payloadLength = input.readInt();
            long expectedLength = minimumFileBytes() + (long) payloadLength;
            if (payloadLength < 0 || expectedLength != bytes.length) {
                throw new IOException("Prepared texture payload length is invalid");
            }
            byte[] payload = input.readNBytes(payloadLength);
            byte[] checksum = input.readNBytes(CHECKSUM_BYTES);
            if (payload.length != payloadLength || checksum.length != CHECKSUM_BYTES) {
                throw new EOFException("Prepared texture ended before its checksum");
            }
            if (!MessageDigest.isEqual(checksum, Hashes.sha256Bytes(payload))) {
                throw new IOException("Prepared texture checksum mismatch");
            }
            return decodePayload(payload);
        } catch (IllegalArgumentException | ArithmeticException error) {
            throw new IOException("Prepared texture contains invalid data: " + error.getMessage(), error);
        }
    }

    private static byte[] encodePayload(PreparedTexture texture) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(texture.pixelBytes() + 128);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(Hashes.decodeSha256(texture.sourceSha256()));
            output.writeInt(texture.transformation().id());
            output.writeInt(texture.originalWidth());
            output.writeInt(texture.originalHeight());
            output.writeInt(texture.uploadWidth());
            output.writeInt(texture.uploadHeight());
            output.writeInt(texture.channels());
            output.writeInt(texture.color0Rgba());
            output.writeInt(texture.color1Rgba());
            output.writeInt(texture.color2Rgba());
            output.writeInt(CODEC_RAW);
            output.writeInt(texture.pixelBytes());
            output.write(texture.pixels());
        }
        return bytes.toByteArray();
    }

    private static PreparedTexture decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte[] sourceHash = input.readNBytes(SHA256_BYTES);
            if (sourceHash.length != SHA256_BYTES) {
                throw new EOFException("Prepared texture ended inside its source hash");
            }
            PreparedTexture.Transformation transformation = PreparedTexture.Transformation.fromId(input.readInt());
            int originalWidth = input.readInt();
            int originalHeight = input.readInt();
            int uploadWidth = input.readInt();
            int uploadHeight = input.readInt();
            int channels = input.readInt();
            int color0 = input.readInt();
            int color1 = input.readInt();
            int color2 = input.readInt();
            int codec = input.readInt();
            if (codec != CODEC_RAW) {
                throw new IOException("Unsupported prepared texture codec: " + codec);
            }
            int pixelLength = input.readInt();
            if (pixelLength < 0 || pixelLength > MAX_FILE_BYTES) {
                throw new IOException("Prepared texture pixel length is invalid: " + pixelLength);
            }
            byte[] pixels = input.readNBytes(pixelLength);
            if (pixels.length != pixelLength) {
                throw new EOFException("Prepared texture ended inside its pixels");
            }
            if (input.available() != 0) {
                throw new IOException("Prepared texture payload contains trailing data");
            }
            return new PreparedTexture(
                    java.util.HexFormat.of().formatHex(sourceHash),
                    transformation,
                    originalWidth,
                    originalHeight,
                    uploadWidth,
                    uploadHeight,
                    channels,
                    color0,
                    color1,
                    color2,
                    pixels);
        }
    }

    private static long minimumFileBytes() {
        return MAGIC.length + Integer.BYTES * 2L + CHECKSUM_BYTES;
    }
}
