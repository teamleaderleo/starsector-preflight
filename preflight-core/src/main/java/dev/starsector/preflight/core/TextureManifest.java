package dev.starsector.preflight.core;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable mapping from logical resource paths to content-addressed prepared texture blobs. */
public final class TextureManifest {
    public static final int FORMAT_VERSION = 1;

    private final String profileFingerprint;
    private final NavigableMap<String, Entry> entries;

    public TextureManifest(String profileFingerprint, Map<String, Entry> entries) {
        if (profileFingerprint == null || profileFingerprint.isBlank()) {
            throw new IllegalArgumentException("profileFingerprint is required");
        }
        this.profileFingerprint = profileFingerprint;
        TreeMap<String, Entry> copy = new TreeMap<>();
        for (Map.Entry<String, Entry> source : entries.entrySet()) {
            String path = ResourceIndex.normalizeLogicalPath(source.getKey());
            Entry value = Objects.requireNonNull(source.getValue(), "manifest entry");
            Entry prior = copy.put(path, value);
            if (prior != null) {
                throw new IllegalArgumentException("Duplicate normalized texture path: " + path);
            }
        }
        this.entries = Collections.unmodifiableNavigableMap(copy);
    }

    public String profileFingerprint() {
        return profileFingerprint;
    }

    public NavigableMap<String, Entry> entries() {
        return entries;
    }

    public int entryCount() {
        return entries.size();
    }

    public Optional<Entry> entry(String logicalPath) {
        return Optional.ofNullable(entries.get(ResourceIndex.normalizeLogicalPath(logicalPath)));
    }

    public record Entry(
            String sourceSha256,
            PreparedTexture.Transformation transformation,
            String blobRelativePath,
            int width,
            int height,
            int channels,
            int pixelBytes) {
        public Entry {
            Hashes.decodeSha256(sourceSha256);
            sourceSha256 = sourceSha256.toLowerCase(java.util.Locale.ROOT);
            transformation = Objects.requireNonNull(transformation, "transformation");
            blobRelativePath = ResourceIndex.normalizeRelativePath(blobRelativePath);
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture manifest dimensions must be positive");
            }
            if (channels != 3 && channels != 4) {
                throw new IllegalArgumentException("Texture manifest channels must be 3 or 4");
            }
            long expected = Math.multiplyExact(Math.multiplyExact((long) width, height), channels);
            if (expected != pixelBytes) {
                throw new IllegalArgumentException(
                        "Texture manifest pixel length is " + pixelBytes + "; expected " + expected);
            }
        }
    }
}
