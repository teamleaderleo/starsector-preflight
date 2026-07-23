package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesAPairedDescriptorAndAtlas() throws Exception {
        int exit = PreflightCli.run(new String[] {
                "font", "generate",
                "--logical", "sans-serif",
                "--size", "24",
                "--name", "probe",
                "--out-dir", temporaryDirectory.toString(),
                "--ascii"
        });
        assertEquals(0, exit);

        Path descriptor = temporaryDirectory.resolve("probe.fnt");
        Path atlas = temporaryDirectory.resolve("probe_0.png");
        assertTrue(Files.isRegularFile(descriptor), "descriptor written");
        assertTrue(Files.isRegularFile(atlas), "atlas written");

        BitmapFont font = BitmapFont.parse(Files.readString(descriptor));
        assertEquals(List.of("probe_0.png"), font.pageFiles());
        assertTrue(font.charIds().contains((int) 'A'), "expected ASCII coverage");

        BufferedImage image = ImageIO.read(atlas.toFile());
        assertNotNull(image, "atlas is a readable PNG");
        // The descriptor's declared atlas size must match the produced image.
        assertTrue(font.serialize().contains("scaleW=" + image.getWidth()), font.serialize());
        assertTrue(font.serialize().contains("scaleH=" + image.getHeight()), font.serialize());
    }

    @Test
    void charsetFromMatchesAnExistingDescriptorsCoverage() throws Exception {
        // A tiny source descriptor covering only 'A' and 'B'.
        Path source = temporaryDirectory.resolve("source.fnt");
        Files.writeString(source, String.join("\n",
                "info face=\"X\" size=10",
                "common lineHeight=10 base=8 scaleW=64 scaleH=64 pages=1",
                "page id=0 file=\"source_0.png\"",
                "chars count=2",
                "char id=65 x=0 y=0 width=5 height=6 xoffset=0 yoffset=2 xadvance=6 page=0 chnl=15",
                "char id=66 x=6 y=0 width=5 height=6 xoffset=0 yoffset=2 xadvance=6 page=0 chnl=15"));

        int exit = PreflightCli.run(new String[] {
                "font", "generate",
                "--logical", "serif",
                "--size", "20",
                "--name", "subset",
                "--out-dir", temporaryDirectory.toString(),
                "--charset-from", source.toString()
        });
        assertEquals(0, exit);

        BitmapFont generated = BitmapFont.parse(Files.readString(temporaryDirectory.resolve("subset.fnt")));
        assertTrue(generated.charIds().contains((int) 'A'));
        assertTrue(generated.charIds().contains((int) 'B'));
        assertTrue(generated.charIds().stream().allMatch(id -> id == 'A' || id == 'B'), generated.charIds().toString());
    }

    @Test
    void rejectsAmbiguousOrMissingFontSource() {
        assertThrows(IllegalArgumentException.class, () -> PreflightCli.run(new String[] {
                "font", "generate",
                "--size", "24", "--name", "x", "--out-dir", temporaryDirectory.toString()
        }));
        assertThrows(IllegalArgumentException.class, () -> PreflightCli.run(new String[] {
                "font", "generate", "--logical", "sans-serif",
                "--name", "x", "--out-dir", temporaryDirectory.toString()
        }));
    }
}
