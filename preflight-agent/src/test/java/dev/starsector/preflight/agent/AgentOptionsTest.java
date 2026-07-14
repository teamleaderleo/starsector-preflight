package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentOptionsTest {
    @Test
    void parsesDestinationAndSettings() {
        AgentOptions options = AgentOptions.parse("dest=build/startup.jfr,settings=default");
        assertEquals(Path.of("build/startup.jfr"), options.destination());
        assertEquals("default", options.settings());
    }

    @Test
    void rejectsMalformedOptions() {
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("dest"));
    }
}
