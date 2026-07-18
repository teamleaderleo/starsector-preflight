package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfAgentIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedJarCanRecordAsAnAgent() throws Exception {
        Path jar = Path.of("target/preflight.jar").toAbsolutePath().normalize();
        Path recording = temporaryDirectory.resolve("startup trace,one.jfr");
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(recording.toString().getBytes(StandardCharsets.UTF_8));
        Path java = Path.of(System.getProperty("java.home"))
                .resolve("bin")
                .resolve(Platform.current() == Platform.WINDOWS ? "java.exe" : "java");
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();

        Process process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + jar + "=dest64=" + encoded,
                "-cp",
                testClasses.toString(),
                "com.fs.starfarer.SyntheticLauncher")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS), output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(Files.isRegularFile(recording), output);
        assertTrue(Files.size(recording) > 0, output);
        try (RecordingFile file = new RecordingFile(recording)) {
            assertTrue(file.hasMoreEvents());
        }
    }
}
