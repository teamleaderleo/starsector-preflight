package dev.starsector.preflight.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/** Content fingerprints for explicitly constrained local code-source archives. */
final class SourceArchiveHashes {
    private static final long MAX_ARCHIVE_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int BUFFER_BYTES = 1024 * 1024;
    private static final ConcurrentHashMap<Key, Result> CACHE = new ConcurrentHashMap<>();

    private SourceArchiveHashes() {
    }

    static Result notRequested() {
        return new Result("", "");
    }

    static Result sha256(Path source) {
        if (source == null) {
            return Result.failure("code source is not a local file");
        }
        Path real;
        BasicFileAttributes attributes;
        try {
            real = source.toRealPath();
            attributes = Files.readAttributes(real, BasicFileAttributes.class);
        } catch (IOException error) {
            return Result.failure("could not inspect code source: " + message(error));
        }
        if (!attributes.isRegularFile()) {
            return Result.failure("code source is not a regular archive file");
        }
        if (attributes.size() > MAX_ARCHIVE_BYTES) {
            return Result.failure("code source exceeds " + MAX_ARCHIVE_BYTES + " bytes");
        }
        Key key = new Key(real, attributes.size(), Math.max(0, attributes.lastModifiedTime().toMillis()));
        return CACHE.computeIfAbsent(key, ignored -> hash(real));
    }

    private static Result hash(Path source) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            return Result.failure("SHA-256 is unavailable");
        }
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream input = Files.newInputStream(source)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return Result.success(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException error) {
            return Result.failure("could not hash code source: " + message(error));
        }
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    record Result(String sha256, String problem) {
        private Result {
            sha256 = sha256 == null ? "" : sha256;
            problem = problem == null ? "" : problem;
        }

        static Result success(String sha256) {
            return new Result(sha256, "");
        }

        static Result failure(String problem) {
            return new Result("", problem);
        }

        boolean successful() {
            return !sha256.isBlank();
        }
    }

    private record Key(Path path, long bytes, long modifiedMillis) {
    }
}
