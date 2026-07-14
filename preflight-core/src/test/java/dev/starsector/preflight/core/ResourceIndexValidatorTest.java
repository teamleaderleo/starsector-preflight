package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceIndexValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesUnchangedFilesAndDetectsSizeChanges() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path file = root.resolve("data/example.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        ResourceIndex index = index(root, attributes.size(), attributes.lastModifiedTime().toMillis());

        ResourceIndexValidator.Result valid = ResourceIndexValidator.validate(index);
        assertTrue(valid.valid());
        assertEquals(1, valid.checkedProviders());

        Files.writeString(file, "{\"changed\":true}");
        ResourceIndexValidator.Result changed = ResourceIndexValidator.validate(index);
        assertFalse(changed.valid());
        assertEquals(1, changed.invalidProviders());
        assertEquals(ResourceIndexValidator.Kind.SIZE_CHANGED, changed.problems().get(0).kind());
    }

    @Test
    void detectsDeletedFilesAndMissingRoots() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path file = root.resolve("data/example.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        ResourceIndex index = index(root, attributes.size(), attributes.lastModifiedTime().toMillis());

        Files.delete(file);
        ResourceIndexValidator.Result missingFile = ResourceIndexValidator.validate(index);
        assertEquals(ResourceIndexValidator.Kind.FILE_MISSING, missingFile.problems().get(0).kind());

        Files.delete(file.getParent());
        Files.delete(root);
        ResourceIndexValidator.Result missingRoot = ResourceIndexValidator.validate(index);
        assertEquals(ResourceIndexValidator.Kind.ROOT_MISSING, missingRoot.problems().get(0).kind());
        assertEquals(1, missingRoot.invalidProviders());
    }

    @Test
    void capsReportedProblemsWhileCountingEveryInvalidProvider() throws Exception {
        Path root = temporaryDirectory.resolve("missing-root");
        ResourceIndex index = new ResourceIndex(
                "fingerprint",
                List.of(new ResourceIndex.Root("root", root, false)),
                Map.of(
                        "one", List.of(new ResourceIndex.Provider(0, "one", 1, 1)),
                        "two", List.of(new ResourceIndex.Provider(0, "two", 1, 1))));

        ResourceIndexValidator.Result result = ResourceIndexValidator.validate(index, 1);

        assertFalse(result.valid());
        assertEquals(2, result.invalidProviders());
        assertEquals(1, result.problems().size());
        assertTrue(result.truncated());
    }

    private static ResourceIndex index(Path root, long size, long modified) {
        return new ResourceIndex(
                "fingerprint",
                List.of(new ResourceIndex.Root("root", root, false)),
                Map.of("data/example.json", List.of(
                        new ResourceIndex.Provider(0, "data/example.json", size, Math.max(0, modified)))));
    }
}
