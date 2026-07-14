package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameLayoutTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void locatesModsInsideMacApplicationBundle() throws Exception {
        Path app = temporaryDirectory.resolve("Starsector.app");
        Path mods = app.resolve("Contents/Resources/Java/mods");
        Files.createDirectories(mods);
        Path enabled = Files.writeString(mods.resolve("enabled_mods.json"), "{\"enabledMods\":[]}");

        GameLayout layout = GameLayout.locate(app);

        assertEquals(mods.toAbsolutePath().normalize(), layout.modsDirectory());
        assertEquals(enabled.toAbsolutePath().normalize(), layout.enabledModsFile());
    }
}
