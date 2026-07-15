package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.ResourceIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds a deterministic image-only resource index from an explicit logical-path selection. */
final class ResourceIndexSubset {
    private static final long MAX_SELECTION_BYTES = 8L * 1024 * 1024;
    private static final int MAX_PATHS = 100_000;
    private static final int MAX_LINE_CHARS = 4_096;
    private static final int DIAGNOSTIC_LIMIT = 200;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "gif", "wbmp", "webp", "tga");

    private ResourceIndexSubset() {
    }

    static Result select(ResourceIndex source, Path selectionFile) throws IOException {
        Path absolute = selectionFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Texture path selection does not exist: " + absolute);
        }
        long bytes = Files.size(absolute);
        if (bytes > MAX_SELECTION_BYTES) {
            throw new IOException("Texture path selection exceeds " + MAX_SELECTION_BYTES + " bytes: " + absolute);
        }

        TreeSet<String> requested = new TreeSet<>();
        List<String> diagnostics = new ArrayList<>();
        long invalid = 0;
        List<String> lines = Files.readAllLines(absolute, StandardCharsets.UTF_8);
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String sourceLine = lines.get(lineNumber - 1);
            if (sourceLine.length() > MAX_LINE_CHARS) {
                invalid++;
                diagnostic(diagnostics, "Line " + lineNumber + " exceeds " + MAX_LINE_CHARS + " characters");
                continue;
            }
            String raw = sourceLine.trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            try {
                requested.add(ResourceIndex.normalizeLogicalPath(raw));
            } catch (RuntimeException error) {
                invalid++;
                diagnostic(diagnostics, "Line " + lineNumber + " is not a valid logical resource path: "
                        + message(error));
            }
            if (requested.size() > MAX_PATHS) {
                throw new IOException("Texture path selection exceeds " + MAX_PATHS + " unique paths");
            }
        }
        if (requested.isEmpty()) {
            throw new IOException("Texture path selection contains no valid logical paths: " + absolute);
        }

        TreeMap<String, List<ResourceIndex.Provider>> selected = new TreeMap<>();
        long missing = 0;
        long nonImage = 0;
        for (String logicalPath : requested) {
            List<ResourceIndex.Provider> providers = source.entries().get(logicalPath);
            if (providers == null) {
                missing++;
                diagnostic(diagnostics, "Requested texture is absent from the active resource index: " + logicalPath);
                continue;
            }
            if (!IMAGE_EXTENSIONS.contains(extension(logicalPath))) {
                nonImage++;
                diagnostic(diagnostics, "Requested resource is not a supported image: " + logicalPath);
                continue;
            }
            selected.put(logicalPath, providers);
        }
        if (selected.isEmpty()) {
            throw new IOException("Texture path selection matched no supported winning image resources");
        }

        String selectionFingerprint = fingerprint(source.profileFingerprint(), selected.keySet());
        ResourceIndex subset = new ResourceIndex(selectionFingerprint, source.roots(), selected);
        return new Result(
                subset,
                absolute,
                source.profileFingerprint(),
                selectionFingerprint,
                requested.size(),
                selected.size(),
                missing,
                nonImage,
                invalid,
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static String fingerprint(String profileFingerprint, Set<String> selectedPaths) {
        StringBuilder canonical = new StringBuilder(128 + selectedPaths.size() * 64)
                .append("starsector-preflight-resource-subset-v1\n")
                .append(profileFingerprint)
                .append('\n');
        for (String logicalPath : selectedPaths) {
            canonical.append(logicalPath).append('\n');
        }
        return Hashes.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot <= slash || dot == path.length() - 1
                ? ""
                : path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static void diagnostic(List<String> diagnostics, String detail) {
        if (diagnostics.size() < DIAGNOSTIC_LIMIT) {
            diagnostics.add(detail);
        }
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    record Result(
            ResourceIndex index,
            Path selectionFile,
            String sourceProfileFingerprint,
            String selectionFingerprint,
            long requestedPaths,
            long selectedPaths,
            long missingPaths,
            long nonImagePaths,
            long invalidPaths,
            List<String> diagnostics) {
        Result {
            diagnostics = List.copyOf(diagnostics);
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            values.put("selectionFile", selectionFile);
            values.put("sourceProfileFingerprint", sourceProfileFingerprint);
            values.put("selectionFingerprint", selectionFingerprint);
            values.put("requestedPaths", requestedPaths);
            values.put("selectedPaths", selectedPaths);
            values.put("missingPaths", missingPaths);
            values.put("nonImagePaths", nonImagePaths);
            values.put("invalidPaths", invalidPaths);
            values.put("diagnostics", diagnostics);
            return values;
        }
    }
}
