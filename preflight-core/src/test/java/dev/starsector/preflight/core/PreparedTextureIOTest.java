package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedTextureIOTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsAndWritesDeterministically() throws Exception {
        PreparedTexture texture = fixture();
        byte[] first = PreparedTextureIO.toBytes(texture);
        byte[] second = PreparedTextureIO.toBytes(fixture());
        assertArrayEquals(first, second);

        Path output = temporaryDirectory.resolve("nested/example.spft");
        PreparedTextureIO.write(output, texture);
        PreparedTexture restored = PreparedTextureIO.read(output);

        assertEquals(texture, restored);
        assertTrue(Files.isRegularFile(output));
    }

    @Test
    void rejectsCorruptionAndTruncation() throws Exception {
        byte[] bytes = PreparedTextureIO.toBytes(fixture());
        byte[] corrupt = bytes.clone();
        corrupt[corrupt.length / 2] ^= 0x44;
        IOException checksum = assertThrows(IOException.class, () -> PreparedTextureIO.fromBytes(corrupt));
        assertTrue(checksum.getMessage().contains("checksum"));

        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 7);
        assertThrows(IOException.class, () -> PreparedTextureIO.fromBytes(truncated));
    }

    private static PreparedTexture fixture() {
        return new PreparedTexture(
                "ab".repeat(32),
                PreparedTexture.Transformation.IDENTITY,
                2,
                1,
                2,
                1,
                4,
                PreparedTexture.rgba(1, 2, 3, 255),
                PreparedTexture.rgba(4, 5, 6, 255),
                PreparedTexture.rgba(7, 8, 9, 255),
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    }
}
