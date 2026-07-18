package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundWrapperObservationCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void findsOneArchiveContainingBothExactWrapperClasses() throws Exception {
        Path archive = temporaryDirectory.resolve("core.jar");
        writeSoundArchive(archive);

        assertEquals(
                archive.toAbsolutePath().normalize(),
                SoundWrapperObservationCommand.findSoundArchive(temporaryDirectory));
    }

    @Test
    void rejectsAmbiguousWrapperProviders() throws Exception {
        writeSoundArchive(temporaryDirectory.resolve("a.jar"));
        writeSoundArchive(temporaryDirectory.resolve("b.jar"));

        IOException error = assertThrows(
                IOException.class,
                () -> SoundWrapperObservationCommand.findSoundArchive(temporaryDirectory));
        assertTrue(error.getMessage().contains("Ambiguous sound-wrapper provider archives"), error.getMessage());
    }

    @Test
    void requiresAllExplicitPaths() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SoundWrapperObservationCommand.Options.parse(
                        new String[] {"audio", "sound-wrapper-observe", "--game", temporaryDirectory.toString()},
                        2));
        assertTrue(error.getMessage().contains("--jogg"), error.getMessage());
    }

    @Test
    void parsesExplicitJavaForDirectRuntimeHandoff() {
        Path java = temporaryDirectory.resolve("selected-java");
        SoundWrapperObservationCommand.Options options = SoundWrapperObservationCommand.Options.parse(
                new String[] {
                    "audio", "sound-wrapper-observe",
                    "--game", temporaryDirectory.toString(),
                    "--jogg", temporaryDirectory.resolve("jogg.jar").toString(),
                    "--jorbis", temporaryDirectory.resolve("jorbis.jar").toString(),
                    "--java", java.toString()
                },
                2);

        assertEquals(java, options.java());
    }

    private static void writeSoundArchive(Path destination) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(destination))) {
            for (String name : new String[] {"sound/J.class", "sound/F.class"}) {
                JarEntry entry = new JarEntry(name);
                entry.setTime(0);
                output.putNextEntry(entry);
                output.write(new byte[] {0, 1, 2, 3});
                output.closeEntry();
            }
        }
    }
}
