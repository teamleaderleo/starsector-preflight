package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ClasspathProfileIndex;
import dev.starsector.preflight.core.ClasspathProfileIndexIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesBothPersistedIndexesThroughCli() throws Exception {
        Path core = temporaryDirectory.resolve("core");
        Path mod = temporaryDirectory.resolve("mod");
        Path coreFile = write(core.resolve("graphics/example.png"), new byte[] {1});
        Path modFile = write(mod.resolve("graphics/example.png"), new byte[] {2});
        ResourceIndex resources = new ResourceIndex(
                "11".repeat(32),
                List.of(
                        new ResourceIndex.Root("core", core, true),
                        new ResourceIndex.Root("mod", mod, false)),
                Map.of("graphics/example.png", List.of(
                        provider(0, "graphics/example.png", coreFile),
                        provider(1, "graphics/example.png", modFile))));
        Path resourceIndex = temporaryDirectory.resolve("resources.spfi");
        ResourceIndexIO.write(resourceIndex, resources);

        Path libraryJar = jar(temporaryDirectory.resolve("library.jar"), Map.of(
                "shared/Utility.class", new byte[] {3},
                "library/Only.class", new byte[] {4}));
        Path campaignJar = jar(temporaryDirectory.resolve("campaign.jar"), Map.of(
                "shared/Utility.class", new byte[] {5},
                "campaign/Only.class", new byte[] {6}));
        ClasspathProfileIndex classpath = new ClasspathProfileIndex(
                "22".repeat(32),
                List.of(
                        archive("library", libraryJar, "33"),
                        archive("campaign", campaignJar, "44")),
                Map.of(
                        "shared/Utility.class", List.of(0, 1),
                        "library/Only.class", List.of(0),
                        "campaign/Only.class", List.of(1)));
        Path classpathIndex = temporaryDirectory.resolve("classpath.spfc");
        ClasspathProfileIndexIO.write(classpathIndex, classpath);

        String output = capture(() -> PreflightCli.run(new String[] {
                "benchmark", "lookups",
                "--resource-index", resourceIndex.toString(),
                "--classpath-index", classpathIndex.toString(),
                "--queries", "1000",
                "--seed", "42"
        }));

        assertTrue(output.contains("\"equivalent\":true"), output);
        assertTrue(output.contains("\"totalMismatches\":0"), output);
        assertTrue(output.contains("\"domain\":\"resources\""), output);
        assertTrue(output.contains("\"domain\":\"classpath\""), output);
        assertTrue(output.contains("\"baselineProbes\":2000"), output);
    }

    @Test
    void recordsBoundedScenarioTelemetryThroughCli() throws Exception {
        Path result = temporaryDirectory.resolve("results").resolve("benchmark.json");
        String output = capture(() -> PreflightCli.run(new String[] {
                "benchmark", "scenario",
                "--run-id", "run-20260717-100000",
                "--scenario-id", "campaign-combat-v1",
                "--mode", "enabled-warm-hit",
                "--iteration", "3",
                "--profile-fingerprint", "ab".repeat(32),
                "--process-start", "2026-07-17T10:00:00Z",
                "--main-menu-ready", "2026-07-17T10:00:12Z",
                "--campaign-ready", "2026-07-17T10:01:02Z",
                "--first-combat-ready", "2026-07-17T10:01:32Z",
                "--exit-code", "0",
                "--adapter-counter", "texture.hits=17",
                "--adapter-counter", "texture.misses=2",
                "--cache-counter", "entries=15",
                "--cache-counter", "bytes=4096",
                "--disable-reason", "none",
                "--output", result.toString()
        }));

        assertTrue(output.contains(result.toAbsolutePath().normalize().toString()), output);
        String json = Files.readString(result);
        assertTrue(json.contains("\"schema\":\"starsector-preflight-benchmark-scenario\""), json);
        assertTrue(json.contains("\"mode\":\"enabled-warm-hit\""), json);
        assertTrue(json.contains("\"processToMainMenuMs\":12000"), json);
        assertTrue(json.contains("\"mainMenuToCampaignReadyMs\":50000"), json);
        assertTrue(json.contains("\"campaignToFirstCombatReadyMs\":30000"), json);
        assertTrue(json.contains("\"adapterCounters\":{\"texture.hits\":17,\"texture.misses\":2}"), json);
        assertTrue(json.contains("\"cacheCounters\":{\"bytes\":4096,\"entries\":15}"), json);
        assertTrue(json.contains("\"exit\":{\"code\":0,\"successful\":true}"), json);
    }

    private static ResourceIndex.Provider provider(int root, String relative, Path file) throws Exception {
        return new ResourceIndex.Provider(
                root,
                relative,
                Files.size(file),
                Files.getLastModifiedTime(file).toMillis());
    }

    private static ClasspathProfileIndex.Archive archive(String modId, Path jar, String hashPair) throws Exception {
        return new ClasspathProfileIndex.Archive(
                modId,
                jar.getFileName().toString(),
                jar,
                hashPair.repeat(32),
                Files.size(jar),
                Files.getLastModifiedTime(jar).toMillis(),
                "classpath/archives/" + hashPair + "/" + hashPair.repeat(32) + ".spfj",
                true);
    }

    private static Path write(Path path, byte[] value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, value);
        return path;
    }

    private static Path jar(Path path, Map<String, byte[]> entries) throws Exception {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0);
                output.putNextEntry(jarEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private static String capture(ThrowingInt operation) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            assertEquals(0, operation.run());
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingInt {
        int run() throws Exception;
    }
}
