package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTextTest {
    @Test
    void readsStringsArraysCommentsEscapesAndTrailingComma() {
        String json = """
                {
                  // Starsector metadata often allows comments
                  "id": "alpha\\u002dmod",
                  "enabledMods": ["alpha", "beta\\npack",],
                }
                """;

        assertEquals("alpha-mod", JsonText.string(json, "id"));
        assertEquals(List.of("alpha", "beta\npack"), JsonText.stringArray(json, "enabledMods"));
    }
}
