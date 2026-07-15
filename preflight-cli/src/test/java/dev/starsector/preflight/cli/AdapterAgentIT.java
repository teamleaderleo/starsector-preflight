package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterAgentIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedAgentProbesCandidateAndPreservesOriginalClass() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        Path agent = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path recording = temporaryDirectory.resolve("startup.jfr");
        Path adapterReport = temporaryDirectory.resolve("adapter.json");
        String agentArguments = "dest64=" + encoded(recording)
                + ",adapter=probe,adapterReport64=" + encoded(adapterReport);

        Process process = new ProcessBuilder(List.of(
                java.toString(),
                "-javaagent:" + agent + "=" + agentArguments,
                "-cp",
                testClasses.toString(),
                "com.fs.starfarer.SyntheticLauncher"))
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(completed, output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("synthetic-starsector-launcher"), output);
        assertTrue(Files.isRegularFile(recording), output);
        assertTrue(Files.isRegularFile(adapterReport), output);
        String json = Files.readString(adapterReport);
        assertTrue(json.contains("\"mode\":\"PROBE\""), json);
        assertTrue(json.contains("com/fs/starfarer/SyntheticLauncher"), json);
        assertTrue(json.contains("\"transformationsApplied\":0"), json);
        assertTrue(json.contains("\"containedFailures\":0"), json);
    }

    private static String encoded(Path path) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }
}