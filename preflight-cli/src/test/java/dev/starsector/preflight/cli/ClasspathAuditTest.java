package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathAuditTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void auditsNestedLibrariesDependenciesDuplicatesAndMalformedArchives() throws Exception {
        Path install = fixture();

        ClasspathAudit.Result first = ClasspathAudit.scan(install);
        Map<String, Object> values = first.values();
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) values.get("totals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicateSamples =
                (List<Map<String, Object>>) values.get("duplicateClassSamples");

        assertEquals(List.of("campaign", "lunalib", "lw_lazylib"), values.get("enabledModIds"));
        assertEquals(5L, totals.get("jars"));
        assertEquals(4L, totals.get("validJars"));
        assertEquals(1L, totals.get("malformedJars"));
        assertEquals(1L, totals.get("declaredMissingJars"));
        assertEquals(2L, totals.get("undeclaredJars"));
        assertEquals(1L, totals.get("missingDependencies"));
        assertEquals(2L, totals.get("dependencyOrderProblems"));
        assertEquals(3L, totals.get("duplicateClasses"));
        assertEquals(
                "lw_lazylib:jars/internal/LazyLib.jar",
                duplicate(duplicateSamples, "shared.Utility").get("probableWinner"));
        assertTrue(first.toJson().contains("MagicLib"));

        String unchanged = ClasspathAudit.scan(install).toJson();
        assertEquals(first.toJson(), unchanged);

        String archiveFingerprint = (String) values.get("archiveFingerprint");
        String classpathFingerprint = (String) values.get("classpathFingerprint");
        enable(install, List.of("lw_lazylib", "lunalib", "campaign"));
        ClasspathAudit.Result reordered = ClasspathAudit.scan(install);
        @SuppressWarnings("unchecked")
        Map<String, Object> reorderedTotals = (Map<String, Object>) reordered.values().get("totals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reorderedDuplicates =
                (List<Map<String, Object>>) reordered.values().get("duplicateClassSamples");

        assertEquals(archiveFingerprint, reordered.values().get("archiveFingerprint"));
        assertNotEquals(classpathFingerprint, reordered.values().get("classpathFingerprint"));
        assertEquals(0L, reorderedTotals.get("dependencyOrderProblems"));
        assertEquals(
                "campaign:jars/campaign.jar",
                duplicate(reorderedDuplicates, "shared.Utility").get("probableWinner"));
    }

    private Path fixture() throws Exception {
        Path install = temporaryDirectory.resolve("Starsector");
        Path mods = install.resolve("mods");
        Files.createDirectories(mods);

        Path lazy = mods.resolve("LazyLib");
        metadata(lazy, """
                {
                  "id":"lw_lazylib", #// real metadata often uses hash comments
                  "jars":[
                    "jars/internal/LazyLib.jar",
                  ]
                }
                """);
        jar(lazy.resolve("jars/internal/LazyLib.jar"), Map.of(
                "shared/Utility.class", new byte[] {1},
                "shared/Config.class", new byte[] {2},
                "lazy/Only.class", new byte[] {3},
                "META-INF/services/example.Service", new byte[] {4}));

        Path luna = mods.resolve("LunaLib");
        metadata(luna, """
                {
                  "id":"lunalib",
                  "jars":[
                    "jars/LunaLib.jar",
                    #"jars/LunaLib-Kotlin.jar",
                  ],
                  "dependencies":[{"id":"lw_lazylib","name":"LazyLib"}]
                }
                """);
        jar(luna.resolve("jars/LunaLib.jar"), Map.of(
                "shared/Utility.class", new byte[] {5},
                "shared/Config.class", new byte[] {6},
                "luna/Only.class", new byte[] {7}));

        Path campaign = mods.resolve("Campaign");
        metadata(campaign, """
                {
                  "id":"campaign",
                  "jars":["jars/campaign.jar","jars/missing.jar"],
                  "dependencies":[
                    {"id":"lunalib","name":"LunaLib"},
                    {"id":"MagicLib","name":"MagicLib"},
                  ],
                }
                """);
        jar(campaign.resolve("jars/campaign.jar"), Map.of(
                "shared/Utility.class", new byte[] {8},
                "shared/Config.class", new byte[] {9},
                "shared/Third.class", new byte[] {10},
                "campaign/Only.class", new byte[] {11}));
        jar(campaign.resolve("jars/extra.jar"), Map.of(
                "shared/Third.class", new byte[] {12},
                "campaign/Extra.class", new byte[] {13}));
        write(campaign.resolve("jars/broken.jar"), "not a zip archive");

        enable(install, List.of("campaign", "lunalib", "lw_lazylib"));
        return install;
    }

    private static Map<String, Object> duplicate(List<Map<String, Object>> samples, String className) {
        return samples.stream()
                .filter(sample -> className.equals(sample.get("className")))
                .findFirst()
                .orElseThrow();
    }

    private static void enable(Path install, List<String> ids) throws Exception {
        String joined = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        write(install.resolve("mods/enabled_mods.json"), "{\"enabledMods\":[" + joined + "]}");
    }

    private static void metadata(Path mod, String value) throws Exception {
        write(mod.resolve("mod_info.json"), value);
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
}
