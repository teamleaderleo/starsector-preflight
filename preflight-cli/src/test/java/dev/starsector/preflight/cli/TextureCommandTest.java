package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesInspectsVerifiesBenchmarksAndInvalidates() throws Exception {
        Path source = temporaryDirectory.resolve("source image.png");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(0, 1, Color.BLUE.getRGB());
        image.setRGB(1, 1, new Color(0, 0, 0, 0).getRGB());
        assertTrue(ImageIO.write(image, "png", source.toFile()));
        Path blob = temporaryDirectory.resolve("prepared texture.spft");

        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "prepare", source.toString(), "--output", blob.toString()
        }));
        assertTrue(Files.isRegularFile(blob));
        PreparedTexture prepared = PreparedTextureIO.read(blob);
        assertEquals(2, prepared.uploadWidth());
        assertEquals(16, prepared.pixelBytes());

        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "inspect", blob.toString()
        }));
        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "verify", source.toString(), blob.toString()
        }));
        assertEquals(0, PreflightCli.run(new String[] {
                "texture", "benchmark", source.toString(), blob.toString(), "--runs", "1"
        }));

        image.setRGB(0, 0, Color.WHITE.getRGB());
        assertTrue(ImageIO.write(image, "png", source.toFile()));
        assertEquals(5, PreflightCli.run(new String[] {
                "texture", "verify", source.toString(), blob.toString()
        }));
    }
}
