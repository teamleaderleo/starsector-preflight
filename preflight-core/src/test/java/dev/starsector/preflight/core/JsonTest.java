package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void serializesNestedCollectionsAndScalarLikeTypes() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("path", Path.of("mods", "alpha"));
        values.put("time", Instant.parse("2026-07-14T00:00:00Z"));
        values.put("items", List.of("one", Map.of("two", 2)));
        values.put("flags", new boolean[] {true, false});

        assertEquals(
                "{\"path\":\"mods/alpha\",\"time\":\"2026-07-14T00:00:00Z\",\"items\":[\"one\",{\"two\":2}],\"flags\":[true,false]}",
                Json.object(values).replace('\\', '/'));
    }
}
