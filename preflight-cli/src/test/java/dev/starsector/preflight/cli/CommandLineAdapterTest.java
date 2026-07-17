package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        CommandLine enabled = CommandLine.parse(new String[] {
                "run",
                "--adapter",
                "--texture-cache-dir", "cache",
                "--texture-manifest", "cache/manifests/profile.spfm",
                "--texture-index", "cache/indexes/profile.spfi"
        }, 1);
        assertEquals(AdapterMode.ENABLED, enabled.adapterMode());
        assertEquals(Path.of("cache"), enabled.textureCacheDirectory());
        assertEquals(Path.of("cache/manifests/profile.spfm"), enabled.textureManifest());
        assertEquals(Path.of("cache/indexes/profile.spfi"), enabled.textureIndex());
    }

    @Test
    void rejectsConflictingModesAndTargetsWhileOff() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {"run", "--adapter", "--adapter-probe"}, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {"run", "--adapter-targets", "targets.txt"}, 1));
    }

    @Test
    void rejectsPartialTextureConfigurationAndProbeMode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {
                        "run", "--adapter", "--texture-cache-dir", "cache"
                }, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandLine.parse(new String[] {
                        "run",
                        "--adapter-probe",
                        "--texture-cache-dir", "cache",
                        "--texture-manifest", "cache/manifests/profile.spfm",
                        "--texture-index", "cache/indexes/profile.spfi"
                }, 1));
    }
}
