package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TexturePreparedPixelAgentIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedWarmHitBypassesDecodeAndConversionButRunsOriginalCleanup() throws Exception {
        Fixture fixture = fixture(false, false);

        ProcessResult result = launch(fixture, "graphics/test.png", false);

        assertSuccess(result);
        assertTrue(result.output().contains(
                "synthetic-pixels:123456:colors=ff0a141e,ff28323c,ff46505a:decode=0:convert=0:cleanup=1"),
                result.output());
        String report = Files.readString(fixture.adapterReport());
        assertTrue(report.contains("\"transformationsApplied\":1"), report);
        assertTrue(report.contains("\"hits\":1"), report);
        assertTrue(report.contains("\"fallbacks\":0"), report);
    }

    @Test
    void packagedColdMissExecutesOriginalDecodeAndConversionOnce() throws Exception {
        Fixture fixture = fixture(false, false);

        ProcessResult result = launch(fixture, "graphics/missing.png", false);

        assertSuccess(result);
        assertTrue(result.output().contains(
                "synthetic-pixels:cc00cc:colors=ffcc00cc,ff00ff00,ff0000ff:decode=1:convert=1:cleanup=1"),
                result.output());
        String report = Files.readString(fixture.adapterReport());
        assertTrue(report.contains("\"misses\":1"), report);
        assertTrue(report.contains("\"entry-missing\":1"), report);
    }

    @Test
    void packagedCorruptBlobQuarantinesThenExecutesOriginalPathOnce() throws Exception {
        Fixture fixture = fixture(true, false);

        ProcessResult result = launch(fixture, "graphics/test.png", false);

        assertSuccess(result);
        assertTrue(result.output().contains("decode=1:convert=1:cleanup=1"), result.output());
        String report = Files.readString(fixture.adapterReport());
        assertTrue(report.contains("\"corruptions\":1"), report);
        assertTrue(report.contains("\"quarantined\":1"), report);
        assertFalse(Files.exists(fixture.blob()));
    }

    @Test
    void packagedIdentityMismatchLeavesClassUntouched() throws Exception {
        Fixture fixture = fixture(false, true);

        ProcessResult result = launch(fixture, "graphics/test.png", false);

        assertSuccess(result);
        assertTrue(result.output().contains("decode=1:convert=1:cleanup=1"), result.output());
        String report = Files.readString(fixture.adapterReport());
        assertTrue(report.contains("\"transformationsApplied\":0"), report);
        assertTrue(report.contains("\"exactMatches\":0"), report);
    }

    @Test
    void packagedKillSwitchLeavesClassUntouched() throws Exception {
        Fixture fixture = fixture(false, false);

        ProcessResult result = launch(fixture, "graphics/test.png", true);

        assertSuccess(result);
        assertTrue(result.output().contains("decode=1:convert=1:cleanup=1"), result.output());
        String report = Files.readString(fixture.adapterReport());
        assertTrue(report.contains("\"killSwitchActive\":true"), report);
        assertTrue(report.contains("kill-switch"), report);
        assertTrue(report.contains("\"transformationsApplied\":0"), report);
    }

    private Fixture fixture(boolean corruptBlob, boolean wrongClassHash) throws Exception {
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path classFile = testClasses.resolve("com/fs/graphics/TextureLoader.class");
        byte[] classBytes = Files.readAllBytes(classFile);
        Path targetJar = temporaryDirectory.resolve("starsector-core/fixture-texture-pixels.jar");
        Files.createDirectories(targetJar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(targetJar))) {
            JarEntry entry = new JarEntry("com/fs/graphics/TextureLoader.class");
            entry.setTime(0);
            output.putNextEntry(entry);
            output.write(classBytes);
            output.closeEntry();
        }

        String classHash = wrongClassHash ? "0".repeat(64) : Hashes.sha256(classBytes);
        String archiveHash = Hashes.sha256(targetJar);
        Path targets = temporaryDirectory.resolve("targets-pixels-" + System.nanoTime() + ".txt");
        Files.writeString(targets, """
                target synthetic-texture-loader-pixels
                class com/fs/graphics/TextureLoader
                sha256 %s
                plan texture-prepared-pixels-v1
                source-kind STARSECTOR_CORE
                source-suffix starsector-core/fixture-texture-pixels.jar
                source-sha256 %s
                loader-class jdk/internal/loader/ClassLoaders$AppClassLoader
                loader-name app
                method Ô00000 (Ljava/lang/String;)Ljava/awt/image/BufferedImage;
                method o00000 (Ljava/awt/image/BufferedImage;Lcom/fs/graphics/Object;)Ljava/nio/ByteBuffer;
                method o00000 (Ljava/nio/ByteBuffer;Ljava/lang/String;)V
                end
                """.formatted(classHash, archiveHash));

        Path cache = temporaryDirectory.resolve("cache-pixels-" + System.nanoTime());
        Path sourceRoot = temporaryDirectory.resolve("game-pixels-" + System.nanoTime());
        Path source = sourceRoot.resolve("graphics/test.png");
        Files.createDirectories(source.getParent());
        byte[] encoded = {1, 3, 3, 7};
        Files.write(source, encoded);
        String sourceHash = Hashes.sha256(encoded);
        String profile = "ef".repeat(32);
        ResourceIndex index = new ResourceIndex(
                profile,
                List.of(new ResourceIndex.Root("core", sourceRoot, true)),
                Map.of("graphics/test.png", List.of(new ResourceIndex.Provider(
                        0,
                        "graphics/test.png",
                        Files.size(source),
                        Files.getLastModifiedTime(source).toMillis()))));
        Path indexPath = cache.resolve("indexes/" + profile + ".spfi");
        ResourceIndexIO.write(indexPath, index);

        PreparedTexture texture = new PreparedTexture(
                sourceHash,
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                3,
                PreparedTexture.rgba(10, 20, 30, 255),
                PreparedTexture.rgba(40, 50, 60, 255),
                PreparedTexture.rgba(70, 80, 90, 255),
                new byte[] {0x12, 0x34, 0x56});
        String blobRelative = "blobs/" + sourceHash.substring(0, 2) + "/" + sourceHash + "-identity.spft";
        Path blob = cache.resolve(blobRelative);
        PreparedTextureIO.write(blob, texture);
        if (corruptBlob) {
            Files.write(blob, new byte[] {1, 2, 3});
        }
        TextureManifest manifest = new TextureManifest(profile, Map.of(
                "graphics/test.png",
                new TextureManifest.Entry(
                        sourceHash,
                        PreparedTexture.Transformation.IDENTITY,
                        blobRelative,
                        1,
                        1,
                        3,
                        3)));
        Path manifestPath = cache.resolve("manifests/" + profile + ".spfm");
        TextureManifestIO.write(manifestPath, manifest);
        Path recording = temporaryDirectory.resolve("startup-pixels-" + System.nanoTime() + ".jfr");
        Path report = temporaryDirectory.resolve("adapter-pixels-" + System.nanoTime() + ".json");
        return new Fixture(
                targetJar,
                targets,
                cache,
                manifestPath,
                indexPath,
                blob,
                recording,
                report,
                testClasses);
    }

    private ProcessResult launch(Fixture fixture, String logicalPath, boolean killSwitch) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        Path agent = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        String agentArguments = "dest64=" + encoded(fixture.recording())
                + ",adapter=enabled"
                + ",textureMode=prepared-pixels"
                + ",adapterReport64=" + encoded(fixture.adapterReport())
                + ",targets64=" + encoded(fixture.targets())
                + ",textureCache64=" + encoded(fixture.cache())
                + ",textureManifest64=" + encoded(fixture.manifest())
                + ",textureIndex64=" + encoded(fixture.index());
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        if (killSwitch) {
            command.add("-Dpreflight.adapter.disabled=true");
        }
        command.add("-javaagent:" + agent + "=" + agentArguments);
        command.add("-cp");
        command.add(fixture.targetJar() + System.getProperty("path.separator") + fixture.testClasses());
        command.add("com.fs.starfarer.SyntheticTextureLauncher");
        command.add(logicalPath);
        command.add("prepared-pixels");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(completed, completed ? process.exitValue() : -1, output);
    }

    private static void assertSuccess(ProcessResult result) {
        assertTrue(result.completed(), result.output());
        assertEquals(0, result.exitCode(), result.output());
    }

    private static String encoded(Path path) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }

    private record Fixture(
            Path targetJar,
            Path targets,
            Path cache,
            Path manifest,
            Path index,
            Path blob,
            Path recording,
            Path adapterReport,
            Path testClasses) {
    }

    private record ProcessResult(boolean completed, int exitCode, String output) {
    }
}
