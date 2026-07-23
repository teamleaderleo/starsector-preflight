package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser, serializer, and integer-scale transform for AngelCode BMFont text {@code .fnt}
 * descriptors — the format Starsector uses under {@code graphics/fonts/}.
 *
 * <p>Starsector renders these fonts by scaling glyphs from the atlas's native size (its
 * {@code baseHeight}) down or up to the requested draw size; LazyFont documents that a draw
 * size evenly divisible by {@code baseHeight} renders best. Producing an {@code N}&times;
 * descriptor (every coordinate scaled by an integer factor, paired with a genuinely higher
 * resolution {@code N}&times; atlas) is therefore the input a same-size supersampled render
 * consumes. This class handles only the descriptor's exact integer arithmetic; generating a
 * true high-resolution atlas (re-rasterizing from a vector font) is a separate step, since
 * upscaling the baked atlas would add no real detail.
 *
 * <p>Parsing is attribute-preserving: unknown lines and attributes round-trip verbatim, and
 * only the well-defined pixel-coordinate attributes are scaled.
 */
final class BitmapFont {
    /** Attributes scaled per tag. Percentages, ids, channels, flags, and page files are left alone. */
    private static final Map<String, Set<String>> SCALED_ATTRIBUTES = Map.of(
            "info", Set.of("size", "padding", "spacing", "outline"),
            "common", Set.of("lineHeight", "base", "scaleW", "scaleH"),
            "char", Set.of("x", "y", "width", "height", "xoffset", "yoffset", "xadvance"),
            "kerning", Set.of("amount"));

    /**
     * Tags parsed into structured attributes for scaling and round-tripping. Everything else
     * (the {@code chars}/{@code kernings} count lines, blanks, comments) passes through verbatim.
     */
    private static final Set<String> PARSED_TAGS = Set.of("info", "common", "page", "char", "kerning");

    private final List<Line> lines;

    private BitmapFont(List<Line> lines) {
        this.lines = lines;
    }

    static BitmapFont parse(String text) {
        List<Line> parsed = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            parsed.add(Line.parse(raw));
        }
        return new BitmapFont(parsed);
    }

    /** Returns a copy with every pixel-coordinate attribute multiplied by {@code factor}. */
    BitmapFont scaled(int factor) {
        if (factor < 1) {
            throw new IllegalArgumentException("scale factor must be positive");
        }
        List<Line> scaledLines = new ArrayList<>(lines.size());
        for (Line line : lines) {
            scaledLines.add(line.scaled(factor));
        }
        return new BitmapFont(scaledLines);
    }

    /** The declared native height ({@code common base}) used as the scaling baseline. */
    int baseHeight() {
        for (Line line : lines) {
            if (line.tag.equals("common") && line.attributes.containsKey("base")) {
                return Integer.parseInt(line.attributes.get("base"));
            }
        }
        throw new IllegalStateException("No common/base line in font descriptor");
    }

    /** The atlas page file names referenced by the descriptor, in order. */
    List<String> pageFiles() {
        List<String> files = new ArrayList<>();
        for (Line line : lines) {
            if (line.tag.equals("page") && line.attributes.containsKey("file")) {
                files.add(stripQuotes(line.attributes.get("file")));
            }
        }
        return List.copyOf(files);
    }

    String serialize() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i).serialize());
            if (i < lines.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** One descriptor line: a tag plus ordered {@code key=value} attributes, or raw passthrough. */
    private static final class Line {
        private final String raw;
        private final String tag;
        private final LinkedHashMap<String, String> attributes;

        private Line(String raw, String tag, LinkedHashMap<String, String> attributes) {
            this.raw = raw;
            this.tag = tag;
            this.attributes = attributes;
        }

        static Line parse(String raw) {
            String trimmed = raw.strip();
            if (trimmed.isEmpty()) {
                return new Line(raw, "", new LinkedHashMap<>());
            }
            List<String> tokens = tokenize(trimmed);
            String tag = tokens.get(0);
            if (!PARSED_TAGS.contains(tag)) {
                return new Line(raw, tag, new LinkedHashMap<>());
            }
            LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
            for (int i = 1; i < tokens.size(); i++) {
                String token = tokens.get(i);
                int equals = token.indexOf('=');
                if (equals < 0) {
                    // Unexpected shape; keep the whole line raw to avoid corrupting it.
                    return new Line(raw, tag, new LinkedHashMap<>());
                }
                attributes.put(token.substring(0, equals), token.substring(equals + 1));
            }
            return new Line(null, tag, attributes);
        }

        Line scaled(int factor) {
            if (raw != null || attributes.isEmpty()) {
                return this;
            }
            Set<String> scalable = SCALED_ATTRIBUTES.getOrDefault(tag, Set.of());
            if (scalable.isEmpty()) {
                return this;
            }
            LinkedHashMap<String, String> scaledAttributes = new LinkedHashMap<>();
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                if (scalable.contains(attribute.getKey())) {
                    scaledAttributes.put(attribute.getKey(), scaleValue(attribute.getValue(), factor));
                } else {
                    scaledAttributes.put(attribute.getKey(), attribute.getValue());
                }
            }
            return new Line(null, tag, scaledAttributes);
        }

        String serialize() {
            if (raw != null) {
                return raw;
            }
            StringBuilder builder = new StringBuilder(tag);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                builder.append(' ').append(attribute.getKey()).append('=').append(attribute.getValue());
            }
            return builder.toString();
        }

        private static String scaleValue(String value, int factor) {
            if (value.indexOf(',') >= 0) {
                String[] parts = value.split(",");
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(Integer.parseInt(parts[i].strip()) * factor);
                }
                return builder.toString();
            }
            return Integer.toString(Integer.parseInt(value.strip()) * factor);
        }

        /** Splits on whitespace while keeping double-quoted values (which may contain spaces) intact. */
        private static List<String> tokenize(String line) {
            List<String> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char character = line.charAt(i);
                if (character == '"') {
                    inQuotes = !inQuotes;
                    current.append(character);
                } else if (Character.isWhitespace(character) && !inQuotes) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(character);
                }
            }
            if (current.length() > 0) {
                tokens.add(current.toString());
            }
            return tokens;
        }
    }
}
