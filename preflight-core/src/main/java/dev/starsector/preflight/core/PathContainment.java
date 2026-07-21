package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Resolves existing paths while enforcing containment beneath a canonical directory. */
public final class PathContainment {
    private PathContainment() {
    }

    public static Path realDirectory(Path directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory is required");
        }
        Path real = directory.toAbsolutePath().normalize().toRealPath();
        if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a directory: " + directory);
        }
        return real;
    }

    public static Path existingInside(Path root, Path candidate) throws IOException {
        return existingInsideRealRoot(realDirectory(root), candidate);
    }

    /** Resolves a candidate beneath a root previously returned by {@link #realDirectory(Path)}. */
    public static Path existingInsideRealRoot(Path realRoot, Path candidate) throws IOException {
        if (realRoot == null) {
            throw new IllegalArgumentException("Real root is required");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("Candidate path is required");
        }
        Path normalizedRoot = realRoot.toAbsolutePath().normalize();
        Path realCandidate = candidate.toAbsolutePath().normalize().toRealPath();
        if (!realCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "Path escapes its root: " + candidate + " resolves to " + realCandidate
                            + " outside " + normalizedRoot);
        }
        return realCandidate;
    }
}
