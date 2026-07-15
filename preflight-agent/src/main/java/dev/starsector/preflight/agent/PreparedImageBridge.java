package dev.starsector.preflight.agent;

import dev.starsector.preflight.core.ManifestTextureCacheLookup;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureCacheLookup;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Process-local bridge called by the exact-version texture-loader wrapper.
 *
 * <p>Every unavailable, stale, corrupt, unsupported, or unexpected cache result returns {@code null}
 * so the transformed method can continue into Starsector's untouched original implementation.</p>
 */
public final class PreparedImageBridge {
    private static final int INTERNAL_ERROR_LIMIT = 8;
    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong FALLBACKS = new AtomicLong();
    private static final AtomicLong INTERNAL_ERRORS = new AtomicLong();
    private static final AtomicLongArray STATUSES = new AtomicLongArray(TextureCacheLookup.Status.values().length);
    private static volatile State state = State.disabled("Prepared image cache is not configured");

    private PreparedImageBridge() {
    }

    static synchronized void configure(Path cacheRoot, Path manifestPath, Path indexPath) throws IOException {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(indexPath, "indexPath");

        Path absoluteCache = cacheRoot.toAbsolutePath().normalize();
        Path absoluteManifest = manifestPath.toAbsolutePath().normalize();
        Path absoluteIndex = indexPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(absoluteCache)) {
            throw new IOException("Prepared texture cache root does not exist: " + absoluteCache);
        }
        if (!Files.isRegularFile(absoluteManifest)) {
            throw new IOException("Prepared texture manifest does not exist: " + absoluteManifest);
        }
        if (!Files.isRegularFile(absoluteIndex)) {
            throw new IOException("Resource index does not exist: " + absoluteIndex);
        }

        ResourceIndex index = ResourceIndexIO.read(absoluteIndex);
        TextureManifest manifest = TextureManifestIO.read(absoluteManifest);
        if (!index.profileFingerprint().equals(manifest.profileFingerprint())) {
            throw new IOException("Resource index and texture manifest profile fingerprints differ");
        }

        ManifestTextureCacheLookup lookup = new ManifestTextureCacheLookup(absoluteCache, manifest);
        state = State.enabled(
                absoluteCache,
                absoluteManifest,
                absoluteIndex,
                index,
                lookup,
                physicalWinners(index));
        resetCounters();
    }

    static synchronized void disable(String detail) {
        state = State.disabled(detail == null || detail.isBlank()
                ? "Prepared image cache is disabled"
                : detail);
    }

    static boolean ready() {
        return state.enabled;
    }

    /** Returns a verified prepared image or {@code null} to execute the original decoder. */
    public static BufferedImage lookup(String requestedPath) {
        State active = state;
        if (!active.enabled || requestedPath == null || requestedPath.isBlank()) {
            FALLBACKS.incrementAndGet();
            STATUSES.incrementAndGet(TextureCacheLookup.Status.DISABLED.ordinal());
            return null;
        }

        try {
            String logicalPath = resolveLogicalPath(active, requestedPath);
            if (logicalPath == null || !sourceMetadataMatches(active.index, logicalPath)) {
                fallback(TextureCacheLookup.Status.STALE);
                return null;
            }

            TextureCacheLookup.Lookup result = active.lookup.lookup(logicalPath);
            STATUSES.incrementAndGet(result.status().ordinal());
            if (result.status() != TextureCacheLookup.Status.HIT) {
                FALLBACKS.incrementAndGet();
                return null;
            }

            PreparedTexture texture = result.texture();
            if (texture.transformation() != PreparedTexture.Transformation.IDENTITY
                    || texture.originalWidth() != texture.uploadWidth()
                    || texture.originalHeight() != texture.uploadHeight()) {
                fallback(TextureCacheLookup.Status.UNSUPPORTED);
                return null;
            }

            BufferedImage image = toBufferedImage(texture);
            HITS.incrementAndGet();
            return image;
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            long failures = INTERNAL_ERRORS.incrementAndGet();
            fallback(TextureCacheLookup.Status.ERROR);
            if (failures >= INTERNAL_ERROR_LIMIT) {
                disable("Prepared image cache disabled after " + failures + " internal lookup failures: "
                        + message(error));
            }
            return null;
        }
    }

    static Snapshot snapshot() {
        State active = state;
        Map<String, Long> statuses = new HashMap<>();
        for (TextureCacheLookup.Status status : TextureCacheLookup.Status.values()) {
            statuses.put(status.name(), STATUSES.get(status.ordinal()));
        }
        return new Snapshot(
                active.enabled,
                active.detail,
                active.cacheRoot,
                active.manifestPath,
                active.indexPath,
                HITS.get(),
                FALLBACKS.get(),
                INTERNAL_ERRORS.get(),
                Map.copyOf(statuses));
    }

    static synchronized void resetForTests() {
        state = State.disabled("Prepared image cache is not configured");
        resetCounters();
    }

    private static void fallback(TextureCacheLookup.Status status) {
        FALLBACKS.incrementAndGet();
        STATUSES.incrementAndGet(status.ordinal());
    }

    private static void resetCounters() {
        HITS.set(0);
        FALLBACKS.set(0);
        INTERNAL_ERRORS.set(0);
        for (int i = 0; i < STATUSES.length(); i++) {
            STATUSES.set(i, 0);
        }
    }

    private static String resolveLogicalPath(State active, String requestedPath) {
        try {
            String logical = ResourceIndex.normalizeLogicalPath(requestedPath);
            if (active.index.winner(logical).isPresent()) {
                return logical;
            }
        } catch (RuntimeException ignored) {
            // The loader may supply a physical absolute or working-directory-relative path.
        }

        Path physical = physicalPath(requestedPath);
        if (physical == null) {
            return null;
        }
        String exact = active.physicalWinners.get(normalizePhysical(physical, false));
        if (exact != null) {
            return exact;
        }
        return active.physicalWinners.get(normalizePhysical(physical, true));
    }

    private static Path physicalPath(String requestedPath) {
        try {
            if (requestedPath.startsWith("file:")) {
                return Path.of(URI.create(requestedPath)).toAbsolutePath().normalize();
            }
            Path value = Path.of(requestedPath);
            if (value.isAbsolute()) {
                return value.toAbsolutePath().normalize();
            }
            return Path.of(System.getProperty("user.dir")).resolve(value).toAbsolutePath().normalize();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Map<String, String> physicalWinners(ResourceIndex index) {
        Map<String, String> result = new HashMap<>(Math.max(16, index.entryCount() * 2));
        Map<String, String> folded = new HashMap<>();
        Map<String, Boolean> ambiguousFolded = new HashMap<>();
        index.entries().forEach((logical, providers) -> {
            ResourceIndex.Provider winner = providers.get(providers.size() - 1);
            Path physical = index.resolve(winner);
            result.putIfAbsent(normalizePhysical(physical, false), logical);
            String lower = normalizePhysical(physical, true);
            String prior = folded.putIfAbsent(lower, logical);
            if (prior != null && !prior.equals(logical)) {
                ambiguousFolded.put(lower, true);
            }
        });
        folded.forEach((path, logical) -> {
            if (!ambiguousFolded.containsKey(path)) {
                result.putIfAbsent(path, logical);
            }
        });
        return Map.copyOf(result);
    }

    private static String normalizePhysical(Path path, boolean foldCase) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return foldCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private static boolean sourceMetadataMatches(ResourceIndex index, String logicalPath) {
        ResourceIndex.Provider provider = index.winner(logicalPath).orElse(null);
        if (provider == null) {
            return false;
        }
        Path source = index.resolve(provider);
        try {
            BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
            return attributes.isRegularFile()
                    && attributes.size() == provider.size()
                    && Math.max(0, attributes.lastModifiedTime().toMillis()) == provider.modifiedMillis();
        } catch (IOException error) {
            return false;
        }
    }

    private static BufferedImage toBufferedImage(PreparedTexture texture) {
        int width = texture.originalWidth();
        int height = texture.originalHeight();
        int channels = texture.channels();
        byte[] pixels = texture.pixels();
        int[] argb = new int[Math.multiplyExact(width, height)];
        int rowBytes = Math.multiplyExact(width, channels);
        for (int y = 0; y < height; y++) {
            int source = Math.multiplyExact(height - 1 - y, rowBytes);
            int target = Math.multiplyExact(y, width);
            for (int x = 0; x < width; x++) {
                int offset = source + x * channels;
                int red = Byte.toUnsignedInt(pixels[offset]);
                int green = Byte.toUnsignedInt(pixels[offset + 1]);
                int blue = Byte.toUnsignedInt(pixels[offset + 2]);
                int alpha = channels == 4 ? Byte.toUnsignedInt(pixels[offset + 3]) : 255;
                argb[target + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    record Snapshot(
            boolean enabled,
            String detail,
            Path cacheRoot,
            Path manifestPath,
            Path indexPath,
            long hits,
            long fallbacks,
            long internalErrors,
            Map<String, Long> statuses) {
    }

    private static final class State {
        private final boolean enabled;
        private final String detail;
        private final Path cacheRoot;
        private final Path manifestPath;
        private final Path indexPath;
        private final ResourceIndex index;
        private final ManifestTextureCacheLookup lookup;
        private final Map<String, String> physicalWinners;

        private State(
                boolean enabled,
                String detail,
                Path cacheRoot,
                Path manifestPath,
                Path indexPath,
                ResourceIndex index,
                ManifestTextureCacheLookup lookup,
                Map<String, String> physicalWinners) {
            this.enabled = enabled;
            this.detail = detail;
            this.cacheRoot = cacheRoot;
            this.manifestPath = manifestPath;
            this.indexPath = indexPath;
            this.index = index;
            this.lookup = lookup;
            this.physicalWinners = physicalWinners;
        }

        private static State enabled(
                Path cacheRoot,
                Path manifestPath,
                Path indexPath,
                ResourceIndex index,
                ManifestTextureCacheLookup lookup,
                Map<String, String> physicalWinners) {
            return new State(true, "Prepared image cache is ready", cacheRoot, manifestPath, indexPath,
                    index, lookup, physicalWinners);
        }

        private static State disabled(String detail) {
            return new State(false, detail, null, null, null, null, null, Map.of());
        }
    }
}
