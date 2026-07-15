package dev.starsector.preflight.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads a small line-oriented allowlist of exact adapter targets. */
final class AdapterTargetRegistry {
    private static final long MAX_FILE_BYTES = 1L * 1024 * 1024;
    private static final int MAX_LINE_CHARS = 4_096;
    private static final int MAX_TARGETS = 256;
    private static final int MAX_METHODS_PER_TARGET = 128;
    private static final String VANILLA_TEXTURE_LOADER_SHA256 =
            "d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50";

    private final List<AdapterTarget> targets;
    private final Map<String, List<AdapterTarget>> byClass;

    private AdapterTargetRegistry(List<AdapterTarget> targets) {
        this.targets = List.copyOf(targets);
        Map<String, List<AdapterTarget>> indexed = new LinkedHashMap<>();
        for (AdapterTarget target : targets) {
            indexed.computeIfAbsent(target.internalClassName(), ignored -> new ArrayList<>()).add(target);
        }
        Map<String, List<AdapterTarget>> frozen = new LinkedHashMap<>();
        indexed.forEach((name, values) -> frozen.put(name, List.copyOf(values)));
        this.byClass = Map.copyOf(frozen);
    }

    static AdapterTargetRegistry empty() {
        return new AdapterTargetRegistry(List.of());
    }

    static AdapterTargetRegistry builtInPreparedImage() {
        return new AdapterTargetRegistry(List.of(new AdapterTarget(
                "starsector-0.98a-rc8-vanilla-prepared-image",
                "com/fs/graphics/TextureLoader",
                VANILLA_TEXTURE_LOADER_SHA256,
                PreparedImageTransformation.PLAN_ID,
                List.of(new AdapterTarget.RequiredMethod(
                        PreparedImageTransformation.METHOD_NAME,
                        PreparedImageTransformation.METHOD_DESCRIPTOR)),
                "STARSECTOR_CORE",
                "Contents/Resources/Java/fs.common_obf.jar",
                "",
                "jdk/internal/loader/ClassLoaders$AppClassLoader",
                "app")));
    }

    static AdapterTargetRegistry load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path absolute = path.toAbsolutePath().normalize();
        long bytes = Files.size(absolute);
        if (bytes > MAX_FILE_BYTES) {
            throw new IOException("Adapter target registry exceeds " + MAX_FILE_BYTES + " bytes: " + absolute);
        }

        List<String> lines = Files.readAllLines(absolute, StandardCharsets.UTF_8);
        List<AdapterTarget> targets = new ArrayList<>();
        Set<String> targetIds = new HashSet<>();
        Builder builder = null;
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String sourceLine = lines.get(lineNumber - 1);
            if (sourceLine.length() > MAX_LINE_CHARS) {
                throw syntax(absolute, lineNumber, "Line exceeds " + MAX_LINE_CHARS + " characters");
            }
            String raw = sourceLine.trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            int separator = raw.indexOf(' ');
            String key = separator < 0 ? raw : raw.substring(0, separator).trim();
            String value = separator < 0 ? "" : raw.substring(separator + 1).trim();
            switch (key) {
                case "target" -> {
                    if (builder != null) {
                        throw syntax(absolute, lineNumber, "Nested target block");
                    }
                    builder = new Builder(value);
                }
                case "class" -> requireBuilder(absolute, lineNumber, builder).className = value;
                case "sha256" -> requireBuilder(absolute, lineNumber, builder).sha256 = value;
                case "plan" -> requireBuilder(absolute, lineNumber, builder).planId = value;
                case "source-kind" -> requireBuilder(absolute, lineNumber, builder).sourceKind = value;
                case "source-suffix" -> requireBuilder(absolute, lineNumber, builder).sourceSuffix = value;
                case "source-sha256" -> requireBuilder(absolute, lineNumber, builder).sourceSha256 = value;
                case "loader-class" -> requireBuilder(absolute, lineNumber, builder).loaderClass = value;
                case "loader-name" -> requireBuilder(absolute, lineNumber, builder).loaderName = value;
                case "method" -> {
                    Builder active = requireBuilder(absolute, lineNumber, builder);
                    if (active.methods.size() >= MAX_METHODS_PER_TARGET) {
                        throw syntax(absolute, lineNumber,
                                "Target exceeds " + MAX_METHODS_PER_TARGET + " required methods");
                    }
                    int split = value.indexOf(' ');
                    if (split <= 0 || split == value.length() - 1) {
                        throw syntax(absolute, lineNumber, "Expected: method <name> <descriptor>");
                    }
                    active.methods.add(new AdapterTarget.RequiredMethod(
                            value.substring(0, split).trim(),
                            value.substring(split + 1).trim()));
                }
                case "end" -> {
                    if (!value.isEmpty()) {
                        throw syntax(absolute, lineNumber, "end does not accept a value");
                    }
                    Builder active = requireBuilder(absolute, lineNumber, builder);
                    AdapterTarget target = active.build(absolute, lineNumber);
                    if (!targetIds.add(target.id())) {
                        throw syntax(absolute, lineNumber, "Duplicate target ID: " + target.id());
                    }
                    if (targets.size() >= MAX_TARGETS) {
                        throw syntax(absolute, lineNumber, "Registry exceeds " + MAX_TARGETS + " targets");
                    }
                    targets.add(target);
                    builder = null;
                }
                default -> throw syntax(absolute, lineNumber, "Unknown directive: " + key);
            }
        }
        if (builder != null) {
            throw new IOException("Unterminated target block in " + absolute);
        }
        return new AdapterTargetRegistry(targets);
    }

    List<AdapterTarget> targets() {
        return targets;
    }

    List<AdapterTarget> forClass(String internalName) {
        return byClass.getOrDefault(internalName, List.of());
    }

    private static Builder requireBuilder(Path path, int lineNumber, Builder builder) throws IOException {
        if (builder == null) {
            throw syntax(path, lineNumber, "Directive must appear inside a target block");
        }
        return builder;
    }

    private static IOException syntax(Path path, int lineNumber, String detail) {
        return new IOException(path.toAbsolutePath().normalize() + ":" + lineNumber + ": " + detail);
    }

    private static final class Builder {
        private final String id;
        private String className;
        private String sha256;
        private String planId = "none";
        private String sourceKind = "";
        private String sourceSuffix = "";
        private String sourceSha256 = "";
        private String loaderClass = "";
        private String loaderName = "";
        private final List<AdapterTarget.RequiredMethod> methods = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        private AdapterTarget build(Path path, int lineNumber) throws IOException {
            try {
                return new AdapterTarget(
                        id,
                        className,
                        sha256,
                        planId,
                        methods,
                        sourceKind,
                        sourceSuffix,
                        sourceSha256,
                        loaderClass,
                        loaderName);
            } catch (RuntimeException error) {
                throw syntax(path, lineNumber, error.getMessage());
            }
        }
    }
}
