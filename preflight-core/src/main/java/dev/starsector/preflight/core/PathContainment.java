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
        if (candidate == null) {
            throw new IllegalArgumentException("Candidate path is required");
        }
        Path realRoot = realDirectory(root);
        Path realCandidate = candidate.toAbsolutePath().normalize().toRealPath();
        if (!realCandidate.startsWith(realRoot)) {
            throw new IllegalArgumentException(
                    "Path escapes its root: " + candidate + " resolves to " + realCandidate
                            + " outside " + realRoot);
        }
        return realCandidate;
    }
}
