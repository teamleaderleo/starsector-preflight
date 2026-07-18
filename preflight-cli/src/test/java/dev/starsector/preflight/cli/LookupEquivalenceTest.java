package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ClasspathProfileIndex;
import dev.starsector.preflight.core.ResourceIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LookupEquivalenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void looseResourceIndexMatchesOrderedFilesystemProbing() throws Exception {
        ResourceIndex index = resourceFixture(40, 800);

        LookupEquivalence.DomainResult result = LookupEquivalence.resources(index, 8_000, 123456L);

        assertTrue(result.equivalent(), result.mismatchSamples().toString());
        assertEquals(0, result.mismatches());
        assertEquals(result.queries() * index.roots().size(), result.baselineProbes());
        assertEquals(result.queries(), result.indexedLookups());
        assertTrue(result.baselineProbes() >= result.indexedLookups() * 40);
        assertTrue(result.requestedHits() > result.requestedMisses());
    }

    @Test
    void classpathIndexMatchesOrderedZipEntryProbing() throws Exception {
        ClasspathProfileIndex index = classpathFixture(24, 600);

        LookupEquivalence.DomainResult result = LookupEquivalence.classpath(index, 6_000, 987654L);

        assertTrue(result.equivalent(), result.mismatchSamples().toString());
        assertEquals(0, result.mismatches());
        assertEquals(result.queries() * index.archives().size(), result.baselineProbes());
        assertEquals(result.queries(), result.indexedLookups());
        assertTrue(result.baselineProbes() >= result.indexedLookups() * 24);
        assertTrue(result.indexedProviderAccesses() > 0);
    }

    @Test
    void looseResourceComparisonTreatsFilesystemAliasesAsTheSameProvider() throws Exception {
        Path root = temporaryDirectory.resolve("case-alias");
        Path actual = root.resolve("graphics/Asset.PNG");
        Path queryAlias = root.resolve("graphics/asset.png");
        Files.createDirectories(actual.getParent());
        Files.write(actual, new byte[] {1, 2, 3});
        if (!Files.exists(queryAlias)) {
            Files.createLink(queryAlias, actual);
        }
        ResourceIndex index = new ResourceIndex(
                "cc".repeat(32),
                List.of(new ResourceIndex.Root("root", root, true)),
                Map.of("graphics/asset.png", List.of(new ResourceIndex.Provider(
                        0,
                        "graphics/Asset.PNG",
                        Files.size(actual),
                        Files.getLastModifiedTime(actual).toMillis()))));

        LookupEquivalence.DomainResult result = LookupEquivalence.resources(index, 100, 7L);

        assertTrue(result.equivalent(), result.mismatchSamples().toString());
    }

    private ResourceIndex resourceFixture(int rootCount, int resourceCount) throws Exception {
        List<ResourceIndex.Root> roots = new ArrayList<>();
        Map<String, List<ResourceIndex.Provider>> entries = new LinkedHashMap<>();
        for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
            Path root = temporaryDirectory.resolve("resources/root-" + rootIndex);
            Files.createDirectories(root);
            roots.add(new ResourceIndex.Root(rootIndex == 0 ? "core" : "mod-" + rootIndex, root, rootIndex == 0));
        }

        for (int resource = 0; resource < resourceCount; resource++) {
            String logical = "graphics/generated/asset-" + resource + ".png";
            List<ResourceIndex.Provider> providers = new ArrayList<>();
            for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
                boolean present = rootIndex == resource % rootCount
                        || (resource % 7 == 0 && rootIndex == rootCount - 1)
                        || (resource % 19 == 0 && rootIndex == rootCount / 2);
                if (!present) {
                    continue;
                }
                Path file = roots.get(rootIndex).path().resolve(logical);
                Files.createDirectories(file.getParent());
                Files.write(file, new byte[] {(byte) resource, (byte) rootIndex});
                providers.add(new ResourceIndex.Provider(
                        rootIndex,
                        logical,
                        Files.size(file),
                        Files.getLastModifiedTime(file).toMillis()));
            }
            entries.put(logical, providers);
        }
        return new ResourceIndex("aa".repeat(32), roots, entries);
    }

    private ClasspathProfileIndex classpathFixture(int archiveCount, int entryCount) throws Exception {
        List<ClasspathProfileIndex.Archive> archives = new ArrayList<>();
        Map<String, List<Integer>> providers = new LinkedHashMap<>();
        for (int archiveIndex = 0; archiveIndex < archiveCount; archiveIndex++) {
            Path jar = temporaryDirectory.resolve("classpath/mod-" + archiveIndex + "/jars/mod.jar");
            Map<String, byte[]> contents = new LinkedHashMap<>();
            for (int entry = 0; entry < entryCount; entry++) {
                boolean present = archiveIndex == entry % archiveCount
                        || (entry % 11 == 0 && archiveIndex == archiveCount - 1)
                        || (entry % 31 == 0 && archiveIndex == archiveCount / 2);
                if (!present) {
                    continue;
                }
                String name = "generated/Type" + entry + ".class";
                contents.put(name, new byte[] {(byte) archiveIndex, (byte) entry});
                providers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(archiveIndex);
            }
            jar(jar, contents);
            archives.add(new ClasspathProfileIndex.Archive(
                    "mod-" + archiveIndex,
                    "jars/mod.jar",
                    jar,
                    String.format("%064x", archiveIndex + 1L),
                    Files.size(jar),
                    Files.getLastModifiedTime(jar).toMillis(),
                    "classpath/archives/fixture/" + archiveIndex + ".spfj",
                    true));
        }
        return new ClasspathProfileIndex("bb".repeat(32), archives, providers);
    }

    private static void jar(Path path, Map<String, byte[]> entries) throws Exception {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0);
                output.putNextEntry(jarEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
}
