package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Offline font-pack generator: re-rasterizes a vector font into a Starsector-compatible
 * AngelCode BMFont pair ({@code <name>.fnt} + {@code <name>_0.png}). Generating at N&times; a
 * font's on-screen size and drawing it at that size yields supersampled, crisp same-size text
 * (see {@code docs/asset-quality-track.md}). Ships no fonts: the vector source is supplied by
 * the operator (a TTF they hold, or a bundled logical font), keeping licensing with the user.
 */
final class FontCommand {
    private FontCommand() {
    }

    static int execute(String[] args, int offset) throws IOException {
        if (offset >= args.length) {
            throw new IllegalArgumentException("Expected: font <generate|generate-pack> ...");
        }
        return switch (args[offset]) {
            case "generate" -> generateOne(args, offset + 1);
            case "generate-pack" -> generatePack(args, offset + 1);
            default -> throw new IllegalArgumentException("Unknown font command: " + args[offset]);
        };
    }

    private static int generateOne(String[] args, int offset) throws IOException {
        Options options = parse(args, offset);

        Font baseFont = loadFont(options).deriveFont((float) options.size);
        List<Integer> codepoints = resolveCodepoints(options);
        FontAtlasGenerator.Result result = FontAtlasGenerator.generate(
                baseFont,
                new FontAtlasGenerator.Options(
                        options.atlasWidth, options.padding, options.name + "_0.png", codepoints));

        Files.createDirectories(options.outDir);
        Written written = writeFont(result, options.outDir, options.name);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("descriptor", written.descriptor.toAbsolutePath().normalize());
        report.put("atlas", written.atlas.toAbsolutePath().normalize());
        report.put("source", options.ttf != null ? options.ttf.toAbsolutePath().normalize() : options.logical);
        report.put("family", baseFont.getFontName());
        report.put("size", options.size);
        report.put("glyphCount", result.glyphCount());
        report.put("baseHeight", result.baseHeight());
        report.put("atlasWidth", result.atlas().getWidth());
        report.put("atlasHeight", result.atlas().getHeight());
        System.out.println(Json.object(report));
        return 0;
    }

    /**
     * Generates matched replacements for every {@code .fnt} in a source directory from one TTF,
     * assembling a complete drop-in Starsector font mod. Each replacement matches the original's
     * on-screen size ({@code base * scale}) and glyph coverage. One JVM, TTF loaded once.
     */
    private static int generatePack(String[] args, int offset) throws IOException {
        PackOptions options = parsePack(args, offset);
        Font ttf = loadBaseFont(options.ttf, options.logical);

        Path outFonts = options.outDir.resolve("graphics").resolve("fonts");
        Files.createDirectories(outFonts);
        List<Path> sources;
        try (var stream = Files.list(options.fontsDir)) {
            sources = stream.filter(path -> path.getFileName().toString().endsWith(".fnt")).sorted().toList();
        }

        List<Object> generated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Path source : sources) {
            String name = source.getFileName().toString();
            name = name.substring(0, name.length() - ".fnt".length());
            BitmapFont original = BitmapFont.parse(Files.readString(source, StandardCharsets.UTF_8));
            int base;
            List<Integer> ids;
            try {
                base = original.baseHeight();
                ids = original.charIds();
            } catch (RuntimeException malformed) {
                skipped.add(name + " (unreadable)");
                continue;
            }
            int size = base * options.scale;
            if (size <= 0 || ids.isEmpty()) {
                skipped.add(name + " (empty)");
                continue;
            }
            FontAtlasGenerator.Result result = FontAtlasGenerator.generate(
                    ttf.deriveFont((float) size),
                    new FontAtlasGenerator.Options(options.atlasWidth, options.padding, name + "_0.png", ids));
            writeFont(result, outFonts, name);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("size", size);
            entry.put("glyphs", result.glyphCount());
            generated.add(Map.copyOf(entry));
        }

        writeModInfo(options);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("modDirectory", options.outDir.toAbsolutePath().normalize());
        report.put("source", options.ttf != null ? options.ttf.toAbsolutePath().normalize() : options.logical);
        report.put("family", ttf.getFontName());
        report.put("scale", options.scale);
        report.put("fontsGenerated", generated.size());
        report.put("fontsSkipped", skipped);
        report.put("fonts", generated);
        System.out.println(Json.object(report));
        return 0;
    }

    private static Written writeFont(FontAtlasGenerator.Result result, Path fontsDir, String name)
            throws IOException {
        Path descriptor = fontsDir.resolve(name + ".fnt");
        Path atlas = fontsDir.resolve(name + "_0.png");
        Files.writeString(descriptor, result.descriptor() + "\n", StandardCharsets.UTF_8);
        if (!ImageIO.write(result.atlas(), "png", atlas.toFile())) {
            throw new IOException("No PNG writer was available for the font atlas: " + name);
        }
        return new Written(descriptor, atlas);
    }

    private static void writeModInfo(PackOptions options) throws IOException {
        Map<String, Object> modInfo = new LinkedHashMap<>();
        modInfo.put("id", options.modId);
        modInfo.put("name", options.modName);
        modInfo.put("author", "preflight");
        modInfo.put("version", "0.1");
        modInfo.put("gameVersion", options.gameVersion);
        modInfo.put("description",
                "Replaces the UI fonts with " + options.modName + " (scale " + options.scale + "x).");
        modInfo.put("utility", true);
        Files.writeString(options.outDir.resolve("mod_info.json"), Json.object(modInfo) + "\n",
                StandardCharsets.UTF_8);
    }

    private record Written(Path descriptor, Path atlas) {
    }

    private static Font loadFont(Options options) throws IOException {
        return loadBaseFont(options.ttf, options.logical);
    }

    private static Font loadBaseFont(Path ttf, String logical) throws IOException {
        if (ttf != null) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, ttf.toFile());
            } catch (FontFormatException error) {
                throw new IOException("Not a usable TrueType/OpenType font: " + ttf, error);
            }
        }
        return new Font(logicalAwtName(logical), Font.PLAIN, 12);
    }

    private static String logicalAwtName(String logical) {
        return switch (logical.toLowerCase(java.util.Locale.ROOT)) {
            case "serif" -> Font.SERIF;
            case "monospaced", "mono" -> Font.MONOSPACED;
            case "sans-serif", "sans", "sansserif" -> Font.SANS_SERIF;
            default -> throw new IllegalArgumentException("--logical must be sans-serif, serif, or monospaced");
        };
    }

    private static List<Integer> resolveCodepoints(Options options) throws IOException {
        if (options.charsetFrom != null) {
            return BitmapFont.parse(Files.readString(options.charsetFrom, StandardCharsets.UTF_8)).charIds();
        }
        List<Integer> codepoints = new ArrayList<>();
        for (int codepoint = 32; codepoint <= 126; codepoint++) {
            codepoints.add(codepoint);
        }
        if (options.latin1) {
            for (int codepoint = 160; codepoint <= 255; codepoint++) {
                codepoints.add(codepoint);
            }
        }
        return codepoints;
    }

    private static Options parse(String[] args, int offset) {
        Options options = new Options();
        for (int i = offset; i < args.length; i++) {
            switch (args[i]) {
                case "--ttf" -> options.ttf = Path.of(requireValue(args, ++i, "--ttf"));
                case "--logical" -> options.logical = requireValue(args, ++i, "--logical");
                case "--size" -> options.size = Integer.parseInt(requireValue(args, ++i, "--size"));
                case "--name" -> options.name = requireValue(args, ++i, "--name");
                case "--out-dir" -> options.outDir = Path.of(requireValue(args, ++i, "--out-dir"));
                case "--atlas-width" ->
                        options.atlasWidth = Integer.parseInt(requireValue(args, ++i, "--atlas-width"));
                case "--padding" -> options.padding = Integer.parseInt(requireValue(args, ++i, "--padding"));
                case "--charset-from" -> options.charsetFrom = Path.of(requireValue(args, ++i, "--charset-from"));
                case "--ascii" -> options.latin1 = false;
                case "--latin1" -> options.latin1 = true;
                default -> throw new IllegalArgumentException("Unknown font generate option: " + args[i]);
            }
        }
        options.validate();
        return options;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static PackOptions parsePack(String[] args, int offset) {
        PackOptions options = new PackOptions();
        for (int i = offset; i < args.length; i++) {
            switch (args[i]) {
                case "--ttf" -> options.ttf = Path.of(requireValue(args, ++i, "--ttf"));
                case "--logical" -> options.logical = requireValue(args, ++i, "--logical");
                case "--fonts-dir" -> options.fontsDir = Path.of(requireValue(args, ++i, "--fonts-dir"));
                case "--out-dir" -> options.outDir = Path.of(requireValue(args, ++i, "--out-dir"));
                case "--scale" -> options.scale = Integer.parseInt(requireValue(args, ++i, "--scale"));
                case "--atlas-width" ->
                        options.atlasWidth = Integer.parseInt(requireValue(args, ++i, "--atlas-width"));
                case "--padding" -> options.padding = Integer.parseInt(requireValue(args, ++i, "--padding"));
                case "--mod-id" -> options.modId = requireValue(args, ++i, "--mod-id");
                case "--mod-name" -> options.modName = requireValue(args, ++i, "--mod-name");
                case "--game-version" -> options.gameVersion = requireValue(args, ++i, "--game-version");
                default -> throw new IllegalArgumentException("Unknown font generate-pack option: " + args[i]);
            }
        }
        options.validate();
        return options;
    }

    private static final class PackOptions {
        private Path ttf;
        private String logical;
        private Path fontsDir;
        private Path outDir;
        private int scale = 1;
        private int atlasWidth = 512;
        private int padding = 1;
        private String modId = "preflight_font_pack";
        private String modName = "Preflight Font Pack";
        private String gameVersion = "0.98a-RC8";

        private void validate() {
            if ((ttf == null) == (logical == null)) {
                throw new IllegalArgumentException("Provide exactly one of --ttf or --logical");
            }
            if (fontsDir == null) {
                throw new IllegalArgumentException("--fonts-dir is required (the game's graphics/fonts directory)");
            }
            if (outDir == null) {
                throw new IllegalArgumentException("--out-dir is required (the mod directory to create)");
            }
            if (scale < 1) {
                throw new IllegalArgumentException("--scale must be a positive integer");
            }
        }
    }

    private static final class Options {
        private Path ttf;
        private String logical;
        private int size = -1;
        private String name;
        private Path outDir;
        private int atlasWidth = 512;
        private int padding = 1;
        private Path charsetFrom;
        private boolean latin1;

        private void validate() {
            if ((ttf == null) == (logical == null)) {
                throw new IllegalArgumentException("Provide exactly one of --ttf or --logical");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("--size must be a positive pixel size");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("--name is required");
            }
            if (outDir == null) {
                throw new IllegalArgumentException("--out-dir is required");
            }
        }
    }
}
