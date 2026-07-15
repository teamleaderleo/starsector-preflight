package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import dev.starsector.preflight.core.TextureManifestValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TextureManifestCommand {
    private TextureManifestCommand() {
    }

    static int execute(String[] args, int offset) throws IOException {
        if (offset >= args.length) {
            throw new IllegalArgumentException("Expected: texture manifest <inspect|query|validate> ...");
        }
        return switch (args[offset]) {
            case "inspect" -> inspect(requireSinglePath(args, offset + 1, "texture manifest inspect <manifest.spfm>"));
            case "query" -> query(parseQuery(args, offset + 1));
            case "validate" -> validate(parseValidate(args, offset + 1));
            default -> throw new IllegalArgumentException("Unknown texture manifest command: " + args[offset]);
        };
    }

    private static int inspect(Path manifestFile) throws IOException {
        TextureManifest manifest = TextureManifestIO.read(manifestFile);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("file", manifestFile.toAbsolutePath().normalize());
        report.put("fileBytes", Files.size(manifestFile));
        report.put("formatVersion", TextureManifest.FORMAT_VERSION);
        report.put("profileFingerprint", manifest.profileFingerprint());
        report.put("entryCount", manifest.entryCount());
        System.out.println(Json.object(report));
        return 0;
    }

    private static int query(QueryOptions options) throws IOException {
        TextureManifest manifest = TextureManifestIO.read(options.manifest());
        TextureManifest.Entry entry = manifest.entry(options.logicalPath()).orElse(null);
        if (entry == null) {
            System.out.println(Json.object(Map.of(
                    "path", options.logicalPath(),
                    "present", false)));
            return 4;
        }
        Path cacheRoot = options.cacheDirectory() == null
                ? inferCacheRoot(options.manifest())
                : options.cacheDirectory().toAbsolutePath().normalize();
        Map<String, Object> report = entryReport(options.logicalPath(), entry, cacheRoot);
        report.put("present", true);
        System.out.println(Json.object(report));
        return 0;
    }

    private static int validate(ValidateOptions options) throws IOException {
        TextureManifest manifest = TextureManifestIO.read(options.manifest());
        Path cacheRoot = options.cacheDirectory() == null
                ? inferCacheRoot(options.manifest())
                : options.cacheDirectory().toAbsolutePath().normalize();
        TextureManifestValidator.Result validation = TextureManifestValidator.validate(cacheRoot, manifest);
        List<Map<String, Object>> problems = validation.problems().stream().map(problem -> {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("kind", problem.kind());
            report.put("logicalPath", problem.logicalPath());
            report.put("blobRelativePath", problem.blobRelativePath());
            report.put("expected", problem.expected());
            report.put("actual", problem.actual());
            return report;
        }).toList();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("manifest", options.manifest().toAbsolutePath().normalize());
        report.put("cacheDirectory", cacheRoot);
        report.put("valid", validation.valid());
        report.put("checkedEntries", validation.checkedEntries());
        report.put("invalidEntries", validation.invalidEntries());
        report.put("truncated", validation.truncated());
        report.put("problems", problems);
        System.out.println(Json.object(report));
        return validation.valid() ? 0 : 5;
    }

    private static Map<String, Object> entryReport(
            String logicalPath,
            TextureManifest.Entry entry,
            Path cacheRoot) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("path", logicalPath);
        report.put("sourceSha256", entry.sourceSha256());
        report.put("transformation", entry.transformation());
        report.put("blobRelativePath", entry.blobRelativePath());
        report.put("blob", cacheRoot.resolve(entry.blobRelativePath()).normalize());
        report.put("width", entry.width());
        report.put("height", entry.height());
        report.put("channels", entry.channels());
        report.put("pixelBytes", entry.pixelBytes());
        return report;
    }

    private static Path inferCacheRoot(Path manifest) {
        Path absolute = manifest.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equals("manifests")
                && parent.getParent() != null) {
            return parent.getParent();
        }
        throw new IllegalArgumentException("Provide --cache-dir when the manifest is outside cache/manifests");
    }

    private static QueryOptions parseQuery(String[] args, int offset) {
        if (offset + 1 >= args.length) {
            throw new IllegalArgumentException(
                    "Expected: texture manifest query <manifest.spfm> <logical-path> [--cache-dir <path>]");
        }
        Path manifest = Path.of(args[offset]);
        String logicalPath = args[offset + 1];
        Path cacheDirectory = null;
        for (int i = offset + 2; i < args.length; i++) {
            if (args[i].equals("--cache-dir")) {
                cacheDirectory = Path.of(requireValue(args, ++i, "--cache-dir"));
            } else {
                throw new IllegalArgumentException("Unknown texture manifest query option: " + args[i]);
            }
        }
        return new QueryOptions(manifest, logicalPath, cacheDirectory);
    }

    private static ValidateOptions parseValidate(String[] args, int offset) {
        if (offset >= args.length) {
            throw new IllegalArgumentException(
                    "Expected: texture manifest validate <manifest.spfm> [--cache-dir <path>]");
        }
        Path manifest = Path.of(args[offset]);
        Path cacheDirectory = null;
        for (int i = offset + 1; i < args.length; i++) {
            if (args[i].equals("--cache-dir")) {
                cacheDirectory = Path.of(requireValue(args, ++i, "--cache-dir"));
            } else {
                throw new IllegalArgumentException("Unknown texture manifest validate option: " + args[i]);
            }
        }
        return new ValidateOptions(manifest, cacheDirectory);
    }

    private static Path requireSinglePath(String[] args, int offset, String usage) {
        if (offset >= args.length || offset + 1 != args.length) {
            throw new IllegalArgumentException("Expected: " + usage);
        }
        return Path.of(args[offset]);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private record QueryOptions(Path manifest, String logicalPath, Path cacheDirectory) {
    }

    private record ValidateOptions(Path manifest, Path cacheDirectory) {
    }
}
