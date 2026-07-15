package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageReadStackAttributionTest {
    @Test
    void repeatedShallowImageFramesOutrankUnrelatedAndDeepFrames() {
        ImageReadStackAttribution attribution = new ImageReadStackAttribution();
        List<ImageReadStackAttribution.Frame> textureStack = List.of(
                new ImageReadStackAttribution.Frame(
                        "java.nio.file.Files", "readAllBytes", "(Ljava/nio/file/Path;)[B", 0),
                new ImageReadStackAttribution.Frame(
                        "com.fs.starfarer.A", "a", "(Ljava/lang/String;)[B", 1),
                new ImageReadStackAttribution.Frame(
                        "com.fs.starfarer.Campaign", "advance", "(F)V", 6));
        List<ImageReadStackAttribution.Frame> otherStack = List.of(
                new ImageReadStackAttribution.Frame(
                        "com.fs.starfarer.Campaign", "advance", "(F)V", 1));

        for (int i = 0; i < 5; i++) {
            attribution.record("graphics/ships/example-" + i + ".png", 1_024, 2_000_000, textureStack);
        }
        attribution.record("graphics/portraits/other.jpg", 512, 1_000_000, otherStack);
        attribution.record("data/config/settings.json", 128, 1_000_000, textureStack);

        Map<String, Object> report = attribution.toMap();
        assertEquals(6L, report.get("imageReadEvents"));
        assertEquals(6L, report.get("eventsWithStack"));
        assertEquals(0L, report.get("eventsWithoutStack"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) report.get("topMethods");
        assertTrue(methods.size() >= 2, methods.toString());
        Map<String, Object> first = methods.get(0);
        assertEquals("com/fs/starfarer/A", first.get("className"));
        assertEquals("a", first.get("methodName"));
        assertEquals(5L, first.get("events"));
        assertEquals(1, first.get("minimumDepth"));
        assertTrue(((Long) first.get("behavioralScore")) > (Long) methods.get(1).get("behavioralScore"));
    }

    @Test
    void missingStacksAndMixedSeparatorsRemainNonfatal() {
        ImageReadStackAttribution attribution = new ImageReadStackAttribution();
        attribution.record("C:\\Games\\Starsector\\graphics\\ships\\one.PNG", 100, 50, List.of());
        attribution.record(null, 100, 50, List.of());
        attribution.record("graphics/ships/no-extension", 100, 50, List.of());

        Map<String, Object> report = attribution.toMap();
        assertEquals(1L, report.get("imageReadEvents"));
        assertEquals(1L, report.get("eventsWithoutStack"));
        assertEquals(List.of(), report.get("topMethods"));
    }
}
