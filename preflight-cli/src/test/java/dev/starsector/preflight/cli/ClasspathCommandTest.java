package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatchesAuditAndWritesReport() throws Exception {
        Path mods = temporaryDirectory.resolve("mods");
        Path library = mods.resolve("Library");
        Files.createDirectories(library);
        Files.writeString(library.resolve("mod_info.json"), """
                {
                  "id":"library",
                  "jars":["jars/library.jar"]
                }
                """);
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[\"library\"]}");
        Path jar = library.resolve("jars/library.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("fixture/Library.class");
            entry.setTime(0);
            output.putNextEntry(entry);
            output.write(new byte[] {1, 2, 3});
            output.closeEntry();
        }
        Path launcher = Files.writeString(temporaryDirectory.resolve("starsector.sh"), "#!/bin/sh\n");
        launcher.toFile().setExecutable(true);
        Path report = temporaryDirectory.resolve("reports/classpath.json");

        assertEquals(0, PreflightCli.run(new String[] {
                "classpath", "audit",
                "--game", temporaryDirectory.toString(),
                "--json", report.toString()
        }));
        String json = Files.readString(report);
        assertTrue(json.contains("\"classpathFingerprint\""));
        assertTrue(json.contains("fixture.Library"));
    }
}
