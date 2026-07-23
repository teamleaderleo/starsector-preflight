package dev.starsector.preflight.cli;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Rasterizes an AngelCode BMFont ({@code .fnt} + atlas PNG) from a vector {@link Font}.
 *
 * <p>This is the atlas half of the font-quality track: producing a font at pixel size
 * {@code N * targetSize} and drawing it at {@code targetSize} yields supersampled, crisp
 * same-size text (see {@code docs/asset-quality-track.md}). Rendering is white glyphs with
 * anti-aliased coverage in the alpha channel — the layout Starsector's tinting renderer
 * expects — packed onto a single page with a simple shelf packer.
 */
final class FontAtlasGenerator {
    private FontAtlasGenerator() {
    }

    record Options(int atlasWidth, int padding, String pageFileName, List<Integer> codepoints) {
        Options {
            if (atlasWidth < 16) {
                throw new IllegalArgumentException("atlasWidth must be at least 16");
            }
            if (padding < 0) {
                throw new IllegalArgumentException("padding must not be negative");
            }
            codepoints = List.copyOf(codepoints);
        }
    }

    record Result(BufferedImage atlas, String descriptor, int glyphCount, int baseHeight) {
    }

    /** Renders {@code font} (already at its target pixel size) into a BMFont atlas + descriptor. */
    static Result generate(Font font, Options options) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        Graphics2D metricsGraphics = scratchGraphics();
        FontMetrics metrics = metricsGraphics.getFontMetrics(font);
        int ascent = metrics.getAscent();
        int lineHeight = metrics.getHeight();
        metricsGraphics.dispose();

        List<Glyph> glyphs = new ArrayList<>();
        for (int codepoint : options.codepoints()) {
            if (!font.canDisplay(codepoint)) {
                continue;
            }
            glyphs.add(measure(font, frc, codepoint, ascent));
        }

        int atlasHeight = pack(glyphs, options.atlasWidth(), options.padding(), lineHeight);
        BufferedImage atlas = render(font, frc, glyphs, options.atlasWidth(), atlasHeight);
        String descriptor = descriptor(font, options, glyphs, ascent, lineHeight, atlasHeight);
        // baseHeight mirrors BitmapFont.baseHeight() (the descriptor's `common base` == ascent).
        return new Result(atlas, descriptor, glyphs.size(), ascent);
    }

    private static Glyph measure(Font font, FontRenderContext frc, int codepoint, int ascent) {
        String text = new String(Character.toChars(codepoint));
        GlyphVector vector = font.createGlyphVector(frc, text);
        int advance = Math.round(vector.getGlyphMetrics(0).getAdvanceX());
        Rectangle ink = vector.getPixelBounds(frc, 0, 0);
        Glyph glyph = new Glyph();
        glyph.codepoint = codepoint;
        glyph.advance = Math.max(0, advance);
        if (ink.width <= 0 || ink.height <= 0) {
            // Whitespace and other non-inking glyphs: no atlas cell, advance only.
            glyph.width = 0;
            glyph.height = 0;
            glyph.xOffset = 0;
            glyph.yOffset = 0;
            glyph.inkY = 0;
        } else {
            glyph.width = ink.width;
            glyph.height = ink.height;
            glyph.xOffset = ink.x;              // left bearing == BMFont xoffset
            glyph.inkY = ink.y;                 // negative: ink top relative to the baseline
            glyph.yOffset = ascent + ink.y;     // BMFont yoffset: line-top to ink-top
        }
        return glyph;
    }

    /** Shelf-packs inking glyphs left-to-right, wrapping rows; returns the atlas height. */
    private static int pack(List<Glyph> glyphs, int atlasWidth, int padding, int lineHeight) {
        int penX = padding;
        int penY = padding;
        int rowHeight = 0;
        for (Glyph glyph : glyphs) {
            if (glyph.width == 0 || glyph.height == 0) {
                continue;
            }
            if (penX + glyph.width + padding > atlasWidth) {
                penX = padding;
                penY += rowHeight + padding;
                rowHeight = 0;
            }
            glyph.atlasX = penX;
            glyph.atlasY = penY;
            penX += glyph.width + padding;
            rowHeight = Math.max(rowHeight, glyph.height);
        }
        int used = penY + rowHeight + padding;
        return nextPowerOfTwo(Math.max(used, lineHeight + 2 * padding));
    }

    private static BufferedImage render(
            Font font, FontRenderContext frc, List<Glyph> glyphs, int atlasWidth, int atlasHeight) {
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setColor(Color.WHITE);
        for (Glyph glyph : glyphs) {
            if (glyph.width == 0 || glyph.height == 0) {
                continue;
            }
            GlyphVector vector = font.createGlyphVector(frc, new String(Character.toChars(glyph.codepoint)));
            // Drawing at (drawX, drawY) puts ink at (drawX + xOffset, drawY + inkY); solve so the
            // ink box top-left lands at the packed cell (atlasX, atlasY).
            float drawX = glyph.atlasX - glyph.xOffset;
            float drawY = glyph.atlasY - glyph.inkY;
            graphics.drawGlyphVector(vector, drawX, drawY);
        }
        graphics.dispose();
        return atlas;
    }

    private static String descriptor(
            Font font,
            Options options,
            List<Glyph> glyphs,
            int ascent,
            int lineHeight,
            int atlasHeight) {
        StringBuilder builder = new StringBuilder();
        String face = font.getFontName().replace("\"", "");
        builder.append("info face=\"").append(face).append("\" size=").append(font.getSize())
                .append(" bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=4")
                .append(" padding=0,0,0,0 spacing=").append(options.padding()).append(',').append(options.padding())
                .append(" outline=0\n");
        builder.append("common lineHeight=").append(lineHeight).append(" base=").append(ascent)
                .append(" scaleW=").append(options.atlasWidth()).append(" scaleH=").append(atlasHeight)
                .append(" pages=1 packed=0 alphaChnl=1 redChnl=0 greenChnl=0 blueChnl=0\n");
        builder.append("page id=0 file=\"").append(options.pageFileName()).append("\"\n");
        builder.append("chars count=").append(glyphs.size()).append('\n');
        for (Glyph glyph : glyphs) {
            builder.append("char id=").append(glyph.codepoint)
                    .append(" x=").append(glyph.atlasX)
                    .append(" y=").append(glyph.atlasY)
                    .append(" width=").append(glyph.width)
                    .append(" height=").append(glyph.height)
                    .append(" xoffset=").append(glyph.xOffset)
                    .append(" yoffset=").append(glyph.yOffset)
                    .append(" xadvance=").append(glyph.advance)
                    .append(" page=0 chnl=15\n");
        }
        return builder.toString().stripTrailing();
    }

    private static Graphics2D scratchGraphics() {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return scratch.createGraphics();
    }

    private static int nextPowerOfTwo(int value) {
        int result = 16;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    private static final class Glyph {
        private int codepoint;
        private int advance;
        private int width;
        private int height;
        private int xOffset;
        private int yOffset;
        private int inkY;
        private int atlasX;
        private int atlasY;
    }
}
