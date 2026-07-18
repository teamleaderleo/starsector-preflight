package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundWrapperObservationRuntimeLauncherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void prefersCanonicalBundledRuntimeOverLowerPriorityCandidates() throws Exception {
        Path game = temporaryDirectory.resolve("Starsector.app");
        Path canonical = executable(game.resolve("Contents/Home/bin").resolve(javaName()));
        executable(game.resolve("Contents/Resources/Java/jre/bin").resolve(javaName()));

        SoundWrapperObservationRuntimeLauncher.JavaSelection selected =
                SoundWrapperObservationRuntimeLauncher.selectJava(game, null, java -> "synthetic-version");

        assertEquals(canonical.toAbsolutePath().normalize(), selected.executable());
        assertEquals("bundled-auto", selected.source());
    }

    @Test
    void skipsHigherPriorityCandidateThatCannotLaunch() throws Exception {
        Path game = temporaryDirectory.resolve("Starsector.app");
        Path broken = executable(game.resolve("Contents/Home/bin").resolve(javaName()));
        Path fallback = executable(game.resolve("Contents/Resources/Java/jre/bin").resolve(javaName()));

        SoundWrapperObservationRuntimeLauncher.JavaSelection selected =
                SoundWrapperObservationRuntimeLauncher.selectJava(game, null, java -> {
                    if (java.equals(broken.toAbsolutePath().normalize())) {
                        throw new IOException("synthetic launch failure");
                    }
                    return "synthetic-version";
                });

        assertEquals(fallback.toAbsolutePath().normalize(), selected.executable());
        assertEquals("bundled-auto", selected.source());
    }

    @Test
    void explicitRuntimeOverridesBundledDiscovery() throws Exception {
        Path game = temporaryDirectory.resolve("game");
        Files.createDirectories(game);
        executable(game.resolve("Contents/Home/bin").resolve(javaName()));
        Path explicit = executable(temporaryDirectory.resolve("custom/bin").resolve(javaName()));

        SoundWrapperObservationRuntimeLauncher.JavaSelection selected =
                SoundWrapperObservationRuntimeLauncher.selectJava(game, explicit, java -> "synthetic-version");

        assertEquals(explicit.toAbsolutePath().normalize(), selected.executable());
        assertEquals("explicit", selected.source());
    }

    @Test
    void rejectsExplicitRuntimeThatCannotLaunch() throws Exception {
        Path game = temporaryDirectory.resolve("game");
        Files.createDirectories(game);
        Path explicit = executable(temporaryDirectory.resolve("custom/bin").resolve(javaName()));

        IOException error = assertThrows(
                IOException.class,
                () -> SoundWrapperObservationRuntimeLauncher.selectJava(game, explicit, java -> {
                    throw new IOException("synthetic launch failure");
                }));

        assertTrue(error.getMessage().contains("synthetic launch failure"), error.getMessage());
    }

    @Test
    void requiresTheThreeInstallationInputs() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SoundWrapperObservationRuntimeLauncher.Options.parse(
                        new String[] {"--game", temporaryDirectory.toString()}));
        assertTrue(error.getMessage().contains("--jogg"), error.getMessage());
    }

    @Test
    void parsesExplicitRuntimeAndOutput() {
        Path game = temporaryDirectory.resolve("game");
        Path jogg = temporaryDirectory.resolve("jogg.jar");
        Path jorbis = temporaryDirectory.resolve("jorbis.jar");
        Path java = temporaryDirectory.resolve(javaName());
        Path output = temporaryDirectory.resolve("report.json");

        SoundWrapperObservationRuntimeLauncher.Options options =
                SoundWrapperObservationRuntimeLauncher.Options.parse(new String[] {
                    "--game", game.toString(),
                    "--jogg", jogg.toString(),
                    "--jorbis", jorbis.toString(),
                    "--java", java.toString(),
                    "--output", output.toString()
                });

        assertEquals(game, options.game());
        assertEquals(jogg, options.jogg());
        assertEquals(jorbis, options.jorbis());
        assertEquals(java, options.java());
        assertEquals(output, options.output());
    }

    @Test
    void mergesContentSafeRuntimeIdentityIntoExistingReport() throws Exception {
        Path report = temporaryDirectory.resolve("report.json");
        Files.writeString(report, "{\"observationComplete\":false}\n");
        Path java = temporaryDirectory.resolve(javaName()).toAbsolutePath().normalize();
        SoundWrapperObservationRuntimeLauncher.RuntimeEvidence evidence =
                new SoundWrapperObservationRuntimeLauncher.RuntimeEvidence(
                        java,
                        "bundled-auto",
                        "a".repeat(64),
                        123,
                        "b".repeat(64));

        SoundWrapperObservationRuntimeLauncher.mergeRuntimeEvidence(report, evidence);

        String json = Files.readString(report);
        assertTrue(json.contains("\"childJavaExecutable\":\""), json);
        assertTrue(json.contains("\"childJavaSelectionSource\":\"bundled-auto\""), json);
        assertTrue(json.contains("\"childJavaExecutableSha256\":\"" + "a".repeat(64) + "\""), json);
        assertTrue(json.contains("\"childJavaVersionOutputLength\":123"), json);
        assertTrue(json.contains("\"childJavaVersionOutputSha256\":\"" + "b".repeat(64) + "\""), json);
        assertTrue(json.contains("\"observationComplete\":false"), json);
    }

    @Test
    void rejectsMissingBundledRuntime() throws Exception {
        Path game = temporaryDirectory.resolve("empty-game");
        Files.createDirectories(game);

        Exception error = assertThrows(
                Exception.class,
                () -> SoundWrapperObservationRuntimeLauncher.selectJava(game, null));
        assertTrue(error.getMessage().contains("Could not locate a bundled Java executable"), error.getMessage());
    }

    private static Path executable(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "synthetic java");
        path.toFile().setExecutable(true);
        return path;
    }

    private static String javaName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }
}
