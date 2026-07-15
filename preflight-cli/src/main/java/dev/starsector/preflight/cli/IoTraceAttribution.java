package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates JFR file I/O by normalized path, extension, and startup-relevant category. */
final class IoTraceAttribution {
    private static final int TOP_LIMIT = 25;
    private static final String UNKNOWN_PATH = "<unknown>";

    private final Map<String, MutableMetric> readsByPath = new TreeMap<>();
    private final Map<String, MutableMetric> writesByPath = new TreeMap<>();
    private final Map<String, MutableMetric> readsByExtension = new TreeMap<>();
    private final Map<String, MutableMetric> writesByExtension = new TreeMap<>();
    private final Map<String, MutableMetric> readsByCategory = new TreeMap<>();
    private final Map<String, MutableMetric> writesByCategory = new TreeMap<>();

    void recordRead(String path, long bytes, long nanos) {
        record(path, bytes, nanos, readsByPath, readsByExtension, readsByCategory);
    }

    void recordWrite(String path, long bytes, long nanos) {
        record(path, bytes, nanos, writesByPath, writesByExtension, writesByCategory);
    }

    Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("topReadPaths", top(readsByPath, "path"));
        values.put("topWritePaths", top(writesByPath, "path"));
        values.put("readExtensions", top(readsByExtension, "extension"));
        values.put("writeExtensions", top(writesByExtension, "extension"));
        values.put("readCategories", top(readsByCategory, "category"));
        values.put("writeCategories", top(writesByCategory, "category"));
        return values;
    }

    private static void record(
            String rawPath,
            long rawBytes,
            long rawNanos,
            Map<String, MutableMetric> byPath,
            Map<String, MutableMetric> byExtension,
            Map<String, MutableMetric> byCategory) {
        String path = normalizePath(rawPath);
        long bytes = Math.max(0, rawBytes);
        long nanos = Math.max(0, rawNanos);
        byPath.computeIfAbsent(path, ignored -> new MutableMetric()).add(bytes, nanos);
        String extension = extension(path);
        byExtension.computeIfAbsent(extension, ignored -> new MutableMetric()).add(bytes, nanos);
        String category = category(extension);
        byCategory.computeIfAbsent(category, ignored -> new MutableMetric()).add(bytes, nanos);
    }

    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN_PATH;
        }
        String value = raw.trim().replace('\\', '/');
        StringBuilder normalized = new StringBuilder(value.length());
        boolean priorSlash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/') {
                if (!priorSlash) {
                    normalized.append(c);
                }
                priorSlash = true;
            } else {
                normalized.append(c);
                priorSlash = false;
            }
        }
        String result = normalized.toString();
        return result.isBlank() ? UNKNOWN_PATH : result;
    }

    static String extension(String path) {
        if (UNKNOWN_PATH.equals(path)) {
            return "<unknown>";
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) {
            return "<none>";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String category(String extension) {
        return switch (extension) {
            case "jar", "zip" -> "archive";
            case "spfi", "spft", "spfm", "spfj", "spfc" -> "preflight-cache";
            case "png", "jpg", "jpeg", "webp", "bmp", "gif", "tga" -> "image";
            case "ogg", "wav", "mp3", "flac" -> "sound";
            case "csv", "json", "xml", "faction", "variant", "ship", "skin", "weapon", "wpn",
                    "proj", "system", "rules" -> "data";
            case "class", "java", "kt" -> "code";
            case "<unknown>" -> "unknown";
            default -> "other";
        };
    }

    private static List<Map<String, Object>> top(Map<String, MutableMetric> source, String keyName) {
        List<Map.Entry<String, MutableMetric>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Comparator
                .<Map.Entry<String, MutableMetric>>comparingLong(entry -> entry.getValue().bytes)
                .reversed()
                .thenComparing(Comparator
                        .<Map.Entry<String, MutableMetric>>comparingLong(entry -> entry.getValue().nanos)
                        .reversed())
                .thenComparing(Comparator
                        .<Map.Entry<String, MutableMetric>>comparingLong(entry -> entry.getValue().operations)
                        .reversed())
                .thenComparing(Map.Entry::getKey));

        List<Map<String, Object>> result = new ArrayList<>(Math.min(TOP_LIMIT, entries.size()));
        for (int i = 0; i < entries.size() && i < TOP_LIMIT; i++) {
            Map.Entry<String, MutableMetric> entry = entries.get(i);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put(keyName, entry.getKey());
            value.put("operations", entry.getValue().operations);
            value.put("bytes", entry.getValue().bytes);
            value.put("durationMs", entry.getValue().nanos / 1_000_000.0);
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static final class MutableMetric {
        private long operations;
        private long bytes;
        private long nanos;

        void add(long additionalBytes, long additionalNanos) {
            operations = Math.addExact(operations, 1);
            bytes = Math.addExact(bytes, additionalBytes);
            nanos = Math.addExact(nanos, additionalNanos);
        }
    }
}
