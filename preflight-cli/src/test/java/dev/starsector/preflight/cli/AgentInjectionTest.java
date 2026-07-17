package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.agent.AdapterMode;
import dev.starsector.preflight.agent.TextureAdapterMode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentInjectionTest {
    @Test
    void preservesExistingOptionsAndAppendsOneAgent() {
        String value = AgentInjection.append(
                "-Xmx4g -Dexample=true",
                Path.of("preflight.jar"),
                Path.of("startup.jfr"));

        assertTrue(value.startsWith("-Xmx4g -Dexample=true "));
        assertEquals(1, occurrences(value, "-javaagent:"));
        assertTrue(value.contains("dest64="));
        assertTrue(value.contains("adapter=off"));
        assertTrue(value.contains("textureMode=compatibility"));
    }

    @Test
    void includesProbeReportAndTargetPaths() {
        String value = AgentInjection.append(
                "",
                Path.of("preflight.jar"),
                Path.of("startup.jfr"),
                AdapterMode.PROBE,
                Path.of("adapter reports", "probe.json"),
                Path.of("adapter targets", "vanilla.txt"));

        assertTrue(value.contains("adapter=probe"));
        assertTrue(value.contains("adapterReport64="));
        assertTrue(value.contains("targets64="));
        assertEquals(1, occurrences(value, "-javaagent:"));
    }

    @Test
    void includesPreparedPixelModeAndTexturePaths() {
        String value = AgentInjection.append(
                "",
                Path.of("preflight.jar"),
                Path.of("startup.jfr"),
                AdapterMode.ENABLED,
                Path.of("adapter.json"),
                null,
                Path.of("cache"),
                Path.of("cache", "manifests", "profile.spfm"),
                Path.of("cache", "indexes", "profile.spfi"),
                TextureAdapterMode.PREPARED_PIXELS);

        assertTrue(value.contains("adapter=enabled"));
        assertTrue(value.contains("textureMode=prepared-pixels"));
        assertTrue(value.contains("textureCache64="));
        assertTrue(value.contains("textureManifest64="));
        assertTrue(value.contains("textureIndex64="));
    }

    @Test
    void quotesAgentPathsContainingSpaces() {
        Path agent = Path.of("preflight build", "preflight.jar").toAbsolutePath().normalize();
        String value = AgentInjection.append(
                "",
                agent,
                Path.of("startup trace.jfr"));

        assertTrue(value.startsWith("-javaagent:\"" + agent + "\"="));
    }

    @Test
    void rejectsDuplicatePreflightAgent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentInjection.append(
                        "-javaagent:preflight.jar",
                        Path.of("preflight.jar"),
                        Path.of("startup.jfr")));
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
