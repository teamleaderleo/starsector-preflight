package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.ResourceIndex;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic compatibility coverage based on common Starsector library and campaign-mod layouts. */
class ModEcosystemCompatibilityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void commonLibraryAndCampaignLayoutsSurviveTheFullPreflightPipeline() throws Exception {
        Ecosystem fixture = Ecosystem.create(temporaryDirectory.resolve("Starsector"));

        ProfileCensus.Result census = ProfileCensus.scan(fixture.installRoot());
        Map<String, Object> values = census.values();
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) values.get("totals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicates = (List<Map<String, Object>>) values.get("duplicateSamples");

        assertEquals(Ecosystem.DEFAULT_ORDER, values.get("enabledModIds"));
        assertEquals(List.of(), values.get("missingModIds"));
        assertEquals(9L, totals.get("jarFiles"));
        assertTrue((long) totals.get("dataFiles") >= 7L);
        assertTrue((long) values.get("duplicateLogicalPaths") >= 1L);
        assertEquals("compat_patch", duplicate(duplicates, Ecosystem.SHARED_SHIP).get("probableWinner"));

        ResourceIndexBuilder.BuildResult built = ResourceIndexBuilder.build(fixture.installRoot());
        ResourceIndex index = built.index();
        assertEquals(6, index.roots().size());
        assertEquals("compat_patch", winnerRoot(index, Ecosystem.SHARED_SHIP));
        assertEquals(4, index.providers(Ecosystem.SHARED_SHIP).size());
        assertEquals("lunalib", winnerRoot(index, "graphics/libraries/luna-logo.png"));

        Path cache = temporaryDirectory.resolve("cache");
        TextureBatchBuilder.Result first = TextureBatchBuilder.build(
                index,
                cache,
                new TextureBatchBuilder.Options(4, 32L * 1024 * 1024));
        assertEquals(0, first.failedBlobs(), first.diagnostics().toString());
        assertTrue(first.manifest().entry(Ecosystem.SHARED_SHIP).isPresent());
        assertTrue(first.deduplicatedEntries() >= 1);
        assertEquals(first.uniqueContent(), first.builtBlobs());
        assertEquals(0, first.cacheHitBlobs());
        assertEquals(
                Hashes.sha256(fixture.patchSharedShip()),
                first.manifest().entry(Ecosystem.SHARED_SHIP).orElseThrow().sourceSha256());

        TextureBatchBuilder.Result repeated = TextureBatchBuilder.build(
                index,
                cache,
                new TextureBatchBuilder.Options(1, 16L * 1024 * 1024));
        assertEquals(0, repeated.failedBlobs(), repeated.diagnostics().toString());
        assertEquals(0, repeated.builtBlobs());
        assertEquals(repeated.uniqueContent(), repeated.cacheHitBlobs());
        assertEquals(first.manifest().entries(), repeated.manifest().entries());

        fixture.enable(List.of("lw_lazylib", "lunalib", "MagicLib", "compat_patch", "nexerelin"));
        ResourceIndex reordered = ResourceIndexBuilder.build(fixture.installRoot()).index();
        assertNotEquals(index.profileFingerprint(), reordered.profileFingerprint());
        assertEquals("nexerelin", winnerRoot(reordered, Ecosystem.SHARED_SHIP));

        TextureBatchBuilder.Result reorderedBuild = TextureBatchBuilder.build(
                reordered,
                cache,
                new TextureBatchBuilder.Options(3, 32L * 1024 * 1024));
        assertEquals(0, reorderedBuild.failedBlobs(), reorderedBuild.diagnostics().toString());
        assertEquals(
                Hashes.sha256(fixture.nexSharedShip()),
                reorderedBuild.manifest().entry(Ecosystem.SHARED_SHIP).orElseThrow().sourceSha256());
        assertEquals(1, reorderedBuild.builtBlobs());
        assertTrue(reorderedBuild.cacheHitBlobs() >= 1);
    }

    private static Map<String, Object> duplicate(List<Map<String, Object>> duplicates, String path) {
        return duplicates.stream()
                .filter(value -> path.equals(value.get("path")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing duplicate sample for " + path));
    }

    private static String winnerRoot(ResourceIndex index, String path) {
        ResourceIndex.Provider provider = index.winner(path).orElseThrow();
        return index.roots().get(provider.rootIndex()).id();
    }

    private record Ecosystem(
            Path installRoot,
            Path mods,
            Path patchSharedShip,
            Path nexSharedShip) {
        private static final String SHARED_SHIP = "graphics/ships/shared.png";
        private static final List<String> DEFAULT_ORDER =
                List.of("lw_lazylib", "lunalib", "MagicLib", "nexerelin", "compat_patch");

        static Ecosystem create(Path installRoot) throws Exception {
            Path core = installRoot.resolve("starsector-core");
            Path mods = installRoot.resolve("mods");
            Files.createDirectories(mods);
            Files.writeString(installRoot.resolve("starsector.sh"), "#!/bin/sh\n");
            writeImage(core.resolve(SHARED_SHIP), Color.BLUE);
            writeImage(core.resolve("graphics/core-only.png"), Color.GRAY);
            Files.writeString(core.resolve("data/config/settings.json"), "{\"fixture\":true}");

            Path lazy = mods.resolve("LazyLib");
            writeMetadata(lazy, """
                    {
                      "id": "lw_lazylib", #// Inline comment used by real-world metadata
                      "name": "LazyLib-like fixture",
                      "utility": "true",
                      "version": {"major":3,"minor":0,"patch":0},
                      "jars": [
                        "jars/LazyLib.jar",
                        "jars/LazyLib-Kotlin.jar",
                        "jars/internal/LazyLib-Console.jar",
                        "jars/internal/Kotlin-Runtime.jar"
                      ],
                      "modPlugin": "fixture.lazy.Plugin"
                    }
                    """);
            touch(lazy, "jars/LazyLib.jar", "jars/LazyLib-Kotlin.jar",
                    "jars/internal/LazyLib-Console.jar", "jars/internal/Kotlin-Runtime.jar");
            writeImage(lazy.resolve("graphics/libraries/shared-library-logo.png"), Color.CYAN);
            Files.writeString(lazy.resolve("data/config/lazy.csv"), "id,value\nlazy,1\n");

            Path luna = mods.resolve("LunaLib");
            writeMetadata(luna, """
                    {
                      "id": "lunalib",
                      "name": "LunaLib-like fixture",
                      "utility": "false",
                      "version": "2.0.5",
                      "jars": [
                        "jars/LunaLib.jar",
                        #"jars/LunaLib-Kotlin.jar",
                        "jars/libs/fuzzywuzzy-1.3.0.jar"
                      ],
                      "modPlugin": "fixture.luna.Plugin",
                      "dependencies": [{"id":"lw_lazylib","name":"LazyLib"}]
                    }
                    """);
            touch(luna, "jars/LunaLib.jar", "jars/libs/fuzzywuzzy-1.3.0.jar");
            Files.createDirectories(luna.resolve("graphics/libraries"));
            Files.copy(
                    lazy.resolve("graphics/libraries/shared-library-logo.png"),
                    luna.resolve("graphics/libraries/luna-logo.png"));
            Files.writeString(luna.resolve("data/config/luna-settings.json"), "{\"enabled\":true}");

            Path magic = mods.resolve("MagicLib");
            writeMetadata(magic, """
                    {
                      "id":"MagicLib",
                      "name":"MagicLib-like fixture",
                      "version":{"major":1,"minor":0,"patch":"x"},
                      "jars":["jars/MagicLib.jar","jars/MagicLib-Kotlin.jar"],
                      "modPlugin":"fixture.magic.Plugin",
                    }
                    """);
            touch(magic, "jars/MagicLib.jar", "jars/MagicLib-Kotlin.jar");
            Files.createDirectories(magic.resolve("src/fixture/magic"));
            Files.writeString(magic.resolve("src/fixture/magic/Plugin.java"), "class Plugin {}\n");
            Files.writeString(magic.resolve("src/fixture/magic/Helpers.kt"), "class Helpers\n");
            writeImage(magic.resolve(SHARED_SHIP), Color.GREEN);
            Files.createDirectories(magic.resolve("data/config/paintjobs"));
            Files.writeString(magic.resolve("data/config/paintjobs/example.paintjob"), "{\"hull\":\"fixture\"}");

            Path nex = mods.resolve("Nexerelin");
            writeMetadata(nex, """
                    {
                      "id":"nexerelin",
                      "name":"Nexerelin-like fixture",
                      "version":{"major":0,"minor":12,"patch":"1x"},
                      "totalConversion":false,
                      "jars":["jars/ExerelinCore.jar"],
                      "dependencies":[
                        {"id":"lw_lazylib","name":"LazyLib"},
                        {"id":"MagicLib","name":"MagicLib"},
                      ],
                      "modPlugin":"fixture.nex.Plugin",
                    }
                    """);
            touch(nex, "jars/ExerelinCore.jar");
            Path nexShared = nex.resolve(SHARED_SHIP);
            writeImage(nexShared, Color.RED);
            writeImage(nex.resolve("graphics/campaign/sector-map.png"), Color.ORANGE);
            Files.createDirectories(nex.resolve("data/campaign"));
            Files.writeString(nex.resolve("data/campaign/factions.csv"), "id,name\nfixture,Fixture\n");
            Files.writeString(nex.resolve("data/campaign/person_missions.csv"), "id,script\nmission,Fixture\n");
            Files.writeString(nex.resolve("data/config/exerelin_config.json"), "{\"corvusMode\":false}");
            Files.writeString(nex.resolve("graphics/campaign/unsupported.svg"), "<svg/>");

            Path patch = mods.resolve("Compatibility Patch");
            writeMetadata(patch, """
                    {
                      "id":"compat_patch",
                      "name":"Synthetic compatibility patch",
                      "version":"1.0.0",
                      "dependencies":[{"id":"nexerelin","name":"Nexerelin"}],
                    }
                    """);
            Path patchShared = patch.resolve(SHARED_SHIP);
            writeImage(patchShared, Color.MAGENTA);
            Files.createDirectories(patch.resolve("data/config"));
            Files.writeString(patch.resolve("data/config/patch.json"), "{\"patched\":true}");

            Ecosystem ecosystem = new Ecosystem(installRoot, mods, patchShared, nexShared);
            ecosystem.enable(DEFAULT_ORDER);
            return ecosystem;
        }

        void enable(List<String> ids) throws Exception {
            String values = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
            Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[" + values + "]}");
        }

        private static void writeMetadata(Path mod, String metadata) throws Exception {
            Files.createDirectories(mod);
            Files.writeString(mod.resolve("mod_info.json"), metadata);
        }

        private static void touch(Path root, String... paths) throws Exception {
            for (String path : paths) {
                Path file = root.resolve(path);
                Files.createDirectories(file.getParent());
                Files.writeString(file, "synthetic jar fixture: " + path);
            }
        }

        private static void writeImage(Path path, Color color) throws Exception {
            Files.createDirectories(path.getParent());
            BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
            assertTrue(ImageIO.write(image, "png", path.toFile()));
        }
    }
}
