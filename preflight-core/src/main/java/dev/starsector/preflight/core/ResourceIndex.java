package dev.starsector.preflight.core;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable mapping from Starsector logical resource paths to ordered physical providers.
 *
 * <p>Provider order is resolution order: core first, then enabled mods. The last provider is
 * therefore the winning provider for ordinary override lookup.</p>
 */
public final class ResourceIndex {
    public static final int FORMAT_VERSION = 1;

    private final String profileFingerprint;
    private final List<Root> roots;
    private final NavigableMap<String, List<Provider>> entries;
    private final long providerCount;

    public ResourceIndex(
            String profileFingerprint,
            List<Root> roots,
            Map<String, ? extends List<Provider>> entries) {
        if (profileFingerprint == null || profileFingerprint.isBlank()) {
            throw new IllegalArgumentException("profileFingerprint is required");
        }
        this.profileFingerprint = profileFingerprint;
        this.roots = List.copyOf(roots);
        if (this.roots.isEmpty()) {
            throw new IllegalArgumentException("At least one resource root is required");
        }

        TreeMap<String, List<Provider>> copy = new TreeMap<>();
        long providers = 0;
        for (Map.Entry<String, ? extends List<Provider>> entry : entries.entrySet()) {
            String key = normalizeLogicalPath(entry.getKey());
            List<Provider> value = List.copyOf(entry.getValue());
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Resource entry has no providers: " + key);
            }
            for (Provider provider : value) {
                if (provider.rootIndex() < 0 || provider.rootIndex() >= this.roots.size()) {
                    throw new IllegalArgumentException("Provider root index is out of range for " + key);
                }
                normalizeRelativePath(provider.relativePath());
            }
            List<Provider> prior = copy.put(key, value);
            if (prior != null) {
                throw new IllegalArgumentException("Duplicate normalized resource path: " + key);
            }
            providers += value.size();
        }
        this.entries = Collections.unmodifiableNavigableMap(copy);
        this.providerCount = providers;
    }

    public String profileFingerprint() {
        return profileFingerprint;
    }

    public List<Root> roots() {
        return roots;
    }

    public NavigableMap<String, List<Provider>> entries() {
        return entries;
    }

    public int entryCount() {
        return entries.size();
    }

    public long providerCount() {
        return providerCount;
    }

    public List<Provider> providers(String logicalPath) {
        List<Provider> providers = entries.get(normalizeLogicalPath(logicalPath));
        return providers == null ? List.of() : providers;
    }

    public Optional<Provider> winner(String logicalPath) {
        List<Provider> providers = providers(logicalPath);
        return providers.isEmpty() ? Optional.empty() : Optional.of(providers.get(providers.size() - 1));
    }

    public Optional<Path> winningFile(String logicalPath) {
        return winner(logicalPath).map(this::resolve);
    }

    public Path resolve(Provider provider) {
        Root root = roots.get(provider.rootIndex());
        Path rootPath = root.path().toAbsolutePath().normalize();
        Path resolved = rootPath.resolve(provider.relativePath()).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException("Provider escapes its resource root: " + provider.relativePath());
        }
        return resolved;
    }

    public static String normalizeLogicalPath(String raw) {
        return normalize(raw, true).toLowerCase(Locale.ROOT);
    }

    public static String normalizeRelativePath(String raw) {
        return normalize(raw, false);
    }

    private static String normalize(String raw, boolean lowercaseKey) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Resource path is required");
        }
        String value = raw.replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Resource path must be relative: " + raw);
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : value.split("/+")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Resource path may not contain '..': " + raw);
            }
            segments.addLast(segment);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Resource path is empty after normalization: " + raw);
        }
        String normalized = String.join("/", new ArrayList<>(segments));
        return lowercaseKey ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    public record Root(String id, Path path, boolean core) {
        public Root {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Root id is required");
            }
            if (path == null) {
                throw new IllegalArgumentException("Root path is required");
            }
            path = path.toAbsolutePath().normalize();
        }
    }

    public record Provider(int rootIndex, String relativePath, long size, long modifiedMillis) {
        public Provider {
            relativePath = normalizeRelativePath(relativePath);
            if (size < 0) {
                throw new IllegalArgumentException("Provider size may not be negative");
            }
            if (modifiedMillis < 0) {
                throw new IllegalArgumentException("Provider modification time may not be negative");
            }
        }
    }
}
