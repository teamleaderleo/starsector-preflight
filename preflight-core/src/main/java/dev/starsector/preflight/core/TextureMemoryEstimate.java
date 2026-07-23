package dev.starsector.preflight.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoded-texture / VRAM footprint estimate derived from a {@link TextureManifest}.
 *
 * <p>{@link #decodedBytes()} is the exact resident base-level size of the manifest's prepared
 * textures ({@code width * height * channels} summed). Everything else is clearly-labelled
 * advisory arithmetic on top of that exact figure: the full-mip-chain upper bound, and the
 * enhancement projections used to make the cost of an upscaled overlay visible before it is
 * generated. The estimate covers only textures present in the manifest (POT/NPOT identity
 * textures within the prepared-texture size limits); it is a strong floor for VRAM planning,
 * not a claim about every image the game may allocate.
 */
public record TextureMemoryEstimate(
        long textureCount,
        long rgbTextureCount,
        long rgbaTextureCount,
        long decodedBytes,
        List<Allocation> largestAllocations) {

    /** Linear scale factors an enhancement overlay is projected for. */
    public static final int[] ENHANCEMENT_SCALES = {2, 4};

    public TextureMemoryEstimate {
        largestAllocations = List.copyOf(largestAllocations);
    }

    /** A single prepared texture's exact resident base-level size. */
    public record Allocation(String logicalPath, int width, int height, int channels, long decodedBytes) {
    }

    /** Projected footprint of an enhancement overlay at a given linear scale. */
    public record EnhancementProjection(int linearScale, long pixelMultiplier, long overlayBytes, long combinedBytes) {
    }

    /**
     * Upper bound if a full mip chain were generated for every texture. A complete chain adds at
     * most one third of the base level (1/4 + 1/16 + ... &lt; 1/3), so this is {@code base + ceil(base/3)}.
     * Advisory: Starsector 2D sprites do not necessarily generate mips.
     */
    public long fullMipChainUpperBoundBytes() {
        return saturatedAdd(decodedBytes, ceilDiv(decodedBytes, 3));
    }

    /**
     * Projects an overlay at {@code linearScale} (e.g. 2 = 2&times; width&amp;height = 4&times; pixels).
     * {@code overlayBytes} is the overlay alone; {@code combinedBytes} is the worst case where both the
     * original and the overlay are resident at once.
     */
    public EnhancementProjection project(int linearScale) {
        if (linearScale < 1) {
            throw new IllegalArgumentException("linearScale must be positive");
        }
        long pixelMultiplier = (long) linearScale * linearScale;
        long overlayBytes = saturatedMultiply(decodedBytes, pixelMultiplier);
        return new EnhancementProjection(
                linearScale, pixelMultiplier, overlayBytes, saturatedAdd(decodedBytes, overlayBytes));
    }

    /** True when the exact resident base size exceeds a configurable Asset Lab budget. */
    public boolean exceedsBudget(long budgetBytes) {
        if (budgetBytes < 0) {
            throw new IllegalArgumentException("budgetBytes must not be negative");
        }
        return decodedBytes > budgetBytes;
    }

    /** Deterministic, JSON-ready view for embedding in {@code doctor} and profile reports. */
    public Map<String, Object> toReportValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("textureCount", textureCount);
        values.put("rgbTextureCount", rgbTextureCount);
        values.put("rgbaTextureCount", rgbaTextureCount);
        values.put("decodedBytes", decodedBytes);
        values.put("fullMipChainUpperBoundBytes", fullMipChainUpperBoundBytes());

        Map<String, Object> enhancement = new LinkedHashMap<>();
        for (int scale : ENHANCEMENT_SCALES) {
            EnhancementProjection projection = project(scale);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pixelMultiplier", projection.pixelMultiplier());
            entry.put("overlayBytes", projection.overlayBytes());
            entry.put("combinedBytes", projection.combinedBytes());
            enhancement.put(scale + "x", Map.copyOf(entry));
        }
        values.put("enhancement", Map.copyOf(enhancement));

        List<Map<String, Object>> largest = largestAllocations.stream()
                .map(allocation -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("logicalPath", allocation.logicalPath());
                    entry.put("width", allocation.width());
                    entry.put("height", allocation.height());
                    entry.put("channels", allocation.channels());
                    entry.put("decodedBytes", allocation.decodedBytes());
                    return Map.copyOf(entry);
                })
                .toList();
        values.put("largestAllocations", List.copyOf(largest));
        return Map.copyOf(values);
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static long saturatedAdd(long left, long right) {
        long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) < 0) {
            return Long.MAX_VALUE;
        }
        return sum;
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
