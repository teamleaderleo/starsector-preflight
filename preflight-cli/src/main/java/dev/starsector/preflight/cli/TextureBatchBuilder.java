package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

final class TextureBatchBuilder {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "gif", "wbmp", "webp", "tga");
    private static final long ESTIMATED_BYTES_PER_PIXEL = 8L;

    private TextureBatchBuilder() {
    }

    static Result build(ResourceIndex index, Path cacheDirectory, Options options)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        Path cacheRoot = cacheDirectory.toAbsolutePath().normalize();
        Files.createDirectories(cacheRoot);

        List<String> diagnostics = new ArrayList<>();
        List<Candidate> candidates = collectCandidates(index);
        List<HashedCandidate> hashed = hashCandidates(candidates, diagnostics);
        Map<BlobKey, List<HashedCandidate>> groups = new LinkedHashMap<>();
        for (HashedCandidate candidate : hashed) {
            groups.computeIfAbsent(
                    new BlobKey(candidate.sourceSha256(), PreparedTexture.Transformation.IDENTITY),
                    ignored -> new ArrayList<>()).add(candidate);
        }

        MemoryBudget budget = new MemoryBudget(options.memoryBudgetBytes());
        ExecutorService executor = Executors.newFixedThreadPool(options.workers(), workerFactory());
        ExecutorCompletionService<BlobResult> completion = new ExecutorCompletionService<>(executor);
        try {
            for (Map.Entry<BlobKey, List<HashedCandidate>> group : groups.entrySet()) {
                completion.submit(blobTask(cacheRoot, group.getKey(), group.getValue().get(0), budget));
            }

            Map<BlobKey, BlobResult> blobs = new HashMap<>();
            for (int i = 0; i < groups.size(); i++) {
                try {
                    BlobResult result = completion.take().get();
                    blobs.put(result.key(), result);
                    if (result.diagnostic() != null) {
                        diagnostics.add(result.diagnostic());
                    }
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    diagnostics.add("Unexpected texture worker failure: " + cause.getMessage());
                }
            }

            TreeMap<String, TextureManifest.Entry> manifestEntries = new TreeMap<>();
            long cacheHitBlobs = 0;
            long builtBlobs = 0;
            long failedBlobs = 0;
            long quarantinedBlobs = 0;
            long pixelBytes = 0;
            long blobBytes = 0;
            for (BlobResult result : blobs.values()) {
                if (result.success()) {
                    cacheHitBlobs += result.cacheHit() ? 1 : 0;
                    builtBlobs += result.cacheHit() ? 0 : 1;
                    quarantinedBlobs += result.quarantined() ? 1 : 0;
                    pixelBytes += result.texture().pixelBytes();
                    blobBytes += result.blobBytes();
                } else {
                    failedBlobs++;
                    quarantinedBlobs += result.quarantined() ? 1 : 0;
                }
            }

            for (HashedCandidate candidate : hashed.stream()
                    .sorted(Comparator.comparing(HashedCandidate::logicalPath))
                    .toList()) {
                BlobKey key = new BlobKey(candidate.sourceSha256(), PreparedTexture.Transformation.IDENTITY);
                BlobResult result = blobs.get(key);
                if (result == null || !result.success()) {
                    continue;
                }
                PreparedTexture texture = result.texture();
                manifestEntries.put(candidate.logicalPath(), new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        result.blobRelativePath(),
                        texture.uploadWidth(),
                        texture.uploadHeight(),
                        texture.channels(),
                        texture.pixelBytes()));
            }

            TextureManifest manifest = new TextureManifest(index.profileFingerprint(), manifestEntries);
            Path manifestPath = cacheRoot.resolve("manifests")
                    .resolve(index.profileFingerprint() + ".spfm");
            TextureManifestIO.write(manifestPath, manifest);

            long sourceBytes = hashed.stream().mapToLong(HashedCandidate::sourceBytes).sum();
            return new Result(
                    manifest,
                    manifestPath,
                    candidates.size(),
                    hashed.size(),
                    groups.size(),
                    cacheHitBlobs,
                    builtBlobs,
                    failedBlobs,
                    quarantinedBlobs,
                    hashed.size() - groups.size(),
                    sourceBytes,
                    pixelBytes,
                    blobBytes,
                    List.copyOf(new LinkedHashSet<>(diagnostics)),
                    System.nanoTime() - started);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private static List<Candidate> collectCandidates(ResourceIndex index) {
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ResourceIndex.Provider>> entry : index.entries().entrySet()) {
            if (!IMAGE_EXTENSIONS.contains(extension(entry.getKey()))) {
                continue;
            }
            ResourceIndex.Provider winner = entry.getValue().get(entry.getValue().size() - 1);
            ResourceIndex.Root root = index.roots().get(winner.rootIndex());
            candidates.add(new Candidate(
                    entry.getKey(),
                    index.resolve(winner),
                    root.id(),
                    winner.size()));
        }
        return List.copyOf(candidates);
    }

    private static List<HashedCandidate> hashCandidates(List<Candidate> candidates, List<String> diagnostics) {
        List<HashedCandidate> hashed = new ArrayList<>();
        for (Candidate candidate : candidates) {
            try {
                String hash = Hashes.sha256(candidate.source());
                hashed.add(new HashedCandidate(
                        candidate.logicalPath(),
                        candidate.source(),
                        candidate.rootId(),
                        candidate.sourceBytes(),
                        hash));
            } catch (IOException error) {
                diagnostics.add("Could not hash " + candidate.logicalPath() + " from "
                        + candidate.rootId() + ": " + error.getMessage());
            }
        }
        return List.copyOf(hashed);
    }

    private static Callable<BlobResult> blobTask(
            Path cacheRoot,
            BlobKey key,
            HashedCandidate representative,
            MemoryBudget budget) {
        return () -> {
            String relative = blobRelativePath(key);
            Path blob = cacheRoot.resolve(relative).normalize();
            if (!blob.startsWith(cacheRoot)) {
                return BlobResult.failure(key, relative, false, "Blob path escaped the cache root");
            }

            boolean quarantined = false;
            if (Files.isRegularFile(blob)) {
                try {
                    PreparedTexture existing = PreparedTextureIO.read(blob);
                    if (existing.sourceSha256().equals(key.sourceSha256())
                            && existing.transformation() == key.transformation()) {
                        return BlobResult.success(
                                key,
                                relative,
                                existing,
                                true,
                                false,
                                Files.size(blob));
                    }
                    quarantined = quarantine(cacheRoot, blob, "identity-mismatch");
                } catch (IOException error) {
                    quarantined = quarantine(cacheRoot, blob, "corrupt");
                }
            }

            long estimatedBytes;
            try {
                Dimensions dimensions = probe(representative.source());
                estimatedBytes = Math.multiplyExact(
                        Math.multiplyExact((long) dimensions.width(), dimensions.height()),
                        ESTIMATED_BYTES_PER_PIXEL);
            } catch (Exception error) {
                return BlobResult.failure(
                        key,
                        relative,
                        quarantined,
                        "Could not probe " + representative.logicalPath() + ": " + error.getMessage());
            }

            long reservation = budget.acquire(estimatedBytes);
            try {
                PreparedTexture texture = ReferenceTexturePreprocessor.prepare(
                        representative.source(),
                        key.transformation());
                if (!texture.sourceSha256().equals(key.sourceSha256())) {
                    return BlobResult.failure(
                            key,
                            relative,
                            quarantined,
                            "Source changed while preparing " + representative.logicalPath());
                }
                PreparedTextureIO.write(blob, texture);
                return BlobResult.success(
                        key,
                        relative,
                        texture,
                        false,
                        quarantined,
                        Files.size(blob));
            } catch (Exception error) {
                return BlobResult.failure(
                        key,
                        relative,
                        quarantined,
                        "Could not prepare " + representative.logicalPath() + ": " + error.getMessage());
            } finally {
                budget.release(reservation);
            }
        };
    }

    private static Dimensions probe(Path source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw new IOException("ImageIO could not open the source");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No ImageIO reader is available");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IOException("Image dimensions are invalid");
                }
                return new Dimensions(width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean quarantine(Path cacheRoot, Path blob, String reason) {
        try {
            Path directory = cacheRoot.resolve("quarantine");
            Files.createDirectories(directory);
            String name = blob.getFileName() + "." + reason + "." + Instant.now().toEpochMilli();
            Files.move(blob, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException error) {
            try {
                Files.deleteIfExists(blob);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }
    }

    private static String blobRelativePath(BlobKey key) {
        String suffix = key.transformation().name().toLowerCase(Locale.ROOT).replace('_', '-');
        return "blobs/" + key.sourceSha256().substring(0, 2) + "/"
                + key.sourceSha256() + "-" + suffix + ".spft";
    }

    private static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot <= slash || dot == path.length() - 1
                ? ""
                : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ThreadFactory workerFactory() {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "Preflight-Texture-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    record Options(int workers, long memoryBudgetBytes) {
        Options {
            if (workers < 1 || workers > 64) {
                throw new IllegalArgumentException("Texture workers must be between 1 and 64");
            }
            if (memoryBudgetBytes < 16L * 1024 * 1024) {
                throw new IllegalArgumentException("Texture memory budget must be at least 16 MiB");
            }
        }
    }

    record Result(
            TextureManifest manifest,
            Path manifestPath,
            long candidateEntries,
            long hashedEntries,
            long uniqueContent,
            long cacheHitBlobs,
            long builtBlobs,
            long failedBlobs,
            long quarantinedBlobs,
            long deduplicatedEntries,
            long sourceBytes,
            long uniquePixelBytes,
            long uniqueBlobBytes,
            List<String> diagnostics,
            long durationNanos) {
        double durationMillis() {
            return durationNanos / 1_000_000.0;
        }
    }

    private record Candidate(String logicalPath, Path source, String rootId, long sourceBytes) {
    }

    private record HashedCandidate(
            String logicalPath,
            Path source,
            String rootId,
            long sourceBytes,
            String sourceSha256) {
    }

    private record BlobKey(String sourceSha256, PreparedTexture.Transformation transformation) {
    }

    private record Dimensions(int width, int height) {
    }

    private record BlobResult(
            BlobKey key,
            String blobRelativePath,
            PreparedTexture texture,
            boolean success,
            boolean cacheHit,
            boolean quarantined,
            long blobBytes,
            String diagnostic) {
        static BlobResult success(
                BlobKey key,
                String relative,
                PreparedTexture texture,
                boolean cacheHit,
                boolean quarantined,
                long blobBytes) {
            return new BlobResult(key, relative, texture, true, cacheHit, quarantined, blobBytes, null);
        }

        static BlobResult failure(BlobKey key, String relative, boolean quarantined, String diagnostic) {
            return new BlobResult(key, relative, null, false, false, quarantined, 0, diagnostic);
        }
    }

    private static final class MemoryBudget {
        private final long maximum;
        private long used;

        MemoryBudget(long maximum) {
            this.maximum = maximum;
        }

        synchronized long acquire(long requested) throws InterruptedException {
            long reservation = Math.max(1, Math.min(maximum, requested));
            while (used + reservation > maximum) {
                wait();
            }
            used += reservation;
            return reservation;
        }

        synchronized void release(long reservation) {
            used -= reservation;
            notifyAll();
        }
    }
}
