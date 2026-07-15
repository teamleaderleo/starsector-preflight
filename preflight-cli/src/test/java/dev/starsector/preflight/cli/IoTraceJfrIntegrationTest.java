package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IoTraceJfrIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void summarizeIncludesKnownReadAndWritePathsFromRealJfr() throws Exception {
        Path image = temporaryDirectory.resolve("Example Mod/graphics/test image.PNG");
        Path cache = temporaryDirectory.resolve("cache/profile.SPFM");
        Path recordingFile = temporaryDirectory.resolve("startup.jfr");
        Path summary = temporaryDirectory.resolve("summary.json");
        Files.createDirectories(image.getParent());
        Files.createDirectories(cache.getParent());
        Files.write(image, new byte[8 * 1024]);

        try (Recording recording = new Recording()) {
            recording.enable("jdk.FileRead").withThreshold(Duration.ZERO);
            recording.enable("jdk.FileWrite").withThreshold(Duration.ZERO);
            recording.start();
            Files.readAllBytes(image);
            Files.readAllBytes(image);
            Files.write(cache, new byte[4 * 1024]);
            recording.stop();
            recording.dump(recordingFile);
        }

        assertEquals(0, PreflightCli.summarize(recordingFile, summary));
        String json = Files.readString(summary);
        String normalizedImage = IoTraceAttribution.normalizePath(image.toString());
        String normalizedCache = IoTraceAttribution.normalizePath(cache.toString());
        assertTrue(json.contains("\"ioAttribution\""), json);
        assertTrue(json.contains(normalizedImage.replace("\\", "\\\\").replace("\"", "\\\"")), json);
        assertTrue(json.contains(normalizedCache.replace("\\", "\\\\").replace("\"", "\\\"")), json);
        assertTrue(json.contains("\"extension\":\"png\""), json);
        assertTrue(json.contains("\"extension\":\"spfm\""), json);
        assertTrue(json.contains("\"category\":\"image\""), json);
        assertTrue(json.contains("\"category\":\"preflight-cache\""), json);
    }
}
