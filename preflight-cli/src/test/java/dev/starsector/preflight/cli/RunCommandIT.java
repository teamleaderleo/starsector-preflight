package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunCommandIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fatalGameLogOverridesZeroLauncherExitAndIsReported() throws Exception {
        Path game = temporaryDirectory.resolve("Synthetic Starsector");
        Files.createDirectories(game.resolve("logs"));
        Path launcher = fakeLauncher(game, true);
        Path trace = temporaryDirectory.resolve("fatal-trace");

        ProcessResult result = run(game, launcher, trace);

        assertTrue(result.completed(), result.output());
        assertEquals(StarsectorRunLogEvidence.FATAL_LIFECYCLE_EXIT, result.exitCode(), result.output());
        Map<String, Object> report = StrictJson.object(Files.readString(trace.resolve("run.json")));
        assertEquals((long) StarsectorRunLogEvidence.FATAL_LIFECYCLE_EXIT, report.get("exitCode"));
        assertEquals(0L, report.get("launcherExitCode"));
        assertEquals("FATAL_LOG_EVIDENCE", report.get("outcome"));
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) report.get("lifecycleEvidence");
        assertNotNull(evidence);
        assertEquals(Boolean.TRUE, evidence.get("fatalDetected"));
        assertEquals("OFF", report.get("adapterMode"));
        assertEquals(null, report.get("textureCacheDirectory"));
        assertTrue(result.output().contains("is not a clean game exit"), result.output());
    }

    @Test
    void cleanZeroLauncherExitRemainsSuccessful() throws Exception {
        Path game = temporaryDirectory.resolve("Clean Synthetic Starsector");
        Files.createDirectories(game.resolve("logs"));
        Path launcher = fakeLauncher(game, false);
        Path trace = temporaryDirectory.resolve("clean-trace");

        ProcessResult result = run(game, launcher, trace);

        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
        Map<String, Object> report = StrictJson.object(Files.readString(trace.resolve("run.json")));
        assertEquals(0L, report.get("exitCode"));
        assertEquals(0L, report.get("launcherExitCode"));
        assertEquals("COMPLETED", report.get("outcome"));
    }

    @Test
    void automaticTextureLaunchPassesExactCurrentArtifactsToTheRealLauncher() throws Exception {
        Path game = temporaryDirectory.resolve("Auto Texture Starsector");
        Path source = game.resolve("starsector-core/graphics/test.png");
        Path mods = game.resolve("mods");
        Files.createDirectories(source.getParent());
        Files.createDirectories(mods);
        Files.writeString(source, "texture");
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[]}");
        Path launcher = fakeLauncher(game, false);

        var current = ResourceIndexBuilder.build(game).index();
        Path cache = temporaryDirectory.resolve("auto-texture-cache");
        Path index = cache.resolve("resource-indexes/" + current.profileFingerprint() + ".spfi");
        Path manifest = cache.resolve("manifests/" + current.profileFingerprint() + ".spfm");
        dev.starsector.preflight.core.ResourceIndexIO.write(index, current);
        dev.starsector.preflight.core.TextureManifestIO.write(
                manifest,
                new dev.starsector.preflight.core.TextureManifest(current.profileFingerprint(), Map.of()));
        Path trace = temporaryDirectory.resolve("auto-texture-trace");

        ProcessResult result = run(game, launcher, trace, List.of(
                "--adapter", "--texture-auto", "--texture-cache-dir", cache.toString()));

        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
        Map<String, Object> report = StrictJson.object(Files.readString(trace.resolve("run.json")));
        assertEquals(Boolean.TRUE, report.get("textureAuto"));
        assertEquals(current.profileFingerprint(), report.get("textureProfileFingerprint"));
        assertEquals(cache.toRealPath().toString(), report.get("textureCacheDirectory"));
        assertEquals(manifest.toRealPath().toString(), report.get("textureManifest"));
        assertEquals(index.toRealPath().toString(), report.get("textureIndex"));
        assertEquals("ENABLED", report.get("adapterMode"));
        String injected = Files.readString(game.resolve("java-tool-options.txt"));
        assertTrue(injected.contains("textureCache64=" + encoded(cache.toRealPath())), injected);
        assertTrue(injected.contains("textureManifest64=" + encoded(manifest.toRealPath())), injected);
        assertTrue(injected.contains("textureIndex64=" + encoded(index.toRealPath())), injected);
        assertTrue(injected.contains("textureMode=compatibility"), injected);
        assertFalse(injected.contains("prepared-pixels"), injected);
    }

    private static Path fakeLauncher(Path game, boolean fatal) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String line = fatal
                ? "328814 [Thread-3] ERROR com.fs.starfarer.combat.CombatMain  - java.lang.NullPointerException: synthetic fatal"
                : "100 [Thread-3] INFO com.fs.starfarer.combat.CombatMain  - synthetic clean exit";
        String frame = "at com.fs.starfarer.combat.CombatMain.main(Unknown Source)";
        Path launcher = game.resolve(windows ? "starsector.bat" : "starsector.sh");
        if (windows) {
            Files.writeString(launcher, "@echo off\r\n"
                    + "> \"%~dp0java-tool-options.txt\" echo %JAVA_TOOL_OPTIONS%\r\n"
                    + "> \"%~dp0logs\\starsector.log\" echo " + line + "\r\n"
                    + (fatal ? ">> \"%~dp0logs\\starsector.log\" echo     " + frame + "\r\n" : "")
                    + "exit /b 0\r\n");
        } else {
            Files.writeString(launcher, "#!/bin/sh\n"
                    + "printf '%s\\n' \"$JAVA_TOOL_OPTIONS\" > java-tool-options.txt\n"
                    + "printf '%s\\n' '" + line + "' > logs/starsector.log\n"
                    + (fatal ? "printf '%s\\n' '    " + frame + "' >> logs/starsector.log\n" : "")
                    + "exit 0\n");
        }
        return launcher;
    }

    private static ProcessResult run(Path game, Path launcher, Path trace) throws Exception {
        return run(game, launcher, trace, List.of("--no-adapter"));
    }

    private static ProcessResult run(Path game, Path launcher, Path trace, List<String> adapterArguments)
            throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        Path jar = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), "missing packaged CLI: " + jar);
        List<String> command = new ArrayList<>(List.of(
                java.toString(),
                "-jar", jar.toString(),
                "run",
                "--game", game.toString(),
                "--launcher", launcher.toString(),
                "--trace-dir", trace.toString(),
                "--no-scan",
                "--no-summary"));
        command.addAll(adapterArguments);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(completed, process.exitValue(), output);
    }

    private static String encoded(Path path) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record ProcessResult(boolean completed, int exitCode, String output) {
    }
}
