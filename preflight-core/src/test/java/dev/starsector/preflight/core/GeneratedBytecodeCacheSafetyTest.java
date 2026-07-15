package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedBytecodeCacheSafetyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void directoryAtExactBundlePathIsAnErrorAndDirectReadRejectsIt() throws Exception {
        String context = "a".repeat(64);
        String requested = Fixture.class.getName();
        Path target = GeneratedBytecodeCache.bundlePath(temporaryDirectory, context, requested);
        Files.createDirectories(target);

        GeneratedBytecodeCache.Lookup lookup =
                GeneratedBytecodeCache.lookup(temporaryDirectory, context, requested);
        assertEquals(GeneratedBytecodeCache.Status.ERROR, lookup.status());
        assertThrows(IOException.class, () -> GeneratedBytecodeBundleIO.read(target));
    }

    @Test
    void disappearingExactBundleIsFailOpen() throws Exception {
        String context = "b".repeat(64);
        String requested = Fixture.class.getName();
        GeneratedBytecodeBundle bundle = new GeneratedBytecodeBundle(
                context,
                requested,
                Map.of(requested, classBytes(Fixture.class)));
        GeneratedBytecodeCache.write(temporaryDirectory, bundle);
        Path target = GeneratedBytecodeCache.bundlePath(temporaryDirectory, context, requested);
        Files.delete(target);

        GeneratedBytecodeCache.Lookup lookup =
                GeneratedBytecodeCache.lookup(temporaryDirectory, context, requested);
        assertEquals(GeneratedBytecodeCache.Status.MISS, lookup.status());
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test class resource " + resource);
            return input.readAllBytes();
        }
    }

    static final class Fixture {
    }
}
