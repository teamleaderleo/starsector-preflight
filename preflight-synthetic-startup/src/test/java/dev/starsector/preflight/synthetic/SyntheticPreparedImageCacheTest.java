package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticPreparedImageCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsDefensivelyAndRejectsMetadataTampering() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        String sourceHash = "a".repeat(64);
        byte[] rgba = {1, 2, 3, 4, 5, 6, 7, 8};
        SyntheticPreparedImageCache.PreparedImage image =
                new SyntheticPreparedImageCache.PreparedImage(2, 1, rgba);

        SyntheticPreparedImageCache.Lookup miss = SyntheticPreparedImageCache.lookup(cache, sourceHash);
        assertEquals(SyntheticPreparedImageCache.Status.MISS, miss.status());

        SyntheticPreparedImageCache.write(cache, sourceHash, image);
        SyntheticPreparedImageCache.Lookup hit = SyntheticPreparedImageCache.lookup(cache, sourceHash);
        assertEquals(SyntheticPreparedImageCache.Status.HIT, hit.status());
        assertEquals(2, hit.image().width());
        assertEquals(1, hit.image().height());
        assertArrayEquals(rgba, hit.image().rgba());
        assertTrue(hit.path().startsWith(cache.toAbsolutePath().normalize()));

        rgba[0] = 99;
        assertNotEquals(rgba[0], hit.image().rgba()[0]);
        byte[] returned = hit.image().rgba();
        returned[1] = 88;
        assertNotEquals(returned[1], hit.image().rgba()[1]);

        byte[] encoded = Files.readAllBytes(hit.path());
        encoded[43] = 1; // width 2 -> 1
        encoded[47] = 2; // height 1 -> 2, preserving width * height and payload length
        Files.write(hit.path(), encoded);
        SyntheticPreparedImageCache.Lookup corrupt = SyntheticPreparedImageCache.lookup(cache, sourceHash);
        assertEquals(SyntheticPreparedImageCache.Status.CORRUPT, corrupt.status());
        assertTrue(corrupt.detail().contains("checksum"), corrupt.detail());
    }

    @Test
    void invalidKeysAndTruncatedFilesFailOpen() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        SyntheticPreparedImageCache.Lookup invalid = SyntheticPreparedImageCache.lookup(cache, "bad");
        assertEquals(SyntheticPreparedImageCache.Status.ERROR, invalid.status());

        String sourceHash = "b".repeat(64);
        SyntheticPreparedImageCache.PreparedImage image =
                new SyntheticPreparedImageCache.PreparedImage(1, 1, new byte[] {9, 8, 7, 6});
        SyntheticPreparedImageCache.write(cache, sourceHash, image);
        Path path = SyntheticPreparedImageCache.cachePath(cache, sourceHash);
        Files.write(path, new byte[] {1, 2, 3});

        SyntheticPreparedImageCache.Lookup truncated = SyntheticPreparedImageCache.lookup(cache, sourceHash);
        assertEquals(SyntheticPreparedImageCache.Status.CORRUPT, truncated.status());
    }
}
