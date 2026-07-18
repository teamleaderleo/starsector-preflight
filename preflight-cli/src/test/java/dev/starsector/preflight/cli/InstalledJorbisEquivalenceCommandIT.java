package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class InstalledJorbisEquivalenceCommandIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedCommandLoadsExactSyntheticDecoderJarsAndProvesCiFixtures() throws Exception {
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path jogg = temporaryDirectory.resolve("jogg-0.0.7.jar");
        Path jorbis = temporaryDirectory.resolve("jorbis-0.0.15.jar");
        writeJar(jogg, testClasses, List.of("com/jcraft/jogg"));
        writeJar(jorbis, testClasses, List.of("com/jcraft/jorbis"));

        Path report = temporaryDirectory.resolve("installed-jorbis-equivalence.json");
        InstalledJorbisEquivalenceCommand.Options options =
                new InstalledJorbisEquivalenceCommand.Options(jogg, jorbis, report);
        int exit = InstalledJorbisEquivalenceCommand.execute(
                options,
                Hashes.sha256(jogg),
                Hashes.sha256(jorbis),
                Path.of("target", "preflight.jar"),
                "ci");

        assertEquals(0, exit);
        String json = Files.readString(report);
        assertTrue(json.contains("\"format\":\"starsector-preflight-installed-jorbis-equivalence-v1\""), json);
        assertTrue(json.contains("\"fixtureProfile\":\"ci\""), json);
        assertTrue(json.contains("\"identityExact\":true"), json);
        assertTrue(json.contains("\"validPcmEquivalent\":true"), json);
        assertTrue(json.contains("\"invalidBehaviorStable\":true"), json);
        assertTrue(json.contains("\"equivalent\":true"), json);
        assertTrue(json.contains("\"validCaseCount\":3"), json);
        assertTrue(json.contains("\"invalidCaseCount\":5"), json);
        assertTrue(json.contains("\"caseCount\":8"), json);
        assertTrue(json.contains("\"streamedMusicEligible\":false"), json);
        assertTrue(json.contains("\"preparedAudioWritesEnabled\":false"), json);
        assertTrue(json.contains("\"liveTransformEnabled\":false"), json);
        assertTrue(json.contains("\"streamOwnershipExact\":true"), json);
    }

    @Test
    void rejectsAnyDecoderArchiveIdentityChangeBeforeLaunchingChild() throws Exception {
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path jogg = temporaryDirectory.resolve("jogg.jar");
        Path jorbis = temporaryDirectory.resolve("jorbis.jar");
        writeJar(jogg, testClasses, List.of("com/jcraft/jogg"));
        writeJar(jorbis, testClasses, List.of("com/jcraft/jorbis"));

        InstalledJorbisEquivalenceCommand.Options options =
                new InstalledJorbisEquivalenceCommand.Options(jogg, jorbis, temporaryDirectory.resolve("report.json"));
        IOException error = assertThrows(
                IOException.class,
                () -> InstalledJorbisEquivalenceCommand.execute(
                        options,
                        "00".repeat(32),
                        Hashes.sha256(jorbis),
                        Path.of("target", "preflight.jar"),
                        "ci"));
        assertTrue(error.getMessage().contains("Jogg JAR SHA-256 differs"), error.getMessage());
    }

    private static void writeJar(Path destination, Path classes, List<String> packageRoots) throws IOException {
        Files.createDirectories(destination.getParent());
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
