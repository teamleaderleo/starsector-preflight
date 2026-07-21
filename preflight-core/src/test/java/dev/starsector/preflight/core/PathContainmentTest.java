package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathContainmentTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsExistingPathsWhoseLinksStayInsideTheRoot() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path target = root.resolve("data/target.txt");
        Path link = root.resolve("data/link.txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "inside");
        createSymbolicLinkOrSkip(link, target);

        Path realRoot = PathContainment.realDirectory(root);
        assertEquals(target.toRealPath(), PathContainment.existingInside(root, link));
        assertEquals(target.toRealPath(), PathContainment.existingInsideRealRoot(realRoot, link));
    }

    @Test
    void rejectsExistingPathsWhoseLinksEscapeTheRoot() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path outside = temporaryDirectory.resolve("outside.txt");
        Path link = root.resolve("data/link.txt");
        Files.createDirectories(link.getParent());
        Files.writeString(outside, "outside");
        createSymbolicLinkOrSkip(link, outside);

        Path realRoot = PathContainment.realDirectory(root);
        assertThrows(IllegalArgumentException.class, () -> PathContainment.existingInside(root, link));
        assertThrows(IllegalArgumentException.class, () -> PathContainment.existingInsideRealRoot(realRoot, link));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        } catch (UnsupportedOperationException | SecurityException | IOException error) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + error.getMessage());
        }
    }
}
