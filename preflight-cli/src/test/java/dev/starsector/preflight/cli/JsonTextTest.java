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
                  "id": "alpha\u002dmod",
                  "enabledMods": ["alpha", "beta\npack",],
                }
                """;

        assertEquals("alpha-mod", JsonText.string(json, "id"));
        assertEquals(List.of("alpha", "beta\npack"), JsonText.stringArray(json, "enabledMods"));
    }

    @Test
    void readsOfficialStyleHashCommentsAndCommentedOutJarEntries() {
        String json = """
                {
                  "id": "library", #// inline explanatory comment
                  "jars": [
                    "jars/Library.jar",
                    #"jars/Optional-Kotlin.jar",
                    "jars/libs/helper.jar",
                  ],
                  # Entire fields are also commonly disabled this way
                  #"utility": "true",
                  "modPlugin": "fixture.Plugin"
                }
                """;

        assertEquals("library", JsonText.string(json, "id"));
        assertEquals(
                List.of("jars/Library.jar", "jars/libs/helper.jar"),
                JsonText.stringArray(json, "jars"));
        assertEquals("fixture.Plugin", JsonText.string(json, "modPlugin"));
    }
}
