package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FontAtlasGeneratorTest {
    private static final Pattern CHAR_LINE = Pattern.compile(
            "char id=(\\d+) x=(\\d+) y=(\\d+) width=(\\d+) height=(\\d+) "
                    + "xoffset=(-?\\d+) yoffset=(-?\\d+) xadvance=(\\d+) page=0 chnl=15");

    private static FontAtlasGenerator.Result generateAscii(int size, int atlasWidth) {
        List<Integer> ascii = new ArrayList<>();
        for (int codepoint = 32; codepoint <= 126; codepoint++) {
            ascii.add(codepoint);
        }
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        return FontAtlasGenerator.generate(
                font, new FontAtlasGenerator.Options(atlasWidth, 1, "test_0.png", ascii));
    }

    @Test
    void producesAParseableDescriptorMatchingTheAtlas() {
        FontAtlasGenerator.Result result = generateAscii(32, 256);
        BitmapFont font = BitmapFont.parse(result.descriptor());

        assertEquals(result.baseHeight(), font.baseHeight());
        assertEquals(List.of("test_0.png"), font.pageFiles());
        assertTrue(result.glyphCount() > 50, "expected most printable ASCII glyphs");
        // Descriptor's declared atlas size must equal the produced image.
        assertTrue(result.descriptor().contains("scaleW=256"), result.descriptor());
        assertTrue(result.descriptor().contains("scaleH=" + result.atlas().getHeight()), result.descriptor());
    }

    @Test
    void packsEveryGlyphInsideTheAtlasBounds() {
        FontAtlasGenerator.Result result = generateAscii(32, 256);
        int atlasWidth = result.atlas().getWidth();
        int atlasHeight = result.atlas().getHeight();

        Matcher matcher = CHAR_LINE.matcher(result.descriptor());
        int matched = 0;
        boolean sawLetterA = false;
        while (matcher.find()) {
            matched++;
            int id = Integer.parseInt(matcher.group(1));
            int x = Integer.parseInt(matcher.group(2));
            int y = Integer.parseInt(matcher.group(3));
            int width = Integer.parseInt(matcher.group(4));
            int height = Integer.parseInt(matcher.group(5));
            int advance = Integer.parseInt(matcher.group(8));
            assertTrue(x + width <= atlasWidth, "glyph " + id + " exceeds atlas width");
            assertTrue(y + height <= atlasHeight, "glyph " + id + " exceeds atlas height");
            if (id == 'A') {
                sawLetterA = true;
                assertTrue(width > 0 && height > 0 && advance > 0, "'A' should ink and advance");
            }
        }
        assertTrue(matched > 50, "expected many char lines");
        assertTrue(sawLetterA, "letter A should be present");
    }

    @Test
    void atlasContainsRenderedInk() {
        FontAtlasGenerator.Result result = generateAscii(32, 256);
        BufferedImage atlas = result.atlas();
        long inkedPixels = 0;
        for (int y = 0; y < atlas.getHeight(); y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                if ((atlas.getRGB(x, y) >>> 24) != 0) {
                    inkedPixels++;
                }
            }
        }
        Assumptions.assumeTrue(inkedPixels > 0, "no glyph ink rendered — environment lacks usable fonts");
        assertTrue(inkedPixels > 100, "expected substantial glyph coverage, saw " + inkedPixels);
    }

    @Test
    void higherPixelSizeYieldsALargerBaseHeight() {
        // The N-times workflow: a 2x-size font has ~2x the baseline metrics.
        int small = generateAscii(16, 256).baseHeight();
        int large = generateAscii(32, 512).baseHeight();
        assertTrue(large > small, "32px base " + large + " should exceed 16px base " + small);
    }

    @Test
    void restrictsGlyphsToDisplayableRequestedCodepoints() {
        FontAtlasGenerator.Result result = generateAscii(24, 256);
        for (int id : BitmapFont.parse(result.descriptor()).charIds()) {
            assertTrue(id >= 32 && id <= 126, "unexpected codepoint " + id);
        }
    }
}
