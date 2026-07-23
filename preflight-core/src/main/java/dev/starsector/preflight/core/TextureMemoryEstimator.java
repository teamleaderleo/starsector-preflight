package dev.starsector.preflight.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Computes a {@link TextureMemoryEstimate} from a {@link TextureManifest}. Pure arithmetic over
 * the exact per-texture dimensions the manifest already records — no game launch, no image decode.
 */
public final class TextureMemoryEstimator {
    /** Default number of largest allocations reported, matching the profile census. */
    public static final int DEFAULT_LARGEST_LIMIT = 25;

    private TextureMemoryEstimator() {
    }

    public static TextureMemoryEstimate estimate(TextureManifest manifest) {
        return estimate(manifest, DEFAULT_LARGEST_LIMIT);
    }

    public static TextureMemoryEstimate estimate(TextureManifest manifest, int largestLimit) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest is required");
        }
        if (largestLimit < 0) {
            throw new IllegalArgumentException("largestLimit must not be negative");
        }

        long textureCount = 0;
        long rgbTextureCount = 0;
        long rgbaTextureCount = 0;
        long decodedBytes = 0;
        List<TextureMemoryEstimate.Allocation> allocations = new ArrayList<>();

        for (Map.Entry<String, TextureManifest.Entry> entry : manifest.entries().entrySet()) {
            TextureManifest.Entry texture = entry.getValue();
            textureCount++;
            if (texture.channels() == 4) {
                rgbaTextureCount++;
            } else {
                rgbTextureCount++;
            }
            decodedBytes = saturatedAdd(decodedBytes, texture.pixelBytes());
            allocations.add(new TextureMemoryEstimate.Allocation(
                    entry.getKey(),
                    texture.width(),
                    texture.height(),
                    texture.channels(),
                    texture.pixelBytes()));
        }

        // Largest first; break ties on logical path so the report is deterministic.
        allocations.sort(Comparator
                .comparingLong(TextureMemoryEstimate.Allocation::decodedBytes).reversed()
                .thenComparing(TextureMemoryEstimate.Allocation::logicalPath));
        List<TextureMemoryEstimate.Allocation> largest =
                allocations.size() > largestLimit ? allocations.subList(0, largestLimit) : allocations;

        return new TextureMemoryEstimate(
                textureCount,
                rgbTextureCount,
                rgbaTextureCount,
                decodedBytes,
                List.copyOf(largest));
    }

    private static long saturatedAdd(long left, long right) {
        long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) < 0) {
            return Long.MAX_VALUE;
        }
        return sum;
    }
}
