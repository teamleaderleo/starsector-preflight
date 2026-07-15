package dev.starsector.preflight.core;

import java.util.Objects;

/**
 * Renderer-neutral lookup contract for prepared texture caches.
 *
 * <p>Implementations report cache failures as data instead of throwing them into the game loading
 * path. A runtime adapter may therefore fall back to the original loader for every non-hit result.</p>
 */
@FunctionalInterface
public interface TextureCacheLookup {
    Lookup lookup(String logicalPath);

    enum Status {
        HIT,
        MISS,
        STALE,
        CORRUPT,
        UNSUPPORTED,
        DISABLED,
        ERROR
    }

    record Lookup(Status status, PreparedTexture texture, String detail) {
        public Lookup {
            status = Objects.requireNonNull(status, "status");
            if (status == Status.HIT && texture == null) {
                throw new IllegalArgumentException("A cache hit requires a prepared texture");
            }
            if (status != Status.HIT && texture != null) {
                throw new IllegalArgumentException("Only a cache hit may contain a prepared texture");
            }
            detail = detail == null ? "" : detail;
        }

        public static Lookup hit(PreparedTexture texture) {
            return new Lookup(Status.HIT, Objects.requireNonNull(texture, "texture"), "");
        }

        public static Lookup miss(String detail) {
            return failure(Status.MISS, detail);
        }

        public static Lookup failure(Status status, String detail) {
            if (status == Status.HIT) {
                throw new IllegalArgumentException("Use hit() for cache hits");
            }
            return new Lookup(status, null, detail);
        }
    }
}
