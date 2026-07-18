package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

    private static Path fakeLauncher(Path game, boolean fatal) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String line = fatal
                ? "328814 [Thread-3] ERROR com.fs.starfarer.combat.CombatMain  - java.lang.NullPointerException: synthetic fatal"
                : "100 [Thread-3] INFO com.fs.starfarer.combat.CombatMain  - synthetic clean exit";
        String frame = "at com.fs.starfarer.combat.CombatMain.main(Unknown Source)";
        Path launcher = game.resolve(windows ? "starsector.bat" : "starsector.sh");
        if (windows) {
            Files.writeString(launcher, "@echo off\r\n"
                    + "> \"%~dp0logs\\starsector.log\" echo " + line + "\r\n"
                    + (fatal ? ">> \"%~dp0logs\\starsector.log\" echo     " + frame + "\r\n" : "")
                    + "exit /b 0\r\n");
        } else {
            Files.writeString(launcher, "#!/bin/sh\n"
                    + "printf '%s\\n' '" + line + "' > logs/starsector.log\n"
                    + (fatal ? "printf '%s\\n' '    " + frame + "' >> logs/starsector.log\n" : "")
                    + "exit 0\n");
        }
        return launcher;
    }

    private static ProcessResult run(Path game, Path launcher, Path trace) throws Exception {
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
                "--no-summary",
                "--no-adapter"));
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

    private record ProcessResult(boolean completed, int exitCode, String output) {
    }
}
