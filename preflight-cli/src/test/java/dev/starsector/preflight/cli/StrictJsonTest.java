package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictJsonTest {
    @Test
    void parsesNestedReportsAndEscapes() {
        Map<String, Object> value = StrictJson.object("""
                {"text":"line\\n\\u0041","number":42,"decimal":1.5,"flag":true,
                 "nothing":null,"items":["x",{"y":-2e3}]}
                """);
        assertEquals("line\nA", value.get("text"));
        assertEquals(42L, value.get("number"));
        assertEquals(1.5, value.get("decimal"));
        assertEquals(Boolean.TRUE, value.get("flag"));
        assertEquals(null, value.get("nothing"));
        assertTrue(value.get("items") instanceof List<?>);
    }

    @Test
    void rejectsDuplicateKeysMalformedNumbersAndTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{\"a\":null,\"a\":1}"));
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{\"a\":01}"));
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{\"a\":1e+-2}"));
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse("{} trailing"));
    }

    @Test
    void rejectsExcessiveNesting() {
        String nested = "[".repeat(130) + "0" + "]".repeat(130);
        assertThrows(IllegalArgumentException.class, () -> StrictJson.parse(nested));
    }
}
