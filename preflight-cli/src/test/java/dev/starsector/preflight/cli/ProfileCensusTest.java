package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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

    @Test
    @SuppressWarnings("unchecked")
    void breaksDownDecodedVramPerModFromExactImageDimensions() throws Exception {
        Path mods = temporaryDirectory.resolve("mods");
        Path big = mods.resolve("Big");
        Path small = mods.resolve("Small");
        Files.createDirectories(big.resolve("graphics"));
        Files.createDirectories(small.resolve("graphics"));
        Files.writeString(big.resolve("mod_info.json"), "{\"id\":\"big\"}");
        Files.writeString(small.resolve("mod_info.json"), "{\"id\":\"small\"}");
        // Big ships a 100x100 RGBA image (100*100*4 = 40000 decoded bytes).
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB), "png",
                big.resolve("graphics/hull.png").toFile());
        // Small ships a 10x10 RGB image (10*10*3 = 300 decoded bytes) plus one unmeasurable "image".
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png",
                small.resolve("graphics/icon.png").toFile());
        Files.writeString(small.resolve("graphics/broken.png"), "not really a png");
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[\"big\",\"small\"]}");

        Map<String, Object> values = ProfileCensus.scan(temporaryDirectory).values();
        Map<String, Object> totals = (Map<String, Object>) values.get("totals");
        assertEquals(40000L + 300L, totals.get("decodedImageBytes"));
        assertEquals(2L, totals.get("measuredImageFiles"));
        assertEquals(1L, totals.get("unmeasuredImageFiles"), "the fake PNG is counted, not guessed");

        // Decoded ranking puts Big first even though both are tiny on disk.
        List<Map<String, Object>> largestDecoded = (List<Map<String, Object>>) values.get("largestDecodedMods");
        assertEquals("big", largestDecoded.get(0).get("id"));
        assertEquals(40000L, largestDecoded.get(0).get("decodedImageBytes"));

        Map<String, Object> workingSet = (Map<String, Object>) values.get("decodedWorkingSet");
        assertEquals(40000L + 300L, workingSet.get("decodedImageBytes"));
    }
}
