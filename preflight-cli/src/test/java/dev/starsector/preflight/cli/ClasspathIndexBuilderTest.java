package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ClasspathProfileIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathIndexBuilderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reusesProfilesAndArchivesAndRepairsCorruption() throws Exception {
        Fixture fixture = fixture();
        Path cache = temporaryDirectory.resolve("cache");

        ClasspathIndexBuilder.Result first = ClasspathIndexBuilder.build(fixture.install(), cache);
        assertFalse(first.profileHit());
        assertTrue(first.profileWritten());
        assertEquals(0, first.archiveHits());
        assertEquals(2, first.archiveBuilds());
        assertEquals(0, first.failedArchives(), first.diagnostics().toString());
        assertEquals("campaign", first.profile().winner("shared/Utility.class").orElseThrow().modId());
        assertEquals(2, first.profile().providerArchives("shared/Utility.class").size());

        ClasspathIndexBuilder.Result repeated = ClasspathIndexBuilder.build(fixture.install(), cache);
        assertTrue(repeated.profileHit());
        assertEquals(0, repeated.archiveHits());
        assertEquals(0, repeated.archiveBuilds());
        assertEquals(first.profile().providers(), repeated.profile().providers());

        Files.delete(first.profilePath());
        ClasspathIndexBuilder.Result rebuiltProfile = ClasspathIndexBuilder.build(fixture.install(), cache);
        assertFalse(rebuiltProfile.profileHit());
        assertEquals(2, rebuiltProfile.archiveHits());
        assertEquals(0, rebuiltProfile.archiveBuilds());

        ClasspathProfileIndex.Archive campaign = rebuiltProfile.profile().archives().stream()
                .filter(archive -> archive.modId().equals("campaign"))
                .findFirst()
                .orElseThrow();
        Path campaignIndex = cache.resolve(campaign.archiveIndexRelativePath());
        Files.write(campaignIndex, new byte[] {1, 2, 3, 4});
        Files.delete(rebuiltProfile.profilePath());
        ClasspathIndexBuilder.Result repaired = ClasspathIndexBuilder.build(fixture.install(), cache);
        assertEquals(1, repaired.archiveHits());
        assertEquals(1, repaired.archiveBuilds());
        assertEquals(1, repaired.quarantinedIndexes());
        assertEquals(0, repaired.failedArchives(), repaired.diagnostics().toString());
        assertTrue(Files.list(cache.resolve("quarantine")).findAny().isPresent());

        String firstFingerprint = repaired.profile().profileFingerprint();
        fixture.enable(List.of("campaign", "library"));
        ClasspathIndexBuilder.Result reordered = ClasspathIndexBuilder.build(fixture.install(), cache);
        assertNotEquals(firstFingerprint, reordered.profile().profileFingerprint());
        assertEquals(2, reordered.archiveHits());
        assertEquals(0, reordered.archiveBuilds());
        assertEquals("library", reordered.profile().winner("shared/Utility.class").orElseThrow().modId());

        ClasspathIndexBuilder.Validation shallow = ClasspathIndexBuilder.validate(reordered.profile(), cache, false);
        ClasspathIndexBuilder.Validation deep = ClasspathIndexBuilder.validate(reordered.profile(), cache, true);
        assertTrue(shallow.valid(), shallow.problems().toString());
        assertTrue(deep.valid(), deep.problems().toString());

        Files.write(fixture.libraryJar(), new byte[] {9, 9, 9, 9, 9});
        ClasspathIndexBuilder.Validation invalid = ClasspathIndexBuilder.validate(reordered.profile(), cache, false);
        assertFalse(invalid.valid());
        assertTrue(invalid.problems().stream().anyMatch(problem -> problem.contains("size changed")));
    }

    private Fixture fixture() throws Exception {
        Path install = temporaryDirectory.resolve("Starsector");
        Path mods = install.resolve("mods");
        Path library = mods.resolve("Library");
        Path campaign = mods.resolve("Campaign");
        write(library.resolve("mod_info.json"), """
                {
                  "id":"library",
                  "jars":["jars/library.jar"]
                }
                """);
        write(campaign.resolve("mod_info.json"), """
                {
                  "id":"campaign",
                  "jars":["jars/campaign.jar"],
                  "dependencies":[{"id":"library","name":"Library"}]
                }
                """);
        Path libraryJar = library.resolve("jars/library.jar");
        jar(libraryJar, Map.of(
                "shared/Utility.class", new byte[] {1},
                "library/Only.class", new byte[] {2},
                "library/config.json", new byte[] {3}));
        jar(campaign.resolve("jars/campaign.jar"), Map.of(
                "shared/Utility.class", new byte[] {4},
                "campaign/Only.class", new byte[] {5}));
        Fixture fixture = new Fixture(install, libraryJar);
        fixture.enable(List.of("library", "campaign"));
        return fixture;
    }

    private static void jar(Path path, Map<String, byte[]> entries) throws Exception {
        Files.createDirectories(path.getParent());
        Map<String, byte[]> ordered = new LinkedHashMap<>();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                ordered.put(entry.getKey(), entry.getValue()));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
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

    private record Fixture(Path install, Path libraryJar) {
        void enable(List<String> ids) throws Exception {
            String joined = ids.stream().map(id -> "\"" + id + "\"")
                    .collect(java.util.stream.Collectors.joining(","));
            write(install.resolve("mods/enabled_mods.json"), "{\"enabledMods\":[" + joined + "]}");
        }
    }
}
