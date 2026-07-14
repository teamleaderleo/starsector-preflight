package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AgentOptionsTest {
    @Test
    void parsesDestinationAndSettings() {
        AgentOptions options = AgentOptions.parse("dest=build/startup.jfr,settings=default");
        assertEquals(Path.of("build/startup.jfr"), options.destination());
        assertEquals("default", options.settings());
    }

    @Test
    void parsesEncodedDestinationContainingSpacesAndCommas() {
        String destination = "build/trace directory/startup,one.jfr";
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(destination.getBytes(StandardCharsets.UTF_8));

        AgentOptions options = AgentOptions.parse("dest64=" + encoded);

        assertEquals(Path.of(destination), options.destination());
    }

    @Test
    void rejectsMalformedOptions() {
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("dest"));
    }
}
