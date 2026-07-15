package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticExtendedCrossProcessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void coldAndWarmSeparateJvmsEliminateImageAudioJarAndCompilerWork() throws Exception {
        Path profile = temporaryDirectory.resolve("profile");
        Path cache = temporaryDirectory.resolve("cache");
        SyntheticExtendedProfile.Scale scale = Boolean.getBoolean("preflight.synthetic.medium")
                ? SyntheticExtendedProfile.Scale.MEDIUM
                : SyntheticExtendedProfile.Scale.TINY;
        var manifest = SyntheticExtendedProfile.generate(profile, 12345, scale);
        String cold = launch(profile, cache, temporaryDirectory.resolve("cold.json"));
        String warm = launch(profile, cache, temporaryDirectory.resolve("warm.json"));
        assertNotEquals(longField(cold, "processId"), longField(warm, "processId"));
        assertEquals(manifest.physicalFiles(), longField(warm, "profileFilesValidated"));
        assertEquals(0, longField(warm, "profileManifestStale"));
        assertTrue(longField(cold, "imageDecoderCalls") > 0);
        assertTrue(longField(cold, "audioDecoderCalls") > 0);
        assertEquals(1, longField(cold, "javaCompilerCalls"));
        assertEquals(manifest.scale().jars(), longField(cold, "extendedIndexJarScans"));
        assertEquals(0, longField(warm, "imageDecoderCalls"));
        assertEquals(0, longField(warm, "audioDecoderCalls"));
        assertEquals(0, longField(warm, "javaCompilerCalls"));
        assertEquals(1, longField(warm, "extendedIndexHits"));
        assertEquals(0, longField(warm, "extendedIndexJarScans"));
        assertEquals(0, longField(warm, "extendedIndexLooseVisits"));
        assertEquals(1, longField(warm, "generatedBytecodeCacheHits"));
        assertEquals(manifest.effectFiles(), longField(warm, "audioCacheHits"));
        assertEquals(manifest.streamedFiles(), longField(warm, "audioStreamed"));
        for (String field : List.of("looseWinningProvidersSha256", "preparedImageOutputsSha256",
                "extendedWinningProvidersSha256", "preparedAudioOutputsSha256", "generatedClassMapSha256")) {
            assertEquals(stringField(cold, field), stringField(warm, field));
        }
        assertEquals(longField(cold, "generatedClassCount"), longField(warm, "generatedClassCount"));
        assertEquals(longField(cold, "generatedClassBytes"), longField(warm, "generatedClassBytes"));
        assertZeroErrors(cold);
        assertZeroErrors(warm);
    }

    @Test
    void corruptAudioFallsBackOnceAndPreservesOutput() throws Exception {
        Path profile = temporaryDirectory.resolve("corrupt-profile");
        Path cache = temporaryDirectory.resolve("corrupt-cache");
        SyntheticExtendedProfile.generate(profile, 77, SyntheticExtendedProfile.Scale.TINY);
        String cold = launch(profile, cache, temporaryDirectory.resolve("corrupt-cold.json"));
        List<Path> audioFiles;
        try (var stream = Files.walk(cache)) {
            audioFiles = stream.filter(path -> path.getFileName().toString().endsWith(".spxa"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        }
        assertTrue(!audioFiles.isEmpty());
        Files.write(audioFiles.get(0), new byte[] {1, 2, 3});
        String repaired = launch(profile, cache, temporaryDirectory.resolve("corrupt-repaired.json"));
        assertEquals(1, longField(repaired, "audioDecoderCalls"));
        assertEquals(1, longField(repaired, "audioCacheCorruptFallbacks"));
        assertEquals(stringField(cold, "preparedAudioOutputsSha256"),
                stringField(repaired, "preparedAudioOutputsSha256"));
        assertZeroErrors(repaired);
    }

    private static void assertZeroErrors(String json) {
        for (String field : List.of("imageCacheReadErrors", "imageCacheWriteErrors",
                "extendedIndexReadErrors", "extendedIndexWriteErrors",
                "audioCacheReadErrors", "audioCacheWriteErrors",
                "generatedBytecodeCacheReadErrors", "generatedBytecodeCacheWriteErrors")) {
            assertEquals(0, longField(json, field));
        }
    }

    private static String launch(Path profile, Path cache, Path report) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(java.toString()); command.add("-Djava.awt.headless=true"); command.add("-cp"); command.add(classpath);
        command.add(SyntheticExtendedStartupWorker.class.getName()); command.add(profile.toString()); command.add(cache.toString()); command.add(report.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly(); throw new AssertionError("Extended synthetic worker timeout");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(Files.isRegularFile(report), output);
        return Files.readString(report, StandardCharsets.UTF_8);
    }

    private static long longField(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\":(-?[0-9]+)").matcher(json);
        assertTrue(matcher.find(), json); return Long.parseLong(matcher.group(1));
    }
    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\":\\\"([^\\\"]*)\\\"").matcher(json);
        assertTrue(matcher.find(), json); return matcher.group(1);
    }
}
