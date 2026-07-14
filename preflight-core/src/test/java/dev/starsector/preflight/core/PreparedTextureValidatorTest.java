package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedTextureValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsChangedSourceContent() throws Exception {
        Path source = temporaryDirectory.resolve("source.png");
        Files.writeString(source, "original");
        PreparedTexture texture = new PreparedTexture(
                Hashes.sha256(source),
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                3,
                0,
                0,
                0,
                new byte[3]);

        assertTrue(PreparedTextureValidator.validateSource(source, texture).valid());
        Files.writeString(source, "changed");
        assertFalse(PreparedTextureValidator.validateSource(source, texture).valid());
    }
}
