package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One isolated synthetic startup pass. Tests launch this class in a fresh JVM for every pass. */
public final class SyntheticStartupWorker {
    private static final int MAX_MODS = 1_000;
    private static final int MAX_MOD_ORDER_BYTES = 1024 * 1024;
    private static final int MAX_MOD_NAME_CHARS = 255;
    private static final int MAX_DISCOVERED_FILES = 100_000;
    private static final int MAX_LOGICAL_PATH_CHARS = 1_024;
    private static final long MAX_WINNING_FILE_BYTES = 64L * 1024 * 1024;

    private SyntheticStartupWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: SyntheticStartupWorker <profile-root> <cache-root> <report.json>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(Path profileRoot, Path cacheRoot, Path reportPath) throws IOException {
        Path profile = profileRoot.toAbsolutePath().normalize();
        Path modsRoot = profile.resolve("mods").normalize();
        if (!modsRoot.startsWith(profile) || !Files.isDirectory(modsRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic profile is missing its mods directory: " + modsRoot);
        }
        List<String> modOrder = readModOrder(profile.resolve("mod-order.txt"));
        TreeMap<String, Provider> providers = new TreeMap<>();
        int discoveredFiles = 0;
        int resourceCollisions = 0;

        for (String modName : modOrder) {
            Path modRoot = modsRoot.resolve(modName).normalize();
            if (!modRoot.startsWith(modsRoot) || !Files.isDirectory(modRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Synthetic mod directory is missing or escaped its root: " + modName);
            }
            int remaining = MAX_DISCOVERED_FILES - discoveredFiles;
            List<Path> files;
            try (var stream = Files.walk(modRoot)) {
                files = stream
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .limit((long) remaining + 1L)
                        .sorted((left, right) -> logicalPath(modRoot, left).compareTo(logicalPath(modRoot, right)))
                        .toList();
            }
            if (files.size() > remaining) {
                throw new IOException("Synthetic profile exceeds " + MAX_DISCOVERED_FILES + " files");
            }
            for (Path file : files) {
                discoveredFiles++;
                String logical = logicalPath(modRoot, file);
                Provider previous = providers.put(logical, new Provider(modName, file));
                if (previous != null) resourceCollisions++;
            }
        }

        MessageDigest providerDigest = sha256Digest();
        MessageDigest preparedOutputDigest = sha256Digest();
        long winningSourceBytes = 0;
        long preparedBytesServed = 0;
        long preparedBytesWritten = 0;
        int imageResources = 0;
        int imageDecoderCalls = 0;
        int imageCacheHits = 0;
        int imageCacheMisses = 0;
        int imageCacheCorruptFallbacks = 0;
        int imageCacheReadErrors = 0;
        int imageCacheWriteErrors = 0;

        for (Map.Entry<String, Provider> entry : providers.entrySet()) {
            String logical = entry.getKey();
            Provider provider = entry.getValue();
            HashedFile source = sha256(provider.path());
            winningSourceBytes = Math.addExact(winningSourceBytes, source.bytes());
            updateLengthPrefixed(providerDigest, logical);
            updateLengthPrefixed(providerDigest, provider.modName());
            updateLengthPrefixed(providerDigest, source.sha256());

            if (!logical.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            imageResources++;
            SyntheticPreparedImageCache.Lookup lookup =
                    SyntheticPreparedImageCache.lookup(cacheRoot, source.sha256());
            SyntheticPreparedImageCache.PreparedImage image;
            switch (lookup.status()) {
                case HIT -> {
                    imageCacheHits++;
                    image = lookup.image();
                    preparedBytesServed = Math.addExact(preparedBytesServed, image.internalRgba().length);
                }
                case MISS -> {
                    imageCacheMisses++;
                    imageDecoderCalls++;
                    image = SyntheticPreparedImageCache.decodePng(provider.path());
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, image.internalRgba().length);
                    if (!writePrepared(cacheRoot, source.sha256(), image)) imageCacheWriteErrors++;
                }
                case CORRUPT -> {
                    imageCacheCorruptFallbacks++;
                    imageDecoderCalls++;
                    image = SyntheticPreparedImageCache.decodePng(provider.path());
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, image.internalRgba().length);
                    if (!writePrepared(cacheRoot, source.sha256(), image)) imageCacheWriteErrors++;
                }
                case ERROR -> {
                    imageCacheReadErrors++;
                    imageDecoderCalls++;
                    image = SyntheticPreparedImageCache.decodePng(provider.path());
                    preparedBytesWritten = Math.addExact(preparedBytesWritten, image.internalRgba().length);
                    if (!writePrepared(cacheRoot, source.sha256(), image)) imageCacheWriteErrors++;
                }
                default -> throw new IllegalStateException("Unknown synthetic image cache status: " + lookup.status());
            }
            updateLengthPrefixed(preparedOutputDigest, logical);
            preparedOutputDigest.update(image.internalRgba());
        }

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-startup-v1");
        report.put("processId", ProcessHandle.current().pid());
        report.put("modCount", modOrder.size());
        report.put("discoveredFiles", discoveredFiles);
        report.put("winningResources", providers.size());
        report.put("resourceCollisions", resourceCollisions);
        report.put("winningSourceBytes", winningSourceBytes);
        report.put("imageResources", imageResources);
        report.put("imageDecoderCalls", imageDecoderCalls);
        report.put("imageCacheHits", imageCacheHits);
        report.put("imageCacheMisses", imageCacheMisses);
        report.put("imageCacheCorruptFallbacks", imageCacheCorruptFallbacks);
        report.put("imageCacheReadErrors", imageCacheReadErrors);
        report.put("imageCacheWriteErrors", imageCacheWriteErrors);
        report.put("preparedBytesServed", preparedBytesServed);
        report.put("preparedBytesWritten", preparedBytesWritten);
        report.put("winningProvidersSha256", HexFormat.of().formatHex(providerDigest.digest()));
        report.put("preparedImageOutputsSha256", HexFormat.of().formatHex(preparedOutputDigest.digest()));
        writeAtomic(reportPath, Json.object(report) + System.lineSeparator());
    }

    private static List<String> readModOrder(Path orderFile) throws IOException {
        if (!Files.isRegularFile(orderFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic profile is missing mod-order.txt: " + orderFile);
        }
        String orderText = new String(readBounded(orderFile, MAX_MOD_ORDER_BYTES), StandardCharsets.UTF_8);
        List<String> order = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : orderText.lines().toList()) {
            String name = raw.trim();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (name.length() > MAX_MOD_NAME_CHARS
                    || name.equals(".")
                    || name.equals("..")
                    || name.indexOf('/') >= 0
                    || name.indexOf('\\') >= 0) {
                throw new IOException("Synthetic mod name is invalid: " + name);
            }
            if (!seen.add(name)) {
                throw new IOException("Synthetic mod order contains a duplicate: " + name);
            }
            order.add(name);
            if (order.size() > MAX_MODS) {
                throw new IOException("Synthetic profile exceeds " + MAX_MODS + " mods");
            }
        }
        if (order.isEmpty()) throw new IOException("Synthetic mod order is empty");
        return List.copyOf(order);
    }

    private static String logicalPath(Path modRoot, Path file) {
        Path relative = modRoot.relativize(file).normalize();
        String logical = relative.toString().replace('\\', '/');
        if (logical.isEmpty()
                || logical.length() > MAX_LOGICAL_PATH_CHARS
                || logical.equals("..")
                || logical.startsWith("../")
                || logical.contains("/../")) {
            throw new IllegalArgumentException("Synthetic resource path is invalid: " + file);
        }
        return logical;
    }

    private static boolean writePrepared(
            Path cacheRoot,
            String sourceSha256,
            SyntheticPreparedImageCache.PreparedImage image) {
        try {
            SyntheticPreparedImageCache.write(cacheRoot, sourceSha256, image);
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static HashedFile sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total = Math.addExact(total, read);
                if (total > MAX_WINNING_FILE_BYTES) {
                    throw new IOException("Synthetic winning file exceeds its byte limit: " + file);
                }
                digest.update(buffer, 0, read);
            }
        }
        return new HashedFile(HexFormat.of().formatHex(digest.digest()), total);
    }

    private static byte[] readBounded(Path file, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) {
                throw new IOException("Synthetic file exceeds its byte safety limit: " + file);
            }
            return bytes;
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void writeAtomic(Path destination, String content) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(
                absolute.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private record Provider(String modName, Path path) {
    }

    private record HashedFile(String sha256, long bytes) {
    }
}
