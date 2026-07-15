package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureParallelHashingTest {
    private static final long MIB = 1024L * 1024L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void hashingUsesConfiguredConcurrencyAndKeepsOutputsIdentical() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        List<String> paths = List.of(
                "graphics/a.png",
                "graphics/b.png",
                "graphics/c.png",
                "graphics/d.png",
                "graphics/e.png",
                "graphics/f.png");
        Color[] colors = {
                Color.RED,
                Color.GREEN,
                Color.BLUE,
                Color.WHITE,
                Color.BLACK,
                Color.MAGENTA
        };
        for (int i = 0; i < paths.size(); i++) {
            writeImage(root.resolve(paths.get(i)), colors[i]);
        }
        ResourceIndex index = index(root, "profile", paths);
        Path serialCache = temporaryDirectory.resolve("serial");
        Path parallelCache = temporaryDirectory.resolve("parallel");

        TextureBatchBuilder.Result serial = TextureBatchBuilder.build(
                index,
                serialCache,
                new TextureBatchBuilder.Options(1, 16 * MIB));

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstWave = new CountDownLatch(3);
        TextureBatchBuilder.SourceHasher trackingHasher = source -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstWave.countDown();
            try {
                if (!firstWave.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Hash workers did not overlap");
                }
                return Hashes.sha256(source);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while testing hash concurrency", error);
            } finally {
                active.decrementAndGet();
            }
        };

        TextureBatchBuilder.Result parallel = TextureBatchBuilder.build(
                index,
                parallelCache,
                new TextureBatchBuilder.Options(3, 32 * MIB),
                BulkTexturePreprocessor::readSnapshot,
                trackingHasher);

        assertEquals(3, maximumActive.get());
        assertEquals(0, active.get());
        assertArrayEquals(
                TextureManifestIO.toBytes(serial.manifest()),
                TextureManifestIO.toBytes(parallel.manifest()));
        for (String logicalPath : paths) {
            Path serialBlob = serialCache.resolve(
                    serial.manifest().entry(logicalPath).orElseThrow().blobRelativePath());
            Path parallelBlob = parallelCache.resolve(
                    parallel.manifest().entry(logicalPath).orElseThrow().blobRelativePath());
            assertArrayEquals(Files.readAllBytes(serialBlob), Files.readAllBytes(parallelBlob));
        }
    }

    @Test
    void repeatedExactSourcePathIsHashedOnce() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path source = root.resolve("graphics/shared.png");
        writeImage(source, Color.ORANGE);
        ResourceIndex index = aliasIndex(root, "profile", source);
        AtomicInteger hashCalls = new AtomicInteger();

        TextureBatchBuilder.Result result = TextureBatchBuilder.build(
                index,
                temporaryDirectory.resolve("cache"),
                new TextureBatchBuilder.Options(2, 16 * MIB),
                BulkTexturePreprocessor::readSnapshot,
                path -> {
                    hashCalls.incrementAndGet();
                    return Hashes.sha256(path);
                });

        assertEquals(1, hashCalls.get());
        assertEquals(2, result.hashedEntries());
        assertEquals(1, result.uniqueContent());
        assertEquals(1, result.deduplicatedEntries());
        assertEquals(2, result.manifest().entryCount());
    }

    @Test
    void hashDiagnosticsStayDeterministicWhenTasksFinishOutOfOrder() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        List<String> paths = List.of("graphics/a.png", "graphics/b.png", "graphics/c.png");
        writeImage(root.resolve(paths.get(0)), Color.RED);
        writeImage(root.resolve(paths.get(1)), Color.GREEN);
        writeImage(root.resolve(paths.get(2)), Color.BLUE);
        ResourceIndex index = index(root, "profile", paths);

        TextureBatchBuilder.SourceHasher failingHasher = source -> {
            String name = source.getFileName().toString();
            long delay = switch (name) {
                case "a.png" -> 90L;
                case "b.png" -> 10L;
                default -> 45L;
            };
            try {
                Thread.sleep(delay);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while testing deterministic diagnostics", error);
            }
            throw new IOException("blocked-" + name);
        };

        TextureBatchBuilder.Result serial = TextureBatchBuilder.build(
                index,
                temporaryDirectory.resolve("serial-errors"),
                new TextureBatchBuilder.Options(1, 16 * MIB),
                BulkTexturePreprocessor::readSnapshot,
                failingHasher);
        TextureBatchBuilder.Result parallel = TextureBatchBuilder.build(
                index,
                temporaryDirectory.resolve("parallel-errors"),
                new TextureBatchBuilder.Options(3, 16 * MIB),
                BulkTexturePreprocessor::readSnapshot,
                failingHasher);

        List<String> expected = List.of(
                "Could not hash graphics/a.png from root: blocked-a.png",
                "Could not hash graphics/b.png from root: blocked-b.png",
                "Could not hash graphics/c.png from root: blocked-c.png");
        assertEquals(expected, serial.diagnostics());
        assertEquals(expected, parallel.diagnostics());
        assertEquals(0, parallel.hashedEntries());
        assertEquals(0, parallel.manifest().entryCount());
    }

    @Test
    void oneHashFailureOmitsEveryLogicalAlias() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        Path source = root.resolve("graphics/shared.png");
        writeImage(source, Color.CYAN);
        ResourceIndex index = aliasIndex(root, "profile", source);
        AtomicInteger hashCalls = new AtomicInteger();

        TextureBatchBuilder.Result result = TextureBatchBuilder.build(
                index,
                temporaryDirectory.resolve("failed-aliases"),
                new TextureBatchBuilder.Options(2, 16 * MIB),
                BulkTexturePreprocessor::readSnapshot,
                path -> {
                    hashCalls.incrementAndGet();
                    throw new IOException("denied");
                });

        assertEquals(1, hashCalls.get());
        assertEquals(0, result.hashedEntries());
        assertEquals(0, result.manifest().entryCount());
        assertEquals(
                List.of(
                        "Could not hash graphics/alias-a.png from root: denied",
                        "Could not hash graphics/alias-b.png from root: denied"),
                result.diagnostics());
    }

    private static ResourceIndex aliasIndex(Path root, String fingerprint, Path source) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        ResourceIndex.Provider provider = new ResourceIndex.Provider(
                0,
                "graphics/shared.png",
                attributes.size(),
                Math.max(0, attributes.lastModifiedTime().toMillis()));
        Map<String, List<ResourceIndex.Provider>> entries = new LinkedHashMap<>();
        entries.put("graphics/alias-a.png", List.of(provider));
        entries.put("graphics/alias-b.png", List.of(provider));
        return new ResourceIndex(
                fingerprint,
                List.of(new ResourceIndex.Root("root", root, false)),
                entries);
    }

    private static ResourceIndex index(Path root, String fingerprint, List<String> paths) throws Exception {
        Map<String, List<ResourceIndex.Provider>> entries = new LinkedHashMap<>();
        for (String relative : paths) {
            Path file = root.resolve(relative);
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            entries.put(relative, List.of(new ResourceIndex.Provider(
                    0,
                    relative,
                    attributes.size(),
                    Math.max(0, attributes.lastModifiedTime().toMillis()))));
        }
        return new ResourceIndex(
                fingerprint,
                List.of(new ResourceIndex.Root("root", root, false)),
                entries);
    }

    private static void writeImage(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }
}
