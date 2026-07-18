package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstalledJorbisEquivalenceCommandTest {
    @Test
    void parsesExactDecoderJarPathsAndOptionalReport() {
        InstalledJorbisEquivalenceCommand.Options options = InstalledJorbisEquivalenceCommand.Options.parse(
                new String[] {
                    "audio",
                    "jorbis-equivalence",
                    "--jogg", "lib/jogg-0.0.7.jar",
                    "--jorbis", "lib/jorbis-0.0.15.jar",
                    "--output", "build/jorbis-equivalence.json"
                },
                2);

        assertEquals(Path.of("lib/jogg-0.0.7.jar"), options.jogg());
        assertEquals(Path.of("lib/jorbis-0.0.15.jar"), options.jorbis());
        assertEquals(Path.of("build/jorbis-equivalence.json"), options.output());
    }

    @Test
    void outputIsOptionalButBothDecoderJarsAreRequired() {
        InstalledJorbisEquivalenceCommand.Options options = InstalledJorbisEquivalenceCommand.Options.parse(
                new String[] {"--jogg", "jogg.jar", "--jorbis", "jorbis.jar"},
                0);
        assertNull(options.output());

        assertThrows(
                IllegalArgumentException.class,
                () -> InstalledJorbisEquivalenceCommand.Options.parse(
                        new String[] {"--jogg", "jogg.jar"}, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> InstalledJorbisEquivalenceCommand.Options.parse(
                        new String[] {"--jogg", "jogg.jar", "--jorbis", "jorbis.jar", "--extra"}, 0));
    }

    @Test
    void childAcceptsOnlyFullOrCiFixtureProfiles() {
        Path output = Path.of("report.json");
        InstalledJorbisEquivalenceChild.Options full = new InstalledJorbisEquivalenceChild.Options(
                "11".repeat(32), "22".repeat(32), "full", output);
        InstalledJorbisEquivalenceChild.Options ci = new InstalledJorbisEquivalenceChild.Options(
                "11".repeat(32), "22".repeat(32), "ci", output);

        assertEquals("full", full.fixtureProfile());
        assertEquals("ci", ci.fixtureProfile());
        assertThrows(
                IllegalArgumentException.class,
                () -> new InstalledJorbisEquivalenceChild.Options(
                        "11".repeat(32), "22".repeat(32), "partial", output));
    }
}
