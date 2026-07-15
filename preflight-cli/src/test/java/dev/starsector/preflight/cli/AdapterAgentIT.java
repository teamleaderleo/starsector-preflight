package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterAgentIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedAgentProbesCandidateAndPreservesOriginalClass() throws Exception {
        Path recording = temporaryDirectory.resolve("startup.jfr");
        Path adapterReport = temporaryDirectory.resolve("adapter.json");
        String agentArguments = "dest64=" + encoded(recording)
                + ",adapter=probe,adapterReport64=" + encoded(adapterReport);

        ProcessResult result = launch(agentArguments, List.of());

        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("synthetic-starsector-launcher"), result.output());
        assertTrue(Files.isRegularFile(recording), result.output());
        assertTrue(Files.isRegularFile(adapterReport), result.output());
        String json = Files.readString(adapterReport);
        assertTrue(json.contains("\"mode\":\"PROBE\""), json);
        assertTrue(json.contains("com/fs/starfarer/SyntheticLauncher"), json);
        assertTrue(json.contains("\"transformationsApplied\":0"), json);
        assertTrue(json.contains("\"containedFailures\":0"), json);
    }

    @Test
    void packagedAgentTransformsSyntheticTargetAndUsesPreparedImage() throws Exception {
        CacheFixture cache = cacheFixture();
        Path recording = temporaryDirectory.resolve("prepared-startup.jfr");
        Path adapterReport = temporaryDirectory.resolve("prepared-adapter.json");
        Path targets = targetFile();
        String agentArguments = "dest64=" + encoded(recording)
                + ",adapter=enabled,adapterReport64=" + encoded(adapterReport)
                + ",targets64=" + encoded(targets)
                + ",textureCache64=" + encoded(cache.cache())
                + ",textureManifest64=" + encoded(cache.manifest())
                + ",resourceIndex64=" + encoded(cache.index());

        ProcessResult result = launch(agentArguments, List.of("prepared-image", cache.logicalPath()));

        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("synthetic-texture-pixel=ff123456"), result.output());
        assertTrue(Files.isRegularFile(recording), result.output());
        assertTrue(Files.isRegularFile(adapterReport), result.output());
        String json = Files.readString(adapterReport);
        assertTrue(json.contains("\"mode\":\"ENABLED\""), json);
        assertTrue(json.contains("\"exactMatches\":1"), json);
        assertTrue(json.contains("\"transformationsApplied\":1"), json);
        assertTrue(json.contains("\"liveTransformationPlansRegistered\":true"), json);
        assertTrue(json.contains("\"hits\":1"), json);
        assertTrue(json.contains("\"fallbacks\":0"), json);
        assertTrue(json.contains("\"containedFailures\":0"), json);
    }

    @Test
    void profilerOnlyLaunchDoesNotCreateAdapterReport() throws Exception {
        Path recording = temporaryDirectory.resolve("profile-only.jfr");
        Path adapterReport = temporaryDirectory.resolve("adapter.json");
        String agentArguments = "dest64=" + encoded(recording)
                + ",adapterReport64=" + encoded(adapterReport);

        ProcessResult result = launch(agentArguments, List.of());

        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(Files.isRegularFile(recording), result.output());
        assertFalse(Files.exists(adapterReport), result.output());
    }

    private CacheFixture cacheFixture() throws Exception {
        String logicalPath = "graphics/test.png";
        String fingerprint = "packaged-profile";
        Path root = temporaryDirectory.resolve("game/core");
        Path source = root.resolve(logicalPath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source");
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        ResourceIndex index = new ResourceIndex(
                fingerprint,
                List.of(new ResourceIndex.Root("core", root, true)),
                Map.of(logicalPath, List.of(new ResourceIndex.Provider(
                        0,
                        logicalPath,
                        attributes.size(),
                        Math.max(0, attributes.lastModifiedTime().toMillis())))));
        PreparedTexture texture = new PreparedTexture(
                "c".repeat(64),
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                3,
                0,
                0,
                0,
                new byte[] {0x12, 0x34, 0x56});
        Path cache = temporaryDirectory.resolve("cache");
        PreparedTextureIO.write(cache.resolve("blobs/test.spft"), texture);
        TextureManifest manifest = new TextureManifest(fingerprint, Map.of(
                logicalPath,
                new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        "blobs/test.spft",
                        1,
                        1,
                        3,
                        3)));
        Path manifestPath = temporaryDirectory.resolve("profile.spfm");
        Path indexPath = temporaryDirectory.resolve("profile.spfi");
        TextureManifestIO.write(manifestPath, manifest);
        ResourceIndexIO.write(indexPath, index);
        return new CacheFixture(cache, manifestPath, indexPath, logicalPath);
    }

    private Path targetFile() throws Exception {
        Path classFile = Path.of(
                "target", "test-classes", "com", "fs", "graphics", "TextureLoader.class")
                .toAbsolutePath().normalize();
        String classHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(classFile)));
        Path targets = temporaryDirectory.resolve("targets.txt");
        Files.writeString(targets, """
                target packaged-prepared-image
                class com/fs/graphics/TextureLoader
                sha256 %s
                plan prepared-image-v1
                source-kind STARSECTOR_CORE
                source-suffix target/test-classes
                loader-class jdk/internal/loader/ClassLoaders$AppClassLoader
                loader-name app
                method Ô00000 (Ljava/lang/String;)Ljava/awt/image/BufferedImage;
                end
                """.formatted(classHash));
        return targets;
    }

    private ProcessResult launch(String agentArguments, List<String> launcherArguments) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        Path agent = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        List<String> command = new java.util.ArrayList<>(List.of(
                java.toString(),
                "-javaagent:" + agent + "=" + agentArguments,
                "-cp",
                testClasses.toString(),
                "com.fs.starfarer.SyntheticLauncher"));
        command.addAll(launcherArguments);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(completed, completed ? process.exitValue() : -1, output);
    }

    private static String encoded(Path path) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }

    private record CacheFixture(Path cache, Path manifest, Path index, String logicalPath) {
    }

    private record ProcessResult(boolean completed, int exitCode, String output) {
    }
}
