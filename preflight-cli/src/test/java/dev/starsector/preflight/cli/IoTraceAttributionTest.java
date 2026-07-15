package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IoTraceAttributionTest {
    @Test
    void aggregatesPathsExtensionsCategoriesAndMixedSeparators() {
        IoTraceAttribution attribution = new IoTraceAttribution();
        attribution.recordRead("C:\\Games\\Starsector\\mods\\Example Mod\\graphics\\ship.PNG", 100, 2_000_000);
        attribution.recordRead("C:/Games//Starsector/mods/Example Mod/graphics/ship.PNG", 150, 3_000_000);
        attribution.recordRead("/cache/blobs/example.spft", 80, 1_000_000);
        attribution.recordRead("/mods/library/jars/library.JAR", 50, 4_000_000);
        attribution.recordRead(null, -1, -1);
        attribution.recordWrite("/cache/manifests/profile.SPFM", 30, 500_000);

        Map<String, Object> values = attribution.toMap();
        Map<String, Object> ship = find(values, "topReadPaths", "path", "C:/Games/Starsector/mods/Example Mod/graphics/ship.PNG");
        assertEquals(2L, ship.get("operations"));
        assertEquals(250L, ship.get("bytes"));
        assertEquals(5.0, ship.get("durationMs"));

        Map<String, Object> png = find(values, "readExtensions", "extension", "png");
        assertEquals(2L, png.get("operations"));
        assertEquals(250L, png.get("bytes"));
        Map<String, Object> cache = find(values, "readCategories", "category", "preflight-cache");
        assertEquals(80L, cache.get("bytes"));
        Map<String, Object> archive = find(values, "readCategories", "category", "archive");
        assertEquals(50L, archive.get("bytes"));
        Map<String, Object> unknown = find(values, "readCategories", "category", "unknown");
        assertEquals(1L, unknown.get("operations"));
        Map<String, Object> writes = find(values, "writeExtensions", "extension", "spfm");
        assertEquals(30L, writes.get("bytes"));
    }

    @Test
    void sortsTiesDeterministicallyAndBoundsTopPaths() {
        IoTraceAttribution attribution = new IoTraceAttribution();
        for (int i = 30; i >= 0; i--) {
            attribution.recordRead("/same/path-" + String.format("%02d", i) + ".json", 10, 100);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paths = (List<Map<String, Object>>) attribution.toMap().get("topReadPaths");
        assertEquals(25, paths.size());
        assertEquals("/same/path-00.json", paths.get(0).get("path"));
        assertEquals("/same/path-24.json", paths.get(24).get("path"));
    }

    @Test
    void classifiesCommonStarsectorAndCacheExtensions() {
        assertEquals("image", IoTraceAttribution.category("png"));
        assertEquals("sound", IoTraceAttribution.category("ogg"));
        assertEquals("data", IoTraceAttribution.category("variant"));
        assertEquals("code", IoTraceAttribution.category("class"));
        assertEquals("preflight-cache", IoTraceAttribution.category("spfc"));
        assertEquals("<none>", IoTraceAttribution.extension("/tmp/README"));
        assertEquals("<unknown>", IoTraceAttribution.normalizePath("  "));
        assertTrue(IoTraceAttribution.normalizePath("/a///b").endsWith("/a/b"));
    }

    private static Map<String, Object> find(
            Map<String, Object> values,
            String listKey,
            String itemKey,
            String expected) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) values.get(listKey);
        return items.stream()
                .filter(item -> expected.equals(item.get(itemKey)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + expected + " in " + listKey + ": " + items));
    }
}
