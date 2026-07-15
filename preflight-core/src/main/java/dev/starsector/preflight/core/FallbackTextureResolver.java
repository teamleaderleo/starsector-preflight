package dev.starsector.preflight.core;

import java.util.Objects;

/**
 * Uses a verified cached texture when available and otherwise delegates to the original loader.
 *
 * <p>Cache lookup failures are deliberately contained. Exceptions from the original loader remain
 * visible because they describe the behavior the game would have had without Preflight.</p>
 */
public final class FallbackTextureResolver {
    private FallbackTextureResolver() {
    }

    public static Resolution resolve(
            String logicalPath,
            TextureCacheLookup cache,
            OriginalLoader originalLoader) throws Exception {
        Objects.requireNonNull(logicalPath, "logicalPath");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(originalLoader, "originalLoader");

        TextureCacheLookup.Lookup lookup;
        try {
            lookup = Objects.requireNonNull(cache.lookup(logicalPath), "cache lookup result");
        } catch (RuntimeException error) {
            lookup = TextureCacheLookup.Lookup.failure(
                    TextureCacheLookup.Status.ERROR,
                    "Cache lookup threw " + error.getClass().getSimpleName() + detail(error));
        }

        if (lookup.status() == TextureCacheLookup.Status.HIT) {
            return new Resolution(lookup.texture(), Source.CACHE, lookup.status(), lookup.detail());
        }

        PreparedTexture original = Objects.requireNonNull(
                originalLoader.load(logicalPath),
                "original loader returned null for " + logicalPath);
        return new Resolution(original, Source.ORIGINAL, lookup.status(), lookup.detail());
    }

    private static String detail(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    @FunctionalInterface
    public interface OriginalLoader {
        PreparedTexture load(String logicalPath) throws Exception;
    }

    public enum Source {
        CACHE,
        ORIGINAL
    }

    public record Resolution(
            PreparedTexture texture,
            Source source,
            TextureCacheLookup.Status cacheStatus,
            String cacheDetail) {
        public Resolution {
            texture = Objects.requireNonNull(texture, "texture");
            source = Objects.requireNonNull(source, "source");
            cacheStatus = Objects.requireNonNull(cacheStatus, "cacheStatus");
            cacheDetail = cacheDetail == null ? "" : cacheDetail;
            if (source == Source.CACHE && cacheStatus != TextureCacheLookup.Status.HIT) {
                throw new IllegalArgumentException("Cache source requires HIT status");
            }
            if (source == Source.ORIGINAL && cacheStatus == TextureCacheLookup.Status.HIT) {
                throw new IllegalArgumentException("Original source may not report HIT status");
            }
        }
    }
}
