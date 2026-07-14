package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileCensusTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void inventoriesEnabledModsAndTracksProbableOverrideWinner() throws Exception {
        Path mods = temporaryDirectory.resolve("mods");
        Path alpha = mods.resolve("Alpha");
        Path beta = mods.resolve("Beta");
        Files.createDirectories(alpha.resolve("graphics"));
        Files.createDirectories(alpha.resolve("src"));
        Files.createDirectories(beta.resolve("graphics"));
        Files.createDirectories(beta.resolve("sounds"));
        Files.createDirectories(beta.resolve("jars"));
        Files.writeString(alpha.resolve("mod_info.json"), "{\"id\":\"alpha\"}");
        Files.writeString(beta.resolve("mod_info.json"), "{\"id\":\"beta\"}");
        Files.writeString(alpha.resolve("graphics/shared.png"), "alpha");
        Files.writeString(beta.resolve("graphics/shared.png"), "beta image");
        Files.writeString(alpha.resolve("src/Example.java"), "class Example {}");
        Files.writeString(beta.resolve("sounds/hit.ogg"), "sound");
        Files.writeString(beta.resolve("jars/beta.jar"), "jar");
        Path enabled = mods.resolve("enabled_mods.json");
        Files.writeString(enabled, "{\"enabledMods\":[\"alpha\",\"beta\",\"missing\"]}");

        ProfileCensus.Result first = ProfileCensus.scan(temporaryDirectory);
        Map<String, Object> values = first.values();
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) values.get("totals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicates = (List<Map<String, Object>>) values.get("duplicateSamples");

        assertEquals(List.of("alpha", "beta", "missing"), values.get("enabledModIds"));
        assertEquals(List.of("missing"), values.get("missingModIds"));
        assertEquals(2L, totals.get("imageFiles"));
        assertEquals(1L, totals.get("looseJavaFiles"));
        assertEquals(1L, totals.get("jarFiles"));
        assertEquals(1L, values.get("duplicateLogicalPaths"));
        assertEquals("beta", duplicates.get(0).get("probableWinner"));
        assertTrue(first.toJson().contains("\"profileFingerprint\""));

        Files.writeString(enabled, "{\"enabledMods\":[\"beta\",\"alpha\"]}");
        ProfileCensus.Result reordered = ProfileCensus.scan(temporaryDirectory);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reorderedDuplicates =
                (List<Map<String, Object>>) reordered.values().get("duplicateSamples");

        assertNotEquals(values.get("profileFingerprint"), reordered.values().get("profileFingerprint"));
        assertEquals("alpha", reorderedDuplicates.get(0).get("probableWinner"));
    }
}
