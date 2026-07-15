package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureBatchCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsQueriesAndValidatesAProfileCache() throws Exception {
        Path core = temporaryDirectory.resolve("starsector-core");
        Path mods = temporaryDirectory.resolve("mods");
        Path alpha = mods.resolve("Alpha");
        writeImage(core.resolve("graphics/core.png"), Color.BLUE);
        writeImage(alpha.resolve("graphics/mod.png"), Color.RED);
        Files.writeString(alpha.resolve("mod_info.json"), "{\"id\":\"alpha\"}");
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[\"alpha\"]}");
        Path launcher = Files.writeString(temporaryDirectory.resolve("starsector.sh"), "#!/bin/sh\n");
        launcher.toFile().setExecutable(true);
        Path cache = temporaryDirectory.resolve("cache");

        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "build",
                "--game", temporaryDirectory.toString(),
                "--cache-dir", cache.toString(),
                "--workers", "2",
                "--memory-mb", "16"
        }));

        Path manifestFile;
        try (var manifests = Files.list(cache.resolve("manifests"))) {
            manifestFile = manifests.findFirst().orElseThrow();
        }
        TextureManifest manifest = TextureManifestIO.read(manifestFile);
        assertEquals(2, manifest.entryCount());

        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "manifest", "inspect", manifestFile.toString()
        }));
        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "manifest", "query", manifestFile.toString(), "graphics/mod.png"
        }));
        assertEquals(4, PreflightCli.run(new String[] {
                "texture", "manifest", "query", manifestFile.toString(), "graphics/missing.png"
        }));
        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "manifest", "validate", manifestFile.toString()
        }));

        Path blob = cache.resolve(manifest.entry("graphics/mod.png").orElseThrow().blobRelativePath());
        Files.write(blob, new byte[] {1, 2, 3});
        assertEquals(5, PreflightCli.run(new String[] {
                "texture", "manifest", "validate", manifestFile.toString()
        }));

        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "build",
                "--game", temporaryDirectory.toString(),
                "--cache-dir", cache.toString(),
                "--workers", "1",
                "--memory-mb", "16"
        }));
        assertTrue(Files.isDirectory(cache.resolve("quarantine")));
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
