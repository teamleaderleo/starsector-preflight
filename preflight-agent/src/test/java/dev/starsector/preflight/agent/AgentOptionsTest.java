package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AgentOptionsTest {
    @Test
    void parsesDestinationSettingsAndSafeAdapterDefaults() {
        AgentOptions options = AgentOptions.parse("dest=build/startup.jfr,settings=default");
        assertEquals(Path.of("build/startup.jfr"), options.destination());
        assertEquals("default", options.settings());
        assertEquals(AdapterMode.OFF, options.adapterMode());
        assertEquals(Path.of("build/adapter.json"), options.adapterReport());
        assertNull(options.adapterTargets());
    }

    @Test
    void parsesEncodedDestinationContainingSpacesAndCommas() {
        String destination = "build/trace directory/startup,one.jfr";
        String encoded = encoded(destination);

        AgentOptions options = AgentOptions.parse("dest64=" + encoded);

        assertEquals(Path.of(destination), options.destination());
    }

    @Test
    void parsesProbeReportAndTargets() {
        String report = "build/adapter reports/probe.json";
        String targets = "build/targets/vanilla.txt";
        AgentOptions options = AgentOptions.parse(
                "adapter=probe,adapterReport64=" + encoded(report) + ",targets64=" + encoded(targets));

        assertEquals(AdapterMode.PROBE, options.adapterMode());
        assertEquals(Path.of(report), options.adapterReport());
        assertEquals(Path.of(targets), options.adapterTargets());
        assertEquals("com/fs/starfarer/", options.candidatePrefixes().get(0));
    }

    @Test
    void rejectsMalformedAndDuplicateOptions() {
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("dest"));
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("adapter=probe,adapter=enabled"));
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("adapter=maybe"));
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}