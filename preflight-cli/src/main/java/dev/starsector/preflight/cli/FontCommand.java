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
        if (offset >= args.length || !"generate".equals(args[offset])) {
            throw new IllegalArgumentException(
                    "Expected: font generate (--ttf <font.ttf> | --logical <name>) --size <px> "
                            + "--name <basename> --out-dir <dir> [--atlas-width <n>] [--padding <n>] "
                            + "[--charset-from <font.fnt> | --ascii | --latin1]");
        }
        Options options = parse(args, offset + 1);

        Font baseFont = loadFont(options).deriveFont((float) options.size);
        List<Integer> codepoints = resolveCodepoints(options);
        FontAtlasGenerator.Result result = FontAtlasGenerator.generate(
                baseFont,
                new FontAtlasGenerator.Options(
                        options.atlasWidth, options.padding, options.name + "_0.png", codepoints));

        Files.createDirectories(options.outDir);
        Path descriptorFile = options.outDir.resolve(options.name + ".fnt");
        Path atlasFile = options.outDir.resolve(options.name + "_0.png");
        Files.writeString(descriptorFile, result.descriptor() + "\n", StandardCharsets.UTF_8);
        if (!ImageIO.write(result.atlas(), "png", atlasFile.toFile())) {
            throw new IOException("No PNG writer was available for the font atlas");
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("descriptor", descriptorFile.toAbsolutePath().normalize());
        report.put("atlas", atlasFile.toAbsolutePath().normalize());
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

    private static Font loadFont(Options options) throws IOException {
        if (options.ttf != null) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, options.ttf.toFile());
            } catch (FontFormatException error) {
                throw new IOException("Not a usable TrueType/OpenType font: " + options.ttf, error);
            }
        }
        return new Font(options.logicalAwtName(), Font.PLAIN, options.size);
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

        private String logicalAwtName() {
            return switch (logical.toLowerCase(java.util.Locale.ROOT)) {
                case "serif" -> Font.SERIF;
                case "monospaced", "mono" -> Font.MONOSPACED;
                case "sans-serif", "sans", "sansserif" -> Font.SANS_SERIF;
                default -> throw new IllegalArgumentException(
                        "--logical must be sans-serif, serif, or monospaced");
            };
        }
    }
}
