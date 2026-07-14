package dev.starsector.preflight.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Computes a deterministic, content-aware fingerprint for a file or directory.
 *
 * <p>The directory fingerprint includes normalized relative paths, file sizes,
 * and file contents. Modification timestamps are deliberately excluded so that
 * copying an unchanged mod does not invalidate its cache key.</p>
 */
public final class ContentFingerprint {
    private static final byte[] ENTRY_SEPARATOR = new byte[] {0};

    private ContentFingerprint() {
    }

    public static String compute(Path input) throws IOException {
        Path absolute = input.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new IOException("Path does not exist: " + absolute);
        }

        MessageDigest digest = sha256();
        if (Files.isRegularFile(absolute)) {
            updateEntry(digest, absolute.getFileName().toString(), absolute);
        } else if (Files.isDirectory(absolute)) {
            List<Path> files;
            try (var stream = Files.walk(absolute)) {
                files = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> normalizeRelativePath(absolute, path)))
                        .toList();
            }

            for (Path file : files) {
                updateEntry(digest, normalizeRelativePath(absolute, file), file);
            }
        } else {
            throw new IOException("Unsupported path type: " + absolute);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static String normalizeRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static void updateEntry(MessageDigest digest, String logicalPath, Path file) throws IOException {
        digest.update(logicalPath.getBytes(StandardCharsets.UTF_8));
        digest.update(ENTRY_SEPARATOR);
        digest.update(Long.toString(Files.size(file)).getBytes(StandardCharsets.US_ASCII));
        digest.update(ENTRY_SEPARATOR);

        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        digest.update(ENTRY_SEPARATOR);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
