package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TextureMemoryEstimatorTest {

    @Test
    void emptyManifestEstimatesZero() {
        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(Map.of()));

        assertEquals(0, estimate.textureCount());
        assertEquals(0, estimate.decodedBytes());
        assertEquals(0, estimate.fullMipChainUpperBoundBytes());
        assertTrue(estimate.largestAllocations().isEmpty());
    }

    @Test
    void sumsDecodedBytesAndChannelCounts() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/a.png", entry(1, 2, 2, 3)); // 12 bytes, RGB
        entries.put("graphics/b.png", entry(2, 2, 2, 4)); // 16 bytes, RGBA
        entries.put("graphics/c.png", entry(3, 4, 4, 4)); // 64 bytes, RGBA

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries));

        assertEquals(3, estimate.textureCount());
        assertEquals(1, estimate.rgbTextureCount());
        assertEquals(2, estimate.rgbaTextureCount());
        assertEquals(92, estimate.decodedBytes());
    }

    @Test
    void reportsLargestAllocationsDescendingWithinLimit() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/small.png", entry(1, 2, 2, 3)); // 12
        entries.put("graphics/big.png", entry(2, 8, 8, 4)); // 256
        entries.put("graphics/mid.png", entry(3, 4, 4, 4)); // 64

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries), 2);

        List<TextureMemoryEstimate.Allocation> largest = estimate.largestAllocations();
        assertEquals(2, largest.size());
        assertEquals("graphics/big.png", largest.get(0).logicalPath());
        assertEquals(256, largest.get(0).decodedBytes());
        assertEquals("graphics/mid.png", largest.get(1).logicalPath());
    }

    @Test
    void breaksAllocationTiesDeterministicallyByPath() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/zeta.png", entry(1, 2, 2, 4)); // 16
        entries.put("graphics/alpha.png", entry(2, 2, 2, 4)); // 16 (tie)

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries));

        assertEquals("graphics/alpha.png", estimate.largestAllocations().get(0).logicalPath());
        assertEquals("graphics/zeta.png", estimate.largestAllocations().get(1).logicalPath());
    }

    @Test
    void projectsEnhancementByPixelSquare() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/a.png", entry(1, 4, 4, 4)); // 64 bytes base

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries));

        TextureMemoryEstimate.EnhancementProjection twoX = estimate.project(2);
        assertEquals(4, twoX.pixelMultiplier());
        assertEquals(256, twoX.overlayBytes());
        assertEquals(320, twoX.combinedBytes()); // base + overlay if both resident

        TextureMemoryEstimate.EnhancementProjection fourX = estimate.project(4);
        assertEquals(16, fourX.pixelMultiplier());
        assertEquals(1024, fourX.overlayBytes());
    }

    @Test
    void fullMipChainUpperBoundAddsAtMostOneThird() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/a.png", entry(1, 4, 4, 4)); // 64 bytes base

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries));

        // 64 + ceil(64/3) = 64 + 22 = 86
        assertEquals(86, estimate.fullMipChainUpperBoundBytes());
    }

    @Test
    void budgetComparisonUsesExactBaseSize() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/a.png", entry(1, 4, 4, 4)); // 64 bytes

        TextureMemoryEstimate estimate = TextureMemoryEstimator.estimate(manifest(entries));

        assertFalse(estimate.exceedsBudget(64));
        assertTrue(estimate.exceedsBudget(63));
    }

    @Test
    void reportValuesExposeExactFiguresAndEnhancementScales() {
        Map<String, TextureManifest.Entry> entries = new LinkedHashMap<>();
        entries.put("graphics/a.png", entry(1, 4, 4, 4)); // 64 bytes

        Map<String, Object> values = TextureMemoryEstimator.estimate(manifest(entries)).toReportValues();

        assertEquals(1L, values.get("textureCount"));
        assertEquals(64L, values.get("decodedBytes"));
        @SuppressWarnings("unchecked")
        Map<String, Object> enhancement = (Map<String, Object>) values.get("enhancement");
        assertTrue(enhancement.containsKey("2x"));
        assertTrue(enhancement.containsKey("4x"));
        assertTrue(values.containsKey("largestAllocations"));
    }

    private static TextureManifest manifest(Map<String, TextureManifest.Entry> entries) {
        return new TextureManifest(sha(999), entries);
    }

    private static TextureManifest.Entry entry(int shaSeed, int width, int height, int channels) {
        return new TextureManifest.Entry(
                sha(shaSeed),
                PreparedTexture.Transformation.IDENTITY,
                "blobs/" + shaSeed + ".spft",
                width,
                height,
                channels,
                width * height * channels);
    }

    private static String sha(int seed) {
        return String.format("%064x", BigInteger.valueOf(seed));
    }
}
