package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BitmapFontTest {

    // Canonical (single-space) descriptor modelled on the real graphics/fonts/insignia15LTaa.fnt.
    private static final String DESCRIPTOR = String.join("\n",
            "info face=\"InsigniaLT\" size=15 bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 "
                    + "smooth=1 aa=4 padding=1,2,3,4 spacing=1,1 outline=0",
            "common lineHeight=15 base=12 scaleW=256 scaleH=256 pages=1 packed=0 alphaChnl=1 "
                    + "redChnl=0 greenChnl=0 blueChnl=0",
            "page id=0 file=\"insignia15LTaa_0.png\"",
            "chars count=2",
            "char id=32 x=254 y=39 width=1 height=1 xoffset=-1 yoffset=12 xadvance=4 page=0 chnl=15",
            "char id=33 x=165 y=38 width=2 height=11 xoffset=1 yoffset=2 xadvance=4 page=0 chnl=15",
            "kernings count=1",
            "kerning first=32 second=33 amount=-1");

    @Test
    void roundTripsCanonicalDescriptorExactly() {
        assertEquals(DESCRIPTOR, BitmapFont.parse(DESCRIPTOR).serialize());
    }

    @Test
    void exposesBaseHeightAndPageFiles() {
        BitmapFont font = BitmapFont.parse(DESCRIPTOR);
        assertEquals(12, font.baseHeight());
        assertEquals(List.of("insignia15LTaa_0.png"), font.pageFiles());
    }

    @Test
    void scalesEveryPixelCoordinateByTheFactor() {
        String scaled = BitmapFont.parse(DESCRIPTOR).scaled(2).serialize();

        // info: size, padding, spacing, outline scale; percentages/flags/sample-count do not.
        assertTrue(scaled.contains("size=30"), scaled);
        assertTrue(scaled.contains("padding=2,4,6,8"), scaled);
        assertTrue(scaled.contains("spacing=2,2"), scaled);
        assertTrue(scaled.contains("stretchH=100"), scaled);
        assertTrue(scaled.contains("aa=4"), scaled);
        assertTrue(scaled.contains("bold=0"), scaled);

        // common: dimensions scale; page count and channel flags do not.
        assertTrue(scaled.contains("lineHeight=30"), scaled);
        assertTrue(scaled.contains("base=24"), scaled);
        assertTrue(scaled.contains("scaleW=512"), scaled);
        assertTrue(scaled.contains("scaleH=512"), scaled);
        assertTrue(scaled.contains("pages=1"), scaled);

        // char: geometry scales (including negative offsets); id/page/chnl do not.
        assertTrue(scaled.contains("char id=32 x=508 y=78 width=2 height=2 xoffset=-2 yoffset=24 "
                + "xadvance=8 page=0 chnl=15"), scaled);
        assertTrue(scaled.contains("char id=33 x=330 y=76 width=4 height=22 xoffset=2 yoffset=4 "
                + "xadvance=8 page=0 chnl=15"), scaled);

        // kerning amount scales, character ids do not.
        assertTrue(scaled.contains("kerning first=32 second=33 amount=-2"), scaled);

        // Passthrough lines and the atlas page file are untouched.
        assertTrue(scaled.contains("chars count=2"), scaled);
        assertTrue(scaled.contains("file=\"insignia15LTaa_0.png\""), scaled);
        assertTrue(scaled.contains("kernings count=1"), scaled);
    }

    @Test
    void baseHeightScalesWithTheFactor() {
        assertEquals(24, BitmapFont.parse(DESCRIPTOR).scaled(2).baseHeight());
    }

    @Test
    void rejectsNonPositiveScale() {
        BitmapFont font = BitmapFont.parse(DESCRIPTOR);
        try {
            font.scaled(0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
