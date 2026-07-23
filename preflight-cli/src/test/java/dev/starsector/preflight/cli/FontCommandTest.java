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
    void generatePackReplacesEveryFontInASourceDirectoryAndMatchesSize() throws Exception {
        // A minimal source "graphics/fonts" with two descriptors of different base sizes.
        Path fontsDir = temporaryDirectory.resolve("src-fonts");
        Files.createDirectories(fontsDir);
        writeSourceFont(fontsDir.resolve("small.fnt"), 8);
        writeSourceFont(fontsDir.resolve("big.fnt"), 20);
        Path modDir = temporaryDirectory.resolve("mymod");

        int exit = PreflightCli.run(new String[] {
                "font", "generate-pack",
                "--logical", "sans-serif",
                "--fonts-dir", fontsDir.toString(),
                "--out-dir", modDir.toString(),
                "--mod-id", "test_pack",
                "--mod-name", "Test Pack"
        });
        assertEquals(0, exit);

        assertTrue(Files.isRegularFile(modDir.resolve("mod_info.json")));
        for (String name : new String[] {"small", "big"}) {
            Path fnt = modDir.resolve("graphics/fonts/" + name + ".fnt");
            Path png = modDir.resolve("graphics/fonts/" + name + "_0.png");
            assertTrue(Files.isRegularFile(fnt), name + ".fnt missing");
            assertTrue(Files.isRegularFile(png), name + "_0.png missing");
            assertNotNull(ImageIO.read(png.toFile()), name + " atlas unreadable");
        }
        // Matched sizing: a larger source base yields a larger generated base.
        int smallBase = BitmapFont.parse(Files.readString(modDir.resolve("graphics/fonts/small.fnt"))).baseHeight();
        int bigBase = BitmapFont.parse(Files.readString(modDir.resolve("graphics/fonts/big.fnt"))).baseHeight();
        assertTrue(bigBase > smallBase, "big=" + bigBase + " small=" + smallBase);
    }

    @Test
    void generatePackScaleEnlargesEveryReplacement() throws Exception {
        Path fontsDir = temporaryDirectory.resolve("src");
        Files.createDirectories(fontsDir);
        writeSourceFont(fontsDir.resolve("f.fnt"), 12);
        Path oneX = temporaryDirectory.resolve("m1");
        Path twoX = temporaryDirectory.resolve("m2");
        pack(fontsDir, oneX, 1);
        pack(fontsDir, twoX, 2);
        int base1 = BitmapFont.parse(Files.readString(oneX.resolve("graphics/fonts/f.fnt"))).baseHeight();
        int base2 = BitmapFont.parse(Files.readString(twoX.resolve("graphics/fonts/f.fnt"))).baseHeight();
        assertTrue(base2 > base1, "2x base " + base2 + " should exceed 1x base " + base1);
    }

    private static void pack(Path fontsDir, Path modDir, int scale) throws Exception {
        assertEquals(0, PreflightCli.run(new String[] {
                "font", "generate-pack", "--logical", "sans-serif",
                "--fonts-dir", fontsDir.toString(), "--out-dir", modDir.toString(),
                "--scale", Integer.toString(scale)
        }));
    }

    private static void writeSourceFont(Path path, int base) throws Exception {
        String stem = path.getFileName().toString().replace(".fnt", "");
        Files.writeString(path, String.join("\n",
                "info face=\"Src\" size=" + base,
                "common lineHeight=" + (base + 2) + " base=" + base + " scaleW=128 scaleH=128 pages=1",
                "page id=0 file=\"" + stem + "_0.png\"",
                "chars count=2",
                "char id=65 x=0 y=0 width=5 height=6 xoffset=0 yoffset=2 xadvance=6 page=0 chnl=15",
                "char id=97 x=6 y=0 width=5 height=6 xoffset=0 yoffset=2 xadvance=6 page=0 chnl=15"));
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
