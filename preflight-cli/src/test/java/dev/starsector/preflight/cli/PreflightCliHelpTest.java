package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PreflightCliHelpTest {
    @Test
    void benchmarkHelpIncludesScenarioRecorderAndComparison() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertEquals(0, PreflightCli.run(new String[] {"benchmark", "--help"}));
        } finally {
            System.setOut(original);
        }

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("preflight benchmark lookups"), output);
        assertTrue(output.contains("preflight benchmark scenario"), output);
        assertTrue(output.contains("preflight benchmark compare"), output);
    }
}
