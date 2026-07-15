package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.ResourceIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Audits enabled mod dependencies and JAR central directories without loading classes. */
final class ClasspathAudit {
    private static final int DUPLICATE_SAMPLE_LIMIT = 100;

    private ClasspathAudit() {
    }

    static Result scan(Path installRoot) throws IOException {
        GameLayout layout = GameLayout.locate(installRoot);
        List<String> enabledIds = JsonText.stringArray(
                Files.readString(layout.enabledModsFile(), StandardCharsets.UTF_8), "enabledMods");
        List<String> diagnostics = new ArrayList<>();
        Map<String, Path> installed = discoverMods(layout.modsDirectory(), diagnostics);
        Map<String, Integer> enabledOrder = order(enabledIds);
        List<ModRecord> mods = new ArrayList<>();
        List<JarRecord> jars = new ArrayList<>();

        for (int modIndex = 0; modIndex < enabledIds.size(); modIndex++) {
            final int modOrder = modIndex;
            String id = enabledIds.get(modOrder);
            Path directory = installed.get(id);
            if (directory == null) {
                diagnostics.add("Enabled mod directory not found for ID: " + id);
                mods.add(new ModRecord(
                        id, modOrder, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
                continue;
            }

            String metadata = readMetadata(directory, diagnostics);
            List<String> dependencies = metadata == null
                    ? List.of()
                    : JsonObjectArrays.stringFields(metadata, "dependencies", "id");
            List<String> declared = metadata == null
                    ? List.of()
                    : normalizeDeclaredJars(JsonText.stringArray(metadata, "jars"), id, diagnostics);
            List<String> actual = discoverJars(directory, diagnostics);
            Set<String> actualSet = new LinkedHashSet<>(actual);
            Set<String> declaredSet = new LinkedHashSet<>(declared);
            List<String> missingDeclared = declared.stream().filter(path -> !actualSet.contains(path)).toList();
            List<String> undeclared = actual.stream().filter(path -> !declaredSet.contains(path)).toList();
            List<String> missingDependencies = dependencies.stream()
                    .filter(dependency -> !enabledOrder.containsKey(dependency))
                    .toList();
            List<String> dependencyOrderProblems = dependencies.stream()
                    .filter(enabledOrder::containsKey)
                    .filter(dependency -> enabledOrder.get(dependency) > modOrder)
                    .toList();

            missingDeclared.forEach(path -> diagnostics.add("Declared JAR is missing for " + id + ": " + path));
            missingDependencies.forEach(dependency ->
                    diagnostics.add("Enabled mod " + id + " is missing dependency " + dependency));
            dependencyOrderProblems.forEach(dependency ->
                    diagnostics.add("Dependency " + dependency + " appears after dependent mod " + id));

            List<String> classpathOrder = new ArrayList<>();
            declared.stream().filter(actualSet::contains).forEach(classpathOrder::add);
            classpathOrder.addAll(undeclared);
            for (int jarOrder = 0; jarOrder < classpathOrder.size(); jarOrder++) {
                String relative = classpathOrder.get(jarOrder);
                jars.add(auditJar(id, modOrder, jarOrder, directory, relative, declaredSet.contains(relative)));
            }
            mods.add(new ModRecord(
                    id, modOrder, directory, dependencies, declared, missingDeclared, undeclared,
                    missingDependencies, dependencyOrderProblems));
        }

        Map<String, List<String>> classProviders = classProviders(jars, diagnostics);
        List<Map<String, Object>> duplicateSamples = classProviders.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .limit(DUPLICATE_SAMPLE_LIMIT)
                .map(entry -> duplicateSample(entry.getKey(), entry.getValue()))
                .toList();
        long duplicateClasses = classProviders.values().stream()
                .filter(providers -> providers.size() > 1)
                .count();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("installRoot", layout.installRoot());
        values.put("enabledModsFile", layout.enabledModsFile());
        values.put("enabledModIds", enabledIds);
        values.put("archiveFingerprint", archiveFingerprint(jars));
        values.put("classpathFingerprint", classpathFingerprint(enabledIds, jars));
        values.put("totals", totals(enabledIds, mods, jars, duplicateClasses));
        values.put("mods", mods.stream().map(ModRecord::toMap).toList());
        values.put("jars", jars.stream().map(JarRecord::toMap).toList());
        values.put("duplicateClassSamples", duplicateSamples);
        values.put("diagnostics", List.copyOf(new LinkedHashSet<>(diagnostics)));
        return new Result(values);
    }

    private static Map<String, Integer> order(List<String> enabledIds) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < enabledIds.size(); i++) {
            result.putIfAbsent(enabledIds.get(i), i);
        }
        return result;
    }

    private static Map<String, Path> discoverMods(Path modsDirectory, List<String> diagnostics) throws IOException {
        Map<String, Path> result = new LinkedHashMap<>();
        try (var stream = Files.list(modsDirectory)) {
            for (Path directory : stream.filter(Files::isDirectory).sorted().toList()) {
                String id = directory.getFileName().toString();
                String metadata = readMetadata(directory, diagnostics);
                if (metadata != null) {
                    String declared = JsonText.string(metadata, "id");
                    if (declared != null && !declared.isBlank()) {
                        id = declared;
                    }
                }
                Path normalized = directory.toAbsolutePath().normalize();
                Path prior = result.putIfAbsent(id, normalized);
                if (prior != null) {
                    diagnostics.add("Duplicate mod ID " + id + " in " + prior + " and " + normalized);
                }
            }
        }
        return result;
    }

    private static String readMetadata(Path directory, List<String> diagnostics) {
        Path info = directory.resolve("mod_info.json");
        if (!Files.isRegularFile(info)) {
            diagnostics.add("mod_info.json is missing from " + directory);
            return null;
        }
        try {
            return Files.readString(info, StandardCharsets.UTF_8);
        } catch (IOException error) {
            diagnostics.add("Could not read " + info + ": " + error.getMessage());
            return null;
        }
    }

    private static List<String> normalizeDeclaredJars(
            List<String> paths, String modId, List<String> diagnostics) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String path : paths) {
            try {
                String normalized = ResourceIndex.normalizeRelativePath(path);
                if (!result.add(normalized)) {
                    diagnostics.add("Duplicate declared JAR for " + modId + ": " + normalized);
                }
            } catch (IllegalArgumentException error) {
                diagnostics.add("Invalid declared JAR for " + modId + ": " + path + " (" + error.getMessage() + ")");
            }
        }
        return List.copyOf(result);
    }

    private static List<String> discoverJars(Path directory, List<String> diagnostics) {
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .map(path -> directory.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            diagnostics.add("Could not enumerate JARs under " + directory + ": " + error.getMessage());
            return List.of();
        }
    }

    private static JarRecord auditJar(
            String modId, int modOrder, int jarOrder, Path directory, String relative, boolean declared) {
        Path file = directory.resolve(relative).normalize();
        String hash;
        long fileBytes;
        try {
            hash = Hashes.sha256(file);
            fileBytes = Files.size(file);
        } catch (IOException error) {
            return JarRecord.failure(
                    modId, modOrder, jarOrder, relative, declared, "hash failed: " + error.getMessage());
        }

        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            List<String> classes = new ArrayList<>();
            long resources = 0;
            long uncompressed = 0;
            long compressed = 0;
            for (ZipEntry entry : entries) {
                uncompressed += Math.max(0, entry.getSize());
                compressed += Math.max(0, entry.getCompressedSize());
                if (entry.getName().endsWith(".class")) {
                    classes.add(entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'));
                } else {
                    resources++;
                }
            }
            return new JarRecord(
                    modId, modOrder, jarOrder, relative, declared, true, hash, fileBytes,
                    classes.size(), resources, uncompressed, compressed, List.copyOf(classes), null);
        } catch (IOException error) {
            return new JarRecord(
                    modId, modOrder, jarOrder, relative, declared, false, hash, fileBytes,
                    0, 0, 0, 0, List.of(), error.getMessage());
        }
    }

    private static Map<String, List<String>> classProviders(
            List<JarRecord> jars, List<String> diagnostics) {
        Map<String, List<String>> result = new TreeMap<>();
        for (JarRecord jar : jars) {
            if (!jar.valid()) {
                diagnostics.add("Could not read JAR " + jar.modId() + ":" + jar.relativePath()
                        + ": " + jar.error());
                continue;
            }
            for (String className : jar.classes()) {
                result.computeIfAbsent(className, ignored -> new ArrayList<>())
                        .add(jar.modId() + ":" + jar.relativePath());
            }
        }
        return result;
    }

    private static Map<String, Object> duplicateSample(String className, List<String> providers) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("className", className);
        value.put("providers", List.copyOf(providers));
        value.put("probableWinner", providers.get(providers.size() - 1));
        return value;
    }

    private static Map<String, Object> totals(
            List<String> enabledIds, List<ModRecord> mods, List<JarRecord> jars, long duplicateClasses) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("enabledMods", enabledIds.size());
        totals.put("resolvedMods", mods.stream().filter(mod -> mod.directory() != null).count());
        totals.put("jars", jars.size());
        totals.put("validJars", jars.stream().filter(JarRecord::valid).count());
        totals.put("malformedJars", jars.stream().filter(jar -> !jar.valid()).count());
        totals.put("declaredMissingJars", mods.stream().mapToLong(mod -> mod.missingDeclaredJars().size()).sum());
        totals.put("undeclaredJars", mods.stream().mapToLong(mod -> mod.undeclaredJars().size()).sum());
        totals.put("missingDependencies", mods.stream().mapToLong(mod -> mod.missingDependencies().size()).sum());
        totals.put("dependencyOrderProblems", mods.stream().mapToLong(mod -> mod.dependencyOrderProblems().size()).sum());
        totals.put("classEntries", jars.stream().mapToLong(JarRecord::classEntries).sum());
        totals.put("resourceEntries", jars.stream().mapToLong(JarRecord::resourceEntries).sum());
        totals.put("uncompressedBytes", jars.stream().mapToLong(JarRecord::uncompressedBytes).sum());
        totals.put("compressedBytes", jars.stream().mapToLong(JarRecord::compressedBytes).sum());
        totals.put("duplicateClasses", duplicateClasses);
        return totals;
    }

    private static String archiveFingerprint(List<JarRecord> jars) {
        MessageDigest digest = sha256();
        update(digest, "preflight-archive-set-v1");
        jars.stream()
                .sorted(Comparator.comparing(JarRecord::modId).thenComparing(JarRecord::relativePath))
                .forEach(jar -> updateJar(digest, jar));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String classpathFingerprint(List<String> enabledIds, List<JarRecord> jars) {
        MessageDigest digest = sha256();
        update(digest, "preflight-classpath-v1");
        enabledIds.forEach(id -> update(digest, id));
        jars.stream()
                .sorted(Comparator.comparingInt(JarRecord::modOrder).thenComparingInt(JarRecord::jarOrder))
                .forEach(jar -> updateJar(digest, jar));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateJar(MessageDigest digest, JarRecord jar) {
        update(digest, jar.modId());
        update(digest, jar.relativePath());
        update(digest, jar.sourceSha256() == null ? "missing" : jar.sourceSha256());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    record Result(Map<String, Object> values) {
        Result {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        String toJson() {
            return Json.object(values);
        }
    }

    private record ModRecord(
            String id,
            int order,
            Path directory,
            List<String> dependencies,
            List<String> declaredJars,
            List<String> missingDeclaredJars,
            List<String> undeclaredJars,
            List<String> missingDependencies,
            List<String> dependencyOrderProblems) {
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("order", order);
            value.put("directory", directory);
            value.put("dependencies", dependencies);
            value.put("declaredJars", declaredJars);
            value.put("missingDeclaredJars", missingDeclaredJars);
            value.put("undeclaredJars", undeclaredJars);
            value.put("missingDependencies", missingDependencies);
            value.put("dependencyOrderProblems", dependencyOrderProblems);
            return value;
        }
    }

    private record JarRecord(
            String modId,
            int modOrder,
            int jarOrder,
            String relativePath,
            boolean declared,
            boolean valid,
            String sourceSha256,
            long fileBytes,
            long classEntries,
            long resourceEntries,
            long uncompressedBytes,
            long compressedBytes,
            List<String> classes,
            String error) {
        static JarRecord failure(
                String modId, int modOrder, int jarOrder, String relativePath, boolean declared, String error) {
            return new JarRecord(
                    modId, modOrder, jarOrder, relativePath, declared, false, null,
                    0, 0, 0, 0, 0, List.of(), error);
        }

        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("modId", modId);
            value.put("modOrder", modOrder);
            value.put("jarOrder", jarOrder);
            value.put("relativePath", relativePath);
            value.put("declared", declared);
            value.put("valid", valid);
            value.put("sourceSha256", sourceSha256);
            value.put("fileBytes", fileBytes);
            value.put("classEntries", classEntries);
            value.put("resourceEntries", resourceEntries);
            value.put("uncompressedBytes", uncompressedBytes);
            value.put("compressedBytes", compressedBytes);
            value.put("error", error);
            return value;
        }
    }
}
