package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathIndexCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsQueriesInspectsAndValidatesProfile() throws Exception {
        Path install = fixture();
        Path cache = temporaryDirectory.resolve("cache");

        String build = capture(() -> PreflightCli.run(new String[] {
                "classpath", "index", "build",
                "--game", install.toString(),
                "--cache-dir", cache.toString()
        }));
        assertTrue(build.contains("\"archiveBuilds\":2"), build);
        Path profile;
        try (var stream = Files.list(cache.resolve("classpath/profiles"))) {
            profile = stream.filter(path -> path.toString().endsWith(".spfc")).findFirst().orElseThrow();
        }

        String inspect = capture(() -> PreflightCli.run(new String[] {
                "classpath", "index", "inspect", profile.toString()
        }));
        assertTrue(inspect.contains("\"archiveCount\":2"), inspect);
        assertTrue(inspect.contains("\"entryCount\":3"), inspect);

        String winner = capture(() -> PreflightCli.run(new String[] {
                "classpath", "index", "query", profile.toString(), "shared/Utility.class",
                "--cache-dir", cache.toString()
        }));
        assertTrue(winner.contains("\"modId\":\"campaign\""), winner);
        assertTrue(winner.contains("\"className\":\"shared.Utility\""), winner);

        String all = capture(() -> PreflightCli.run(new String[] {
                "classpath", "index", "query", profile.toString(), "shared/Utility.class",
                "--all", "--cache-dir", cache.toString()
        }));
        assertTrue(all.indexOf("\"modId\":\"library\"") < all.indexOf("\"modId\":\"campaign\""), all);

        assertEquals(4, PreflightCli.run(new String[] {
                "classpath", "index", "query", profile.toString(), "missing/Type.class",
                "--cache-dir", cache.toString()
        }));
        assertEquals(0, PreflightCli.run(new String[] {
                "classpath", "index", "validate", profile.toString(),
                "--cache-dir", cache.toString(), "--deep"
        }));

        String repeat = capture(() -> PreflightCli.run(new String[] {
                "classpath", "index", "build",
                "--game", install.toString(),
                "--cache-dir", cache.toString()
        }));
        assertTrue(repeat.contains("\"profileHit\":true"), repeat);
    }

    private Path fixture() throws Exception {
        Path install = temporaryDirectory.resolve("Starsector");
        Path mods = install.resolve("mods");
        Path library = mods.resolve("Library");
        Path campaign = mods.resolve("Campaign");
        write(library.resolve("mod_info.json"), "{\"id\":\"library\",\"jars\":[\"jars/library.jar\"]}");
        write(campaign.resolve("mod_info.json"), "{\"id\":\"campaign\",\"jars\":[\"jars/campaign.jar\"]}");
        jar(library.resolve("jars/library.jar"), Map.of(
                "shared/Utility.class", new byte[] {1},
                "library/Only.class", new byte[] {2}));
        jar(campaign.resolve("jars/campaign.jar"), Map.of(
                "shared/Utility.class", new byte[] {3},
                "campaign/Only.class", new byte[] {4}));
        write(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[\"library\",\"campaign\"]}");
        Path launcher = install.resolve("starsector.sh");
        write(launcher, "#!/bin/sh\n");
        launcher.toFile().setExecutable(true);
        return install;
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

    private static void jar(Path path, Map<String, byte[]> entries) throws Exception {
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
    }

    private static void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }

    @FunctionalInterface
    private interface ThrowingInt {
        int run() throws Exception;
    }
}
