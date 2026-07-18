package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundWrapperObservationCommandIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedCommandObservesSyntheticWrapperWithoutEnablingReuse() throws Exception {
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path sound = temporaryDirectory.resolve("sound-wrapper.jar");
        Path jogg = temporaryDirectory.resolve("jogg.jar");
        Path jorbis = temporaryDirectory.resolve("jorbis.jar");
        writeJar(sound, testClasses, List.of("sound"));
        writeJar(jogg, testClasses, List.of("com/jcraft/jogg"));
        writeJar(jorbis, testClasses, List.of("com/jcraft/jorbis"));

        Path report = temporaryDirectory.resolve("sound-wrapper-observation.json");
        SoundWrapperObservationCommand.Options options =
                new SoundWrapperObservationCommand.Options(temporaryDirectory, jogg, jorbis, report);
        int exit = SoundWrapperObservationCommand.execute(
                options,
                Hashes.sha256(jogg),
                Hashes.sha256(jorbis),
                Path.of("target", "preflight.jar"),
                "ci");

        String json = Files.readString(report);
        assertEquals(0, exit, json);
        assertTrue(json.contains("\"format\":\"starsector-preflight-sound-wrapper-observation-v1\""), json);
        assertTrue(json.contains("\"observationComplete\":true"), json);
        assertTrue(json.contains("\"identityExact\":true"), json);
        assertTrue(json.contains("\"wrapperPayloadMatchesDirectJorbis\":false"), json);
        assertTrue(json.contains("\"equivalenceEstablished\":false"), json);
        assertTrue(json.contains("\"requiresHumanReview\":true"), json);
        assertTrue(json.contains("\"preparedAudioWritesEnabled\":false"), json);
        assertTrue(json.contains("\"cacheReadsEnabled\":false"), json);
        assertTrue(json.contains("\"liveTransformEnabled\":false"), json);
        assertTrue(json.contains("\"decodedAudioIncluded\":false"), json);
    }

    private static void writeJar(Path destination, Path classes, List<String> packageRoots) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(destination))) {
            for (String packageRoot : packageRoots) {
                Path root = classes.resolve(packageRoot);
                try (var files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".class"))
                            .sorted(Comparator.comparing(Path::toString))
                            .toList()) {
                        String name = classes.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                        JarEntry entry = new JarEntry(name);
                        entry.setTime(0);
                        output.putNextEntry(entry);
                        Files.copy(file, output);
                        output.closeEntry();
                    }
                }
            }
        }
    }
}
