package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonObjectArraysTest {
    @Test
    void readsDependencyIdsAcrossCommentsAndTrailingCommas() {
        String metadata = """
                {
                  "dependencies": [
                    {"id":"lw_lazylib","name":"LazyLib"},
                    # Optional compatibility entry disabled by the author
                    # {"id":"optional","name":"Optional"},
                    /* block comment */
                    {"id":"MagicLib","name":"MagicLib"},
                  ],
                  "other": [{"id":"ignored"}]
                }
                """;

        assertEquals(
                List.of("lw_lazylib", "MagicLib"),
                JsonObjectArrays.stringFields(metadata, "dependencies", "id"));
        assertEquals(List.of("ignored"), JsonObjectArrays.stringFields(metadata, "other", "id"));
        assertEquals(List.of(), JsonObjectArrays.stringFields(metadata, "missing", "id"));
    }
}
