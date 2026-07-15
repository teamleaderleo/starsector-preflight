package dev.starsector.preflight.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads a small line-oriented allowlist of exact adapter targets. */
final class AdapterTargetRegistry {
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

    static AdapterTargetRegistry load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<AdapterTarget> targets = new ArrayList<>();
        Builder builder = null;
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String raw = lines.get(lineNumber - 1).trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            int separator = raw.indexOf(' ');
            String key = separator < 0 ? raw : raw.substring(0, separator).trim();
            String value = separator < 0 ? "" : raw.substring(separator + 1).trim();
            switch (key) {
                case "target" -> {
                    if (builder != null) {
                        throw syntax(path, lineNumber, "Nested target block");
                    }
                    builder = new Builder(value);
                }
                case "class" -> requireBuilder(path, lineNumber, builder).className = value;
                case "sha256" -> requireBuilder(path, lineNumber, builder).sha256 = value;
                case "plan" -> requireBuilder(path, lineNumber, builder).planId = value;
                case "method" -> {
                    Builder active = requireBuilder(path, lineNumber, builder);
                    int split = value.indexOf(' ');
                    if (split <= 0 || split == value.length() - 1) {
                        throw syntax(path, lineNumber, "Expected: method <name> <descriptor>");
                    }
                    active.methods.add(new AdapterTarget.RequiredMethod(
                            value.substring(0, split).trim(),
                            value.substring(split + 1).trim()));
                }
                case "end" -> {
                    if (!value.isEmpty()) {
                        throw syntax(path, lineNumber, "end does not accept a value");
                    }
                    Builder active = requireBuilder(path, lineNumber, builder);
                    targets.add(active.build(path, lineNumber));
                    builder = null;
                }
                default -> throw syntax(path, lineNumber, "Unknown directive: " + key);
            }
        }
        if (builder != null) {
            throw new IOException("Unterminated target block in " + path.toAbsolutePath().normalize());
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
        private final List<AdapterTarget.RequiredMethod> methods = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        private AdapterTarget build(Path path, int lineNumber) throws IOException {
            try {
                return new AdapterTarget(id, className, sha256, planId, methods);
            } catch (RuntimeException error) {
                throw syntax(path, lineNumber, error.getMessage());
            }
        }
    }
}