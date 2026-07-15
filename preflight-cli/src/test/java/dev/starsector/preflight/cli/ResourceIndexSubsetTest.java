package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceIndexSubsetTest {
    private static final long MIB = 1024L * 1024L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void selectionIsOrderIndependentAndReportsRejectedPaths() throws Exception {
        ResourceIndex source = fixtureIndex();
        Path first = temporaryDirectory.resolve("first.txt");
        Files.writeString(first, """
                # startup selection
                graphics/B.PNG
                graphics/a.png
                graphics/a.png
                data/config.json
                graphics/missing.png
                ../escape.png
                """);
        Path second = temporaryDirectory.resolve("second.txt");
        Files.writeString(second, """
                graphics/a.png
                graphics/b.png
                data/config.json
                graphics/missing.png
                """);

        ResourceIndexSubset.Result left = ResourceIndexSubset.select(source, first);
        ResourceIndexSubset.Result right = ResourceIndexSubset.select(source, second);

        assertEquals(left.selectionFingerprint(), right.selectionFingerprint());
        assertArrayEquals(ResourceIndexIO.toBytes(left.index()), ResourceIndexIO.toBytes(right.index()));
        assertEquals(List.of("graphics/a.png", "graphics/b.png"), left.index().entries().keySet().stream().toList());
        assertEquals(5, left.requestedPaths());
        assertEquals(2, left.selectedPaths());
        assertEquals(1, left.missingPaths());
        assertEquals(1, left.nonImagePaths());
        assertEquals(1, left.invalidPaths());
        assertNotEquals(source.profileFingerprint(), left.selectionFingerprint());
    }

    @Test
    void subsetBuildDoesNotReplaceFullProfileManifest() throws Exception {
        ResourceIndex source = fixtureIndex();
        Path cache = temporaryDirectory.resolve("cache");
        TextureBatchBuilder.Result full = TextureBatchBuilder.build(
                source,
                cache,
                new TextureBatchBuilder.Options(2, 16 * MIB));
        byte[] fullManifest = Files.readAllBytes(full.manifestPath());

        Path selectionFile = temporaryDirectory.resolve("selection.txt");
        Files.writeString(selectionFile, "graphics/a.png\n");
        ResourceIndexSubset.Result selection = ResourceIndexSubset.select(source, selectionFile);
        TextureBatchBuilder.Result subset = TextureBatchBuilder.build(
                selection.index(),
                cache,
                new TextureBatchBuilder.Options(2, 16 * MIB));

        assertEquals(2, full.manifest().entryCount());
        assertEquals(1, subset.manifest().entryCount());
        assertTrue(subset.manifest().entry("graphics/a.png").isPresent());
        assertTrue(subset.manifest().entry("graphics/b.png").isEmpty());
        assertNotEquals(full.manifestPath(), subset.manifestPath());
        assertArrayEquals(fullManifest, Files.readAllBytes(full.manifestPath()));
        assertEquals(selection.selectionFingerprint(), TextureManifestIO.read(subset.manifestPath()).profileFingerprint());
    }

    private ResourceIndex fixtureIndex() throws Exception {
        Path root = temporaryDirectory.resolve("root");
        writeImage(root.resolve("graphics/a.png"), Color.RED);
        writeImage(root.resolve("graphics/b.png"), Color.BLUE);
        Path data = root.resolve("data/config.json");
        Files.createDirectories(data.getParent());
        Files.writeString(data, "{}");

        Map<String, List<ResourceIndex.Provider>> entries = new LinkedHashMap<>();
        add(entries, root, "graphics/a.png");
        add(entries, root, "graphics/b.png");
        add(entries, root, "data/config.json");
        return new ResourceIndex(
                "full-profile",
                List.of(new ResourceIndex.Root("root", root, true)),
                entries);
    }

    private static void add(
            Map<String, List<ResourceIndex.Provider>> entries,
            Path root,
            String relative) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(root.resolve(relative), BasicFileAttributes.class);
        entries.put(relative, List.of(new ResourceIndex.Provider(
                0,
                relative,
                attributes.size(),
                Math.max(0, attributes.lastModifiedTime().toMillis()))));
    }

    private static void writeImage(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }
}
