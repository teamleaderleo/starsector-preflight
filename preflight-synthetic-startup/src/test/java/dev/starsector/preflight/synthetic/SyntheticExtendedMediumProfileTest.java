package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "preflight.synthetic.medium", matches = "true")
class SyntheticExtendedMediumProfileTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mediumProfileHasExactPhysicalScaleAndStableManifest() throws Exception {
        Path profile = temporaryDirectory.resolve("medium");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                88_221,
                SyntheticExtendedProfile.Scale.MEDIUM);
        SyntheticExtendedProfile.Fingerprint fingerprint = SyntheticExtendedProfile.fingerprint(profile);

        assertEquals(2_516, manifest.physicalFiles());
        assertEquals(40, manifest.effectFiles());
        assertEquals(10, manifest.streamedFiles());
        assertEquals(manifest.physicalFiles(), fingerprint.files());
        assertEquals(manifest.fingerprintSha256(), fingerprint.sha256());
        assertEquals(manifest, SyntheticExtendedProfile.readManifest(profile));
        assertTrue(fingerprint.bytes() > 0);
    }
}
