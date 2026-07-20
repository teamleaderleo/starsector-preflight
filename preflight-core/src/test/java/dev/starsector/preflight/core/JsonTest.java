package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void escapesControlAndLineSeparatorCharacters() {
        assertEquals("\"a\\u0000b\\u2028c\\u2029d\\ne\"", Json.quote("a\u0000b\u2028c\u2029d\ne"));
    }

    @Test
    void rejectsNonStringObjectKeys() {
        assertThrows(IllegalArgumentException.class, () -> Json.value(Map.of(1, "one")));
    }

    @Test
    void serializesNestedCollectionsAndScalarLikeTypes() {
        Path path = Path.of("mods", "alpha");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("path", path);
        values.put("time", Instant.parse("2026-07-14T00:00:00Z"));
        values.put("items", List.of("one", Map.of("two", 2)));
        values.put("flags", new boolean[] {true, false});

        assertEquals(
                "{\"path\":" + Json.quote(path.toString())
                        + ",\"time\":\"2026-07-14T00:00:00Z\",\"items\":[\"one\",{\"two\":2}],\"flags\":[true,false]}",
                Json.object(values));
    }
}
