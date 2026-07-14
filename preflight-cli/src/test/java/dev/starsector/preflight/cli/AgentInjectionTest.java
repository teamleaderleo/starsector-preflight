package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentInjectionTest {
    @Test
    void preservesExistingOptionsAndAppendsOneAgent() {
        String value = AgentInjection.append(
                "-Xmx4g -Dexample=true",
                Path.of("/tmp/preflight.jar"),
                Path.of("/tmp/startup.jfr"));

        assertTrue(value.startsWith("-Xmx4g -Dexample=true "));
        assertEquals(1, occurrences(value, "-javaagent:"));
        assertTrue(value.contains("dest64="));
    }

    @Test
    void quotesAgentPathsContainingSpaces() {
        String value = AgentInjection.append(
                "",
                Path.of("/tmp/preflight build/preflight.jar"),
                Path.of("/tmp/startup trace.jfr"));

        assertTrue(value.startsWith("-javaagent:\"/tmp/preflight build/preflight.jar\"="));
    }

    @Test
    void rejectsDuplicatePreflightAgent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentInjection.append(
                        "-javaagent:/tmp/preflight.jar",
                        Path.of("/tmp/preflight.jar"),
                        Path.of("/tmp/startup.jfr")));
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
