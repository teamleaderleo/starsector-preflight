package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.ImageHeaderReader;
import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

final class ProfileCensus {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "bmp", "gif", "tga");
    private static final Set<String> SOUND_EXTENSIONS = Set.of("ogg", "wav", "mp3", "flac");
    private static final Set<String> DATA_EXTENSIONS = Set.of(
            "csv", "json", "faction", "variant", "ship", "skin", "weapon", "wpn", "proj", "system", "rules", "xml");
    private static final int LARGEST_LIMIT = 25;
    private static final int DUPLICATE_SAMPLE_LIMIT = 100;

    private ProfileCensus() {
    }

    static Result scan(Path installRoot) throws IOException {
        long scanStarted = System.nanoTime();
        GameLayout layout = GameLayout.locate(installRoot);
        List<String> diagnostics = new ArrayList<>(layout.diagnostics());
        String enabledJson = Files.readString(layout.enabledModsFile(), StandardCharsets.UTF_8);
        List<String> enabledIds = JsonText.stringArray(enabledJson, "enabledMods");
        if (enabledIds.isEmpty()) {
            diagnostics.add("enabled_mods.json contains no enabled mod IDs");
        }

        Map<String, Path> directoriesById = discoverModDirectories(layout.modsDirectory(), diagnostics);
        List<ResolvedMod> mods = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < enabledIds.size(); i++) {
            String id = enabledIds.get(i);
            Path directory = directoriesById.get(id);
            if (directory == null) {
                missing.add(id);
                diagnostics.add("Enabled mod directory not found for ID: " + id);
            } else {
                mods.add(new ResolvedMod(id, directory, i));
            }
        }

        ScanAccumulator accumulator = new ScanAccumulator(layout, enabledIds, diagnostics);
        for (ResolvedMod mod : mods) {
            accumulator.scanMod(mod);
        }
        return accumulator.finish(mods, missing, System.nanoTime() - scanStarted);
    }

    private static Map<String, Path> discoverModDirectories(Path modsDirectory, List<String> diagnostics) throws IOException {
        Map<String, Path> byId = new LinkedHashMap<>();
        try (var entries = Files.list(modsDirectory)) {
            for (Path directory : entries.filter(Files::isDirectory).sorted().toList()) {
                Path info = directory.resolve("mod_info.json");
                String id = null;
                if (Files.isRegularFile(info)) {
                    try {
                        id = JsonText.string(Files.readString(info, StandardCharsets.UTF_8), "id");
                    } catch (RuntimeException | IOException error) {
                        diagnostics.add("Could not read mod ID from " + info + ": " + error.getMessage());
                    }
                }
                if (id == null || id.isBlank()) {
                    id = directory.getFileName().toString();
                }
                Path prior = byId.putIfAbsent(id, directory.toAbsolutePath().normalize());
                if (prior != null) {
                    diagnostics.add("Duplicate mod ID " + id + " in " + prior + " and " + directory);
                }
            }
        }
        return byId;
    }

    record Result(Map<String, Object> values) {
        String toJson() {
            return Json.object(values);
        }
    }

    private record ResolvedMod(String id, Path directory, int order) {
    }

    private record Asset(String modId, String logicalPath, long bytes) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("modId", modId);
            values.put("path", logicalPath);
            values.put("bytes", bytes);
            return values;
        }
    }

    private record Provider(String modId, int order) {
    }

    private static final class ModStats {
        final String id;
        final Path directory;
        long files;
        long bytes;
        long imageFiles;
        long imageBytes;
        long decodedImageBytes;
        long measuredImageFiles;
        long unmeasuredImageFiles;
        long soundFiles;
        long soundBytes;
        long looseJavaFiles;
        long looseJavaBytes;
        long jarFiles;
        long jarBytes;
        long dataFiles;
        long dataBytes;

        ModStats(String id, Path directory) {
            this.id = id;
            this.directory = directory;
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            values.put("directory", directory);
            values.put("files", files);
            values.put("bytes", bytes);
            values.put("imageFiles", imageFiles);
            values.put("imageBytes", imageBytes);
            values.put("decodedImageBytes", decodedImageBytes);
            values.put("measuredImageFiles", measuredImageFiles);
            values.put("unmeasuredImageFiles", unmeasuredImageFiles);
            values.put("soundFiles", soundFiles);
            values.put("soundBytes", soundBytes);
            values.put("looseJavaFiles", looseJavaFiles);
            values.put("looseJavaBytes", looseJavaBytes);
            values.put("jarFiles", jarFiles);
            values.put("jarBytes", jarBytes);
            values.put("dataFiles", dataFiles);
            values.put("dataBytes", dataBytes);
            return values;
        }
    }

    private static final class ScanAccumulator {
        private final GameLayout layout;
        private final List<String> enabledIds;
        private final List<String> diagnostics;
        private final Map<String, long[]> extensionTotals = new HashMap<>();
        private final Map<String, List<Provider>> providersByLogicalPath = new HashMap<>();
        private final List<ModStats> modStats = new ArrayList<>();
        private final PriorityQueue<Asset> largest = new PriorityQueue<>(
                Comparator.comparingLong(Asset::bytes)
                        .thenComparing(Asset::modId)
                        .thenComparing(Asset::logicalPath));
        private final MessageDigest profileDigest = sha256();
        private long totalFiles;
        private long totalBytes;
        private long imageFiles;
        private long imageBytes;
        private long decodedImageBytes;
        private long measuredImageFiles;
        private long unmeasuredImageFiles;
        private long soundFiles;
        private long soundBytes;
        private long looseJavaFiles;
        private long looseJavaBytes;
        private long jarFiles;
        private long jarBytes;
        private long dataFiles;
        private long dataBytes;

        ScanAccumulator(GameLayout layout, List<String> enabledIds, List<String> diagnostics) {
            this.layout = layout;
            this.enabledIds = List.copyOf(enabledIds);
            this.diagnostics = diagnostics;
            updateDigest("preflight-profile-v1");
            for (String id : enabledIds) {
                updateDigest(id);
            }
        }

        void scanMod(ResolvedMod mod) throws IOException {
            ModStats stats = new ModStats(mod.id(), mod.directory());
            modStats.add(stats);
            updateDigest("mod");
            updateDigest(mod.id());
            updateDigest(Integer.toString(mod.order()));
            scanDirectory(mod, stats, mod.directory(), new LinkedHashSet<>());
        }

        private void scanDirectory(
                ResolvedMod mod,
                ModStats stats,
                Path directory,
                Set<Path> visitedDirectories) throws IOException {
            Path realDirectory;
            try {
                realDirectory = directory.toRealPath();
            } catch (IOException error) {
                diagnostics.add("Could not resolve " + directory + ": " + error.getMessage());
                return;
            }
            if (!visitedDirectories.add(realDirectory)) {
                diagnostics.add("Skipped directory cycle or duplicate link at " + directory);
                return;
            }

            List<Path> entries;
            try (var stream = Files.list(directory)) {
                entries = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            } catch (IOException error) {
                diagnostics.add("Could not inspect " + directory + ": " + error.getMessage());
                return;
            }

            for (Path entry : entries) {
                try {
                    if (Files.isDirectory(entry)) {
                        scanDirectory(mod, stats, entry, visitedDirectories);
                    } else if (Files.isRegularFile(entry)) {
                        BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class);
                        recordFile(mod, stats, entry, attributes);
                    }
                } catch (IOException error) {
                    diagnostics.add("Could not inspect " + entry + ": " + error.getMessage());
                }
            }
        }

        private void recordFile(ResolvedMod mod, ModStats stats, Path file, BasicFileAttributes attributes) {
            String logicalPath = normalize(mod.directory().relativize(file));
            long bytes = attributes.size();
            long modified = attributes.lastModifiedTime().toMillis();
            totalFiles++;
            totalBytes += bytes;
            stats.files++;
            stats.bytes += bytes;
            updateDigest(logicalPath);
            updateDigest(Long.toString(bytes));
            updateDigest(Long.toString(modified));

            if (!logicalPath.equalsIgnoreCase("mod_info.json")) {
                providersByLogicalPath
                        .computeIfAbsent(logicalPath.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(new Provider(mod.id(), mod.order()));
            }
            addLargest(new Asset(mod.id(), logicalPath, bytes));
            String extension = extension(logicalPath);
            classify(stats, extension, bytes);
            if (IMAGE_EXTENSIONS.contains(extension)) {
                recordDecodedImage(stats, file);
            }
        }

        /**
         * Adds an image's exact decoded (VRAM) footprint to the per-mod and profile totals. Reads
         * dimensions from the header only. Unreadable or unsupported formats are counted as
         * unmeasured rather than guessed, so the decoded total stays an exact floor. This does not
         * feed the profile fingerprint — it is pure read-only accounting.
         */
        private void recordDecodedImage(ModStats stats, Path file) {
            Optional<ImageHeaderReader.ImageDimensions> dimensions;
            try {
                dimensions = ImageHeaderReader.read(file);
            } catch (IOException error) {
                dimensions = Optional.empty();
            }
            if (dimensions.isPresent()) {
                long decoded = dimensions.get().decodedBytes();
                stats.decodedImageBytes += decoded;
                stats.measuredImageFiles++;
                decodedImageBytes += decoded;
                measuredImageFiles++;
            } else {
                stats.unmeasuredImageFiles++;
                unmeasuredImageFiles++;
            }
        }

        Result finish(List<ResolvedMod> mods, List<String> missing, long scanNanos) {
            List<Map.Entry<String, List<Provider>>> duplicateEntries = providersByLogicalPath.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            long duplicateLogicalPaths = duplicateEntries.size();
            long duplicateProviderEntries = duplicateEntries.stream()
                    .mapToLong(entry -> entry.getValue().size() - 1L)
                    .sum();
            List<Map<String, Object>> duplicateSamples = new ArrayList<>();
            for (Map.Entry<String, List<Provider>> entry : duplicateEntries.stream()
                    .limit(DUPLICATE_SAMPLE_LIMIT)
                    .toList()) {
                List<Provider> sorted = entry.getValue().stream()
                        .sorted(Comparator.comparingInt(Provider::order))
                        .toList();
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("path", entry.getKey());
                sample.put("providers", sorted.stream().map(Provider::modId).toList());
                sample.put("probableWinner", sorted.get(sorted.size() - 1).modId());
                duplicateSamples.add(sample);
            }

            List<Map<String, Object>> modsByOrder = modStats.stream().map(ModStats::toMap).toList();
            List<Map<String, Object>> largestMods = modStats.stream()
                    .sorted(Comparator.comparingLong((ModStats stats) -> stats.bytes).reversed()
                            .thenComparing(stats -> stats.id))
                    .map(ModStats::toMap)
                    .toList();
            // Which mods actually cost the most VRAM once decoded — distinct from on-disk size,
            // since compression ratios vary wildly between mods.
            List<Map<String, Object>> largestDecodedMods = modStats.stream()
                    .filter(stats -> stats.decodedImageBytes > 0)
                    .sorted(Comparator.comparingLong((ModStats stats) -> stats.decodedImageBytes).reversed()
                            .thenComparing(stats -> stats.id))
                    .map(ModStats::toMap)
                    .toList();
            List<Map<String, Object>> largestAssets = largest.stream()
                    .sorted(Comparator.comparingLong(Asset::bytes).reversed()
                            .thenComparing(Asset::modId)
                            .thenComparing(Asset::logicalPath))
                    .map(Asset::toMap)
                    .toList();

            Map<String, Object> totals = new LinkedHashMap<>();
            totals.put("files", totalFiles);
            totals.put("bytes", totalBytes);
            totals.put("imageFiles", imageFiles);
            totals.put("imageBytes", imageBytes);
            totals.put("decodedImageBytes", decodedImageBytes);
            totals.put("measuredImageFiles", measuredImageFiles);
            totals.put("unmeasuredImageFiles", unmeasuredImageFiles);
            totals.put("soundFiles", soundFiles);
            totals.put("soundBytes", soundBytes);
            totals.put("looseJavaFiles", looseJavaFiles);
            totals.put("looseJavaBytes", looseJavaBytes);
            totals.put("jarFiles", jarFiles);
            totals.put("jarBytes", jarBytes);
            totals.put("dataFiles", dataFiles);
            totals.put("dataBytes", dataBytes);

            Map<String, Object> extensionReport = new LinkedHashMap<>();
            extensionTotals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("files", entry.getValue()[0]);
                values.put("bytes", entry.getValue()[1]);
                extensionReport.put(entry.getKey(), values);
            });

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("generatedAt", Instant.now());
            values.put("scanDurationMs", scanNanos / 1_000_000.0);
            values.put("installRoot", layout.installRoot());
            values.put("modsDirectory", layout.modsDirectory());
            values.put("enabledModsFile", layout.enabledModsFile());
            values.put("fingerprintKind", "ordered-path-size-mtime-v1");
            values.put("profileFingerprint", HexFormat.of().formatHex(profileDigest.digest()));
            values.put("enabledModIds", enabledIds);
            values.put("resolvedModCount", mods.size());
            values.put("missingModIds", missing);
            values.put("totals", totals);
            values.put("extensions", extensionReport);
            values.put("mods", modsByOrder);
            values.put("largestMods", largestMods);
            values.put("largestDecodedMods", largestDecodedMods);
            Map<String, Object> decodedWorkingSet = new LinkedHashMap<>();
            decodedWorkingSet.put("decodedImageBytes", decodedImageBytes);
            decodedWorkingSet.put("measuredImageFiles", measuredImageFiles);
            decodedWorkingSet.put("unmeasuredImageFiles", unmeasuredImageFiles);
            decodedWorkingSet.put("basis", "exact width*height*channels from image headers; unmeasured formats excluded");
            values.put("decodedWorkingSet", decodedWorkingSet);
            values.put("largestAssets", largestAssets);
            values.put("overrideSemantics", "probable-enabled-order-only");
            values.put("duplicateLogicalPaths", duplicateLogicalPaths);
            values.put("duplicateProviderEntries", duplicateProviderEntries);
            values.put("duplicateSamples", duplicateSamples);
            values.put("diagnostics", List.copyOf(new LinkedHashSet<>(diagnostics)));
            return new Result(values);
        }

        private void classify(ModStats stats, String extension, long bytes) {
            long[] totals = extensionTotals.computeIfAbsent(extension, ignored -> new long[2]);
            totals[0]++;
            totals[1] += bytes;
            if (IMAGE_EXTENSIONS.contains(extension)) {
                imageFiles++;
                imageBytes += bytes;
                stats.imageFiles++;
                stats.imageBytes += bytes;
            }
            if (SOUND_EXTENSIONS.contains(extension)) {
                soundFiles++;
                soundBytes += bytes;
                stats.soundFiles++;
                stats.soundBytes += bytes;
            }
            if (extension.equals("java")) {
                looseJavaFiles++;
                looseJavaBytes += bytes;
                stats.looseJavaFiles++;
                stats.looseJavaBytes += bytes;
            }
            if (extension.equals("jar")) {
                jarFiles++;
                jarBytes += bytes;
                stats.jarFiles++;
                stats.jarBytes += bytes;
            }
            if (DATA_EXTENSIONS.contains(extension)) {
                dataFiles++;
                dataBytes += bytes;
                stats.dataFiles++;
                stats.dataBytes += bytes;
            }
        }

        private void addLargest(Asset asset) {
            largest.add(asset);
            if (largest.size() > LARGEST_LIMIT) {
                largest.remove();
            }
        }

        private void updateDigest(String value) {
            profileDigest.update(value.getBytes(StandardCharsets.UTF_8));
            profileDigest.update((byte) 0);
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static String extension(String logicalPath) {
        int slash = logicalPath.lastIndexOf('/');
        int dot = logicalPath.lastIndexOf('.');
        if (dot <= slash || dot == logicalPath.length() - 1) {
            return "(none)";
        }
        return logicalPath.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
