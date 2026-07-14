package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceIndexTest {
    @Test
    void returnsOrderedProvidersAndLastWinner() {
        ResourceIndex index = new ResourceIndex(
                "fingerprint",
                List.of(
                        new ResourceIndex.Root("core", Path.of("core"), true),
                        new ResourceIndex.Root("alpha", Path.of("mods", "alpha"), false)),
                Map.of("Graphics\\Ships//Example.PNG", List.of(
                        new ResourceIndex.Provider(0, "graphics/ships/example.png", 10, 1),
                        new ResourceIndex.Provider(1, "graphics/ships/Example.PNG", 20, 2))));

        assertEquals(2, index.providers("./graphics/ships/example.png").size());
        assertEquals(1, index.winner("GRAPHICS/SHIPS/EXAMPLE.PNG").orElseThrow().rootIndex());
        assertTrue(index.winningFile("graphics/ships/example.png").orElseThrow()
                .endsWith(Path.of("mods", "alpha", "graphics", "ships", "Example.PNG")));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        assertThrows(IllegalArgumentException.class, () -> ResourceIndex.normalizeLogicalPath("../secret"));
        assertThrows(IllegalArgumentException.class, () -> ResourceIndex.normalizeLogicalPath("/absolute"));
        assertThrows(IllegalArgumentException.class, () -> ResourceIndex.normalizeLogicalPath("C:\\absolute"));
    }

    @Test
    void rejectsProvidersOutsideResolutionOrder() {
        List<ResourceIndex.Root> roots = List.of(
                new ResourceIndex.Root("core", Path.of("core"), true),
                new ResourceIndex.Root("alpha", Path.of("alpha"), false));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ResourceIndex(
                        "fingerprint",
                        roots,
                        Map.of("data/example.json", List.of(
                                new ResourceIndex.Provider(1, "data/example.json", 1, 1),
                                new ResourceIndex.Provider(0, "data/example.json", 1, 1)))));
    }

    @Test
    void rejectsUnknownProviderDuringDirectResolution() {
        ResourceIndex index = new ResourceIndex(
                "fingerprint",
                List.of(new ResourceIndex.Root("core", Path.of("core"), true)),
                Map.of("data/example.json", List.of(
                        new ResourceIndex.Provider(0, "data/example.json", 1, 1))));

        assertThrows(
                IllegalArgumentException.class,
                () -> index.resolve(new ResourceIndex.Provider(8, "data/example.json", 1, 1)));
    }
}
