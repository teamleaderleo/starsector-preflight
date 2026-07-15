package dev.starsector.preflight.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable ordered mapping from JAR entry names to archive providers. */
public final class ClasspathProfileIndex {
    public static final int FORMAT_VERSION = 1;

    private final String profileFingerprint;
    private final List<Archive> archives;
    private final NavigableMap<String, List<Integer>> providers;
    private final long providerCount;

    public ClasspathProfileIndex(
            String profileFingerprint,
            List<Archive> archives,
            Map<String, ? extends List<Integer>> providers) {
        Hashes.decodeSha256(profileFingerprint);
        this.profileFingerprint = profileFingerprint.toLowerCase(java.util.Locale.ROOT);
        this.archives = List.copyOf(archives);

        TreeMap<String, List<Integer>> copy = new TreeMap<>();
        long count = 0;
        for (Map.Entry<String, ? extends List<Integer>> item : providers.entrySet()) {
            String name = JarArchiveIndex.normalizeEntryName(item.getKey());
            List<Integer> values = List.copyOf(item.getValue());
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Classpath entry has no providers: " + name);
            }
            int previous = -1;
            for (int archiveIndex : values) {
                if (archiveIndex < 0 || archiveIndex >= this.archives.size()) {
                    throw new IllegalArgumentException("Classpath provider is out of range for " + name);
                }
                if (archiveIndex <= previous) {
                    throw new IllegalArgumentException("Classpath providers are not in resolution order for " + name);
                }
                previous = archiveIndex;
            }
            if (copy.put(name, values) != null) {
                throw new IllegalArgumentException("Duplicate classpath entry: " + name);
            }
            count = Math.addExact(count, values.size());
        }
        this.providers = Collections.unmodifiableNavigableMap(copy);
        this.providerCount = count;
    }

    public String profileFingerprint() {
        return profileFingerprint;
    }

    public List<Archive> archives() {
        return archives;
    }

    public NavigableMap<String, List<Integer>> providers() {
        return providers;
    }

    public int entryCount() {
        return providers.size();
    }

    public long providerCount() {
        return providerCount;
    }

    public List<Integer> providerIndexes(String entryName) {
        List<Integer> result = providers.get(JarArchiveIndex.normalizeEntryName(entryName));
        return result == null ? List.of() : result;
    }

    public List<Archive> providerArchives(String entryName) {
        List<Archive> result = new ArrayList<>();
        for (int index : providerIndexes(entryName)) {
            result.add(archives.get(index));
        }
        return List.copyOf(result);
    }

    public Optional<Archive> winner(String entryName) {
        List<Integer> indexes = providerIndexes(entryName);
        return indexes.isEmpty() ? Optional.empty() : Optional.of(archives.get(indexes.get(indexes.size() - 1)));
    }

    public record Archive(
            String modId,
            String relativePath,
            Path physicalPath,
            String sourceSha256,
            long sourceBytes,
            long modifiedMillis,
            String archiveIndexRelativePath,
            boolean declared) {
        public Archive {
            if (modId == null || modId.isBlank()) {
                throw new IllegalArgumentException("Classpath archive mod ID is required");
            }
            relativePath = JarArchiveIndex.normalizeEntryName(relativePath);
            if (physicalPath == null) {
                throw new IllegalArgumentException("Classpath archive physical path is required");
            }
            physicalPath = physicalPath.toAbsolutePath().normalize();
            Hashes.decodeSha256(sourceSha256);
            sourceSha256 = sourceSha256.toLowerCase(java.util.Locale.ROOT);
            if (sourceBytes < 0 || modifiedMillis < 0) {
                throw new IllegalArgumentException("Classpath archive metadata may not be negative");
            }
            archiveIndexRelativePath = JarArchiveIndex.normalizeEntryName(archiveIndexRelativePath);
        }
    }
}
