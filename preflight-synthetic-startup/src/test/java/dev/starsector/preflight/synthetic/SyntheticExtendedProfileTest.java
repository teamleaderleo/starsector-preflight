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
    @TempDir
    Path temporaryDirectory;

    @Test
    void scalesExposeExactPhysicalFileCounts() {
        assertEquals(112, SyntheticExtendedProfile.Scale.TINY.physicalFiles());
        assertEquals(2_516, SyntheticExtendedProfile.Scale.MEDIUM.physicalFiles());
        assertEquals(47_323, SyntheticExtendedProfile.Scale.LARGE.physicalFiles());
    }

    @Test
    void fixedSeedProducesExactDeterministicTinyFingerprintAndManifest() throws Exception {
        SyntheticExtendedProfile.Manifest first = SyntheticExtendedProfile.generate(
                temporaryDirectory.resolve("first"),
                12_345,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedProfile.Manifest second = SyntheticExtendedProfile.generate(
                temporaryDirectory.resolve("second"),
                12_345,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedProfile.Manifest changed = SyntheticExtendedProfile.generate(
                temporaryDirectory.resolve("changed"),
                12_346,
                SyntheticExtendedProfile.Scale.TINY);

        assertEquals(112, first.physicalFiles());
        assertEquals(10, first.effectFiles());
        assertEquals(2, first.streamedFiles());
        assertEquals(first.fingerprintSha256(), second.fingerprintSha256());
        assertNotEquals(first.fingerprintSha256(), changed.fingerprintSha256());
        assertEquals(first, SyntheticExtendedProfile.readManifest(temporaryDirectory.resolve("first")));

        SyntheticExtendedProfile.Fingerprint fingerprint = SyntheticExtendedProfile.fingerprint(
                temporaryDirectory.resolve("first"));
        assertEquals(first.physicalFiles(), fingerprint.files());
        assertEquals(first.fingerprintSha256(), fingerprint.sha256());
        assertTrue(fingerprint.bytes() > 0);
    }

    @Test
    void generatorRefusesEveryOccupiedRootAndPreservesItsContents() throws Exception {
        Path occupied = temporaryDirectory.resolve("occupied");
        Files.createDirectories(occupied);
        Path preserved = occupied.resolve("keep.txt");
        Files.writeString(preserved, "keep");

        assertThrows(
                IOException.class,
                () -> SyntheticExtendedProfile.generate(
                        occupied,
                        1,
                        SyntheticExtendedProfile.Scale.TINY));
        assertEquals("keep", Files.readString(preserved));
    }

    @Test
    void malformedManifestIsRejected() throws Exception {
        Path profile = temporaryDirectory.resolve("malformed");
        SyntheticExtendedProfile.generate(profile, 7, SyntheticExtendedProfile.Scale.TINY);
        Files.writeString(
                profile.resolve(SyntheticExtendedProfile.MANIFEST),
                "version=" + SyntheticExtendedProfile.VERSION + "\nseed=7\n");

        assertThrows(IOException.class, () -> SyntheticExtendedProfile.readManifest(profile));
    }
}
