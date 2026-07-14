package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentFingerprintTest {
    @TempDir
    Path temp;

    @Test
    void isDeterministicAndContentAware() throws Exception {
        Files.writeString(temp.resolve("b.txt"), "beta");
        Files.createDirectories(temp.resolve("nested"));
        Files.writeString(temp.resolve("nested/a.txt"), "alpha");

        String first = ContentFingerprint.compute(temp);
        String second = ContentFingerprint.compute(temp);
        assertEquals(first, second);

        Files.writeString(temp.resolve("nested/a.txt"), "changed");
        assertNotEquals(first, ContentFingerprint.compute(temp));
    }
}
