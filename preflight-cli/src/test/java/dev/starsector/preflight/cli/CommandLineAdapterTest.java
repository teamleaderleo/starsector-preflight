package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.agent.AdapterMode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommandLineAdapterTest {
    @Test
    void defaultsOffAndParsesProbeOrEnabledModes() {
        assertEquals(AdapterMode.OFF, CommandLine.parse(new String[] {"run"}, 1).adapterMode());

        CommandLine probe = CommandLine.parse(
                new String[] {"run", "--adapter-probe", "--adapter-targets", "targets.txt"}, 1);
        assertEquals(AdapterMode.PROBE, probe.adapterMode());
        assertEquals(Path.of("targets.txt"), probe.adapterTargets());

        CommandLine enabled = CommandLine.parse(new String[] {"run", "--adapter"}, 1);
        assertEquals(AdapterMode.ENABLED, enabled.adapterMode());
    }

    @Test
    void parsesOnlyCompleteEnabledPreparedImageContext() {
        CommandLine enabled = CommandLine.parse(new String[] {
                "run",
                "--adapter",
                "--adapter-cache-dir", "cache",
                "--adapter-texture-manifest", "manifest.spfm",
                "--adapter-resource-index", "index.spfi"
        }, 1);

        assertTrue(enabled.hasAdapterTextureContext());
        assertEquals(Path.of("cache"), enabled.adapterCacheDirectory());
        assertEquals(Path.of("manifest.spfm"), enabled.adapterTextureManifest());
        assertEquals(Path.of("index.spfi"), enabled.adapterResourceIndex());
    }

    @Test
    void rejectsConflictingModesTargetsWhileOffAndPartialTextureContext() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {"run", "--adapter", "--adapter-probe"}, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {"run", "--adapter-targets", "targets.txt"}, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {
                        "run", "--adapter", "--adapter-cache-dir", "cache"
                }, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {
                        "run",
                        "--adapter-probe",
                        "--adapter-cache-dir", "cache",
                        "--adapter-texture-manifest", "manifest.spfm",
                        "--adapter-resource-index", "index.spfi"
                }, 1));
    }
}
