package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticExtendedResourceIndexTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void tinyProfileHasExactLooseJarAndCollisionCounts() throws Exception {
        Path profile = temporaryDirectory.resolve("profile");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                12_345,
                SyntheticExtendedProfile.Scale.TINY);

        SyntheticExtendedResourceIndex.Build build = SyntheticExtendedResourceIndex.build(
                profile,
                manifest.fingerprintSha256());
        SyntheticExtendedResourceIndex index = build.index();

        assertEquals(4, build.jarScans());
        assertEquals(108, build.looseVisits());
        assertEquals(112, build.physicalFilesVisited());
        assertEquals(8, build.jarEntriesVisited());
        assertTrue(build.bytesHashed() > 0);
        assertEquals(89, index.providerCount());
        assertEquals(23, index.collidedPaths());
        assertEquals(27, index.collisionEvents());
        assertEquals(manifest.fingerprintSha256(), index.profileFingerprintSha256());
        assertEquals(64, index.providerDigest().length());

        SyntheticExtendedResourceIndex.Provider shared = index.providers().get(
                "classpath/generated/shared-001.txt");
        assertEquals(SyntheticExtendedResourceIndex.Kind.LOOSE, shared.kind());
        assertArrayEquals(
                "loose-override-12345-1\n".getBytes(StandardCharsets.UTF_8),
                index.readBytes("classpath/generated/shared-001.txt"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> index.providers().clear());
    }

    @Test
    void persistedIndexRoundTripsAndRejectsCorruptionAndCrossProfileSubstitution()
            throws Exception {
        Path firstProfile = temporaryDirectory.resolve("first-profile");
        Path secondProfile = temporaryDirectory.resolve("second-profile");
        SyntheticExtendedProfile.Manifest firstManifest = SyntheticExtendedProfile.generate(
                firstProfile,
                101,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedProfile.Manifest secondManifest = SyntheticExtendedProfile.generate(
                secondProfile,
                202,
                SyntheticExtendedProfile.Scale.TINY);

        SyntheticExtendedResourceIndex index = SyntheticExtendedResourceIndex.build(
                firstProfile,
                firstManifest.fingerprintSha256()).index();
        Path firstIndex = SyntheticExtendedResourceIndex.cachePath(
                temporaryDirectory.resolve("cache"),
                firstManifest.fingerprintSha256());
        index.write(firstIndex);

        SyntheticExtendedResourceIndex.Lookup hit = SyntheticExtendedResourceIndex.lookup(
                firstIndex,
                firstProfile,
                firstManifest.fingerprintSha256());
        assertEquals(SyntheticExtendedResourceIndex.Status.HIT, hit.status(), hit.detail());
        assertEquals(index.providerDigest(), hit.index().providerDigest());
        assertArrayEquals(
                index.readBytes("classpath/generated/jar-only-000.txt"),
                hit.index().readBytes("classpath/generated/jar-only-000.txt"));

        Path secondIndex = SyntheticExtendedResourceIndex.cachePath(
                temporaryDirectory.resolve("cache"),
                secondManifest.fingerprintSha256());
        Files.createDirectories(secondIndex.getParent());
        Files.copy(firstIndex, secondIndex, StandardCopyOption.REPLACE_EXISTING);
        SyntheticExtendedResourceIndex.Lookup substituted = SyntheticExtendedResourceIndex.lookup(
                secondIndex,
                secondProfile,
                secondManifest.fingerprintSha256());
        assertEquals(
                SyntheticExtendedResourceIndex.Status.CORRUPT,
                substituted.status(),
                substituted.detail());

        Files.write(firstIndex, new byte[] {1, 2, 3});
        SyntheticExtendedResourceIndex.Lookup corrupt = SyntheticExtendedResourceIndex.lookup(
                firstIndex,
                firstProfile,
                firstManifest.fingerprintSha256());
        assertEquals(SyntheticExtendedResourceIndex.Status.CORRUPT, corrupt.status());
    }

    @Test
    void providerMutationAndIndexWriteFailureAreExplicitWithoutDestroyingBuiltIndex()
            throws Exception {
        Path profile = temporaryDirectory.resolve("mutable-profile");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                303,
                SyntheticExtendedProfile.Scale.TINY);
        SyntheticExtendedResourceIndex index = SyntheticExtendedResourceIndex.build(
                profile,
                manifest.fingerprintSha256()).index();

        String logical = "data/generated/resource-00000.json";
        SyntheticExtendedResourceIndex.Provider provider = index.providers().get(logical);
        Path physical = profile.resolve(provider.relativeSource());
        Files.writeString(physical, "changed\n");
        assertThrows(IOException.class, () -> index.readBytes(logical));

        Path directoryTarget = temporaryDirectory.resolve("index-directory.spxr");
        Files.createDirectory(directoryTarget);
        assertThrows(IOException.class, () -> index.write(directoryTarget));
        assertEquals(89, index.providerCount());
        assertEquals(64, index.providerDigest().length());
    }

    @Test
    void missingAndNonFileTargetsHaveDistinctStatuses() throws Exception {
        Path profile = temporaryDirectory.resolve("profile");
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.generate(
                profile,
                77,
                SyntheticExtendedProfile.Scale.TINY);
        Path missing = SyntheticExtendedResourceIndex.cachePath(
                temporaryDirectory.resolve("cache"),
                manifest.fingerprintSha256());
        assertEquals(
                SyntheticExtendedResourceIndex.Status.MISS,
                SyntheticExtendedResourceIndex.lookup(
                        missing,
                        profile,
                        manifest.fingerprintSha256()).status());

        Files.createDirectories(missing);
        assertEquals(
                SyntheticExtendedResourceIndex.Status.ERROR,
                SyntheticExtendedResourceIndex.lookup(
                        missing,
                        profile,
                        manifest.fingerprintSha256()).status());
    }
}
