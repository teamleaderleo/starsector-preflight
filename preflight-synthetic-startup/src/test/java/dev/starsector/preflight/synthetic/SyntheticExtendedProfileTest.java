package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticExtendedProfileTest {
    @TempDir Path temporaryDirectory;

    @Test
    void scalesMatchTargetCountsAndFixedSeedIsDeterministic() throws Exception {
        assertEquals(112, SyntheticExtendedProfile.Scale.TINY.physicalFiles());
        assertEquals(2_516, SyntheticExtendedProfile.Scale.MEDIUM.physicalFiles());
        assertEquals(47_323, SyntheticExtendedProfile.Scale.LARGE.physicalFiles());
        var first = SyntheticExtendedProfile.generate(temporaryDirectory.resolve("first"), 12345,
                SyntheticExtendedProfile.Scale.TINY);
        var second = SyntheticExtendedProfile.generate(temporaryDirectory.resolve("second"), 12345,
                SyntheticExtendedProfile.Scale.TINY);
        var changed = SyntheticExtendedProfile.generate(temporaryDirectory.resolve("changed"), 12346,
                SyntheticExtendedProfile.Scale.TINY);
        assertEquals(first.fingerprintSha256(), second.fingerprintSha256());
        assertNotEquals(first.fingerprintSha256(), changed.fingerprintSha256());
        assertEquals(first, SyntheticExtendedProfile.readManifest(temporaryDirectory.resolve("first")));
        assertEquals(first.physicalFiles(), SyntheticExtendedProfile.fingerprint(temporaryDirectory.resolve("first")).files());
    }

    @Test
    void generatorRefusesOccupiedUnmarkedDirectories() throws Exception {
        Path occupied = temporaryDirectory.resolve("occupied");
        Files.createDirectories(occupied);
        Files.writeString(occupied.resolve("keep.txt"), "keep");
        assertThrows(IOException.class, () -> SyntheticExtendedProfile.generate(
                occupied, 1, SyntheticExtendedProfile.Scale.TINY));
        assertTrue(Files.isRegularFile(occupied.resolve("keep.txt")));
    }
}
