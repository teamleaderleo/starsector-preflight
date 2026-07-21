package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.ResourceIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceIndexBuilderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsCoreAndEnabledModProviderOrder() throws Exception {
        Path core = temporaryDirectory.resolve("starsector-core");
        Path mods = temporaryDirectory.resolve("mods");
        Path alpha = mods.resolve("Alpha");
        Path beta = mods.resolve("Beta");
        Files.createDirectories(core.resolve("graphics"));
        Files.createDirectories(alpha.resolve("graphics"));
        Files.createDirectories(beta.resolve("graphics"));
        Files.writeString(core.resolve("graphics/shared.png"), "core");
        Files.writeString(alpha.resolve("graphics/shared.png"), "alpha");
        Files.writeString(beta.resolve("graphics/Shared.PNG"), "beta");
        Files.writeString(alpha.resolve("mod_info.json"), "{\"id\":\"alpha\"}");
        Files.writeString(beta.resolve("mod_info.json"), "{\"id\":\"beta\"}");
        Path enabled = mods.resolve("enabled_mods.json");
        Files.writeString(enabled, "{\"enabledMods\":[\"alpha\",\"beta\"]}");

        ResourceIndexBuilder.BuildResult first = ResourceIndexBuilder.build(temporaryDirectory);
        ResourceIndex index = first.index();

        assertEquals(List.of("core", "alpha", "beta"), index.roots().stream().map(ResourceIndex.Root::id).toList());
        assertEquals(3, index.providers("graphics/shared.png").size());
        assertEquals("beta", index.roots().get(index.winner("graphics/shared.png").orElseThrow().rootIndex()).id());
        assertTrue(index.winningFile("graphics/shared.png").orElseThrow().endsWith(Path.of("graphics", "Shared.PNG")));

        Files.writeString(enabled, "{\"enabledMods\":[\"beta\",\"alpha\"]}");
        ResourceIndexBuilder.BuildResult reordered = ResourceIndexBuilder.build(temporaryDirectory);

        assertNotEquals(index.profileFingerprint(), reordered.index().profileFingerprint());
        assertEquals(
                "alpha",
                reordered.index().roots().get(
                        reordered.index().winner("graphics/shared.png").orElseThrow().rootIndex()).id());
    }

    @Test
    void reportsMissingModsAndBuildsAvailableRoots() throws Exception {
        Path mods = temporaryDirectory.resolve("mods");
        Path alpha = mods.resolve("Alpha");
        Files.createDirectories(alpha);
        Files.writeString(alpha.resolve("mod_info.json"), "{\"id\":\"alpha\"}");
        Files.writeString(alpha.resolve("data.json"), "{}");
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[\"alpha\",\"missing\"]}");

        ResourceIndexBuilder.BuildResult result = ResourceIndexBuilder.build(temporaryDirectory);

        assertEquals(1, result.index().roots().size());
        assertEquals("alpha", result.index().roots().get(0).id());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("missing")));
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("mod-only")));
    }

    @Test
    void recognizesMacBundleJavaDirectoryAsCoreResourceRoot() throws Exception {
        Path app = temporaryDirectory.resolve("Starsector.app");
        Path core = app.resolve("Contents/Resources/Java");
        Path mods = app.resolve("mods");
        Files.createDirectories(core.resolve("graphics"));
        Files.createDirectories(core.resolve("data"));
        Files.createDirectories(mods);
        Files.writeString(core.resolve("graphics/core.png"), "core");
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[]}");

        ResourceIndexBuilder.BuildResult result = ResourceIndexBuilder.build(app);

        assertEquals(List.of("core"), result.index().roots().stream().map(ResourceIndex.Root::id).toList());
        assertEquals(core.toRealPath(), result.index().roots().get(0).path());
        assertTrue(result.index().winner("graphics/core.png").isPresent());
        assertTrue(result.diagnostics().stream().noneMatch(value -> value.contains("mod-only")));
    }

    @Test
    void indexesInternalLinksAndSkipsLinksOutsideTheCanonicalRoot() throws Exception {
        Path core = temporaryDirectory.resolve("starsector-core");
        Path graphics = core.resolve("graphics");
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(graphics);
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[]}");
        Path internal = graphics.resolve("internal.png");
        Path outside = temporaryDirectory.resolve("outside.png");
        Files.writeString(internal, "inside");
        Files.writeString(outside, "outside");
        createSymbolicLinkOrSkip(graphics.resolve("alias.png"), internal);
        createSymbolicLinkOrSkip(graphics.resolve("escaped.png"), outside);

        ResourceIndexBuilder.BuildResult result = ResourceIndexBuilder.build(temporaryDirectory);

        assertTrue(result.index().winner("graphics/alias.png").isPresent());
        assertFalse(result.index().winner("graphics/escaped.png").isPresent());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("escapes its root")));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        } catch (UnsupportedOperationException | SecurityException | IOException error) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + error.getMessage());
        }
    }
}
