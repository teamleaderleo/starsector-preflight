package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FallbackTextureResolverTest {
    @Test
    void verifiedHitBypassesOriginalLoader() throws Exception {
        PreparedTexture cached = texture("00", (byte) 3);
        AtomicBoolean originalCalled = new AtomicBoolean();

        FallbackTextureResolver.Resolution resolution = FallbackTextureResolver.resolve(
                "graphics/ships/example.png",
                ignored -> TextureCacheLookup.Lookup.hit(cached),
                ignored -> {
                    originalCalled.set(true);
                    return texture("11", (byte) 7);
                });

        assertSame(cached, resolution.texture());
        assertEquals(FallbackTextureResolver.Source.CACHE, resolution.source());
        assertEquals(TextureCacheLookup.Status.HIT, resolution.cacheStatus());
        assertFalse(originalCalled.get());
    }

    @Test
    void everyNonHitStatusFallsBackToOriginalLoader() throws Exception {
        for (TextureCacheLookup.Status status : TextureCacheLookup.Status.values()) {
            if (status == TextureCacheLookup.Status.HIT) {
                continue;
            }
            PreparedTexture original = texture("22", (byte) status.ordinal());
            AtomicInteger calls = new AtomicInteger();
            FallbackTextureResolver.Resolution resolution = FallbackTextureResolver.resolve(
                    "graphics/test.png",
                    ignored -> TextureCacheLookup.Lookup.failure(status, status.name()),
                    ignored -> {
                        calls.incrementAndGet();
                        return original;
                    });

            assertSame(original, resolution.texture(), status.name());
            assertEquals(FallbackTextureResolver.Source.ORIGINAL, resolution.source(), status.name());
            assertEquals(status, resolution.cacheStatus(), status.name());
            assertEquals(1, calls.get(), status.name());
        }
    }

    @Test
    void unexpectedCacheExceptionStillUsesOriginalLoader() throws Exception {
        PreparedTexture original = texture("33", (byte) 9);
        FallbackTextureResolver.Resolution resolution = FallbackTextureResolver.resolve(
                "graphics/test.png",
                ignored -> {
                    throw new IllegalStateException("broken cache adapter");
                },
                ignored -> original);

        assertSame(original, resolution.texture());
        assertEquals(TextureCacheLookup.Status.ERROR, resolution.cacheStatus());
        assertTrue(resolution.cacheDetail().contains("broken cache adapter"));
    }

    @Test
    void originalLoaderFailureRemainsVisible() {
        IOException expected = new IOException("original decode failed");
        IOException actual = assertThrows(IOException.class, () -> FallbackTextureResolver.resolve(
                "graphics/test.png",
                ignored -> TextureCacheLookup.Lookup.miss("not cached"),
                ignored -> {
                    throw expected;
                }));
        assertSame(expected, actual);
    }

    @Test
    void concurrentFallbacksRemainDeterministic() throws Exception {
        PreparedTexture original = texture("44", (byte) 12);
        AtomicInteger originalCalls = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<FallbackTextureResolver.Resolution>> futures = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                futures.add(executor.submit(() -> FallbackTextureResolver.resolve(
                        "graphics/shared.png",
                        ignored -> TextureCacheLookup.Lookup.failure(
                                TextureCacheLookup.Status.CORRUPT,
                                "synthetic corruption"),
                        ignored -> {
                            originalCalls.incrementAndGet();
                            return original;
                        })));
            }
            for (var future : futures) {
                FallbackTextureResolver.Resolution resolution = future.get(10, TimeUnit.SECONDS);
                assertEquals(original, resolution.texture());
                assertEquals(FallbackTextureResolver.Source.ORIGINAL, resolution.source());
                assertEquals(TextureCacheLookup.Status.CORRUPT, resolution.cacheStatus());
            }
            assertEquals(128, originalCalls.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static PreparedTexture texture(String bytePair, byte value) {
        String hash = bytePair.repeat(32);
        return new PreparedTexture(
                hash,
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                4,
                PreparedTexture.rgba(1, 2, 3, 255),
                PreparedTexture.rgba(4, 5, 6, 255),
                PreparedTexture.rgba(7, 8, 9, 255),
                new byte[] {value, value, value, value});
    }
}
