package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunCommandFailureIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void launchFailureStillFinalizesRunMetadata() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        Assumptions.assumeFalse(windows, "direct missing-interpreter launch behavior is POSIX-specific");

        Path game = temporaryDirectory.resolve("Broken Synthetic Starsector");
        Files.createDirectories(game.resolve("logs"));
        Path launcher = game.resolve("starsector.sh");
        Files.writeString(launcher, "#!/definitely/missing/preflight-interpreter\nexit 0\n");
        assertTrue(launcher.toFile().setExecutable(true), "could not mark synthetic launcher executable");
        Path trace = temporaryDirectory.resolve("failed-trace");

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path jar = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), "missing packaged CLI: " + jar);
        Process process = new ProcessBuilder(List.of(
                        java.toString(),
                        "-jar", jar.toString(),
                        "run",
                        "--game", game.toString(),
                        "--launcher", launcher.toString(),
                        "--trace-dir", trace.toString(),
                        "--no-scan",
                        "--no-summary",
                        "--no-adapter"))
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(completed, output);
        assertEquals(1, process.exitValue(), output);
        Path metadata = trace.resolve("run.json");
        assertTrue(Files.isRegularFile(metadata), output);
        Map<String, Object> report = StrictJson.object(Files.readString(metadata));
        assertEquals(1L, report.get("exitCode"));
        assertEquals(null, report.get("launcherExitCode"));
        assertEquals("LAUNCH_FAILED", report.get("outcome"));
        assertNotNull(report.get("executionFailure"));
        assertNotNull(report.get("ended"));
    }
}
