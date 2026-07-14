package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates every prepared texture referenced by a profile manifest. */
public final class TextureManifestValidator {
    public static final int DEFAULT_PROBLEM_LIMIT = 100;

    private TextureManifestValidator() {
    }

    public static Result validate(Path cacheDirectory, TextureManifest manifest) {
        return validate(cacheDirectory, manifest, DEFAULT_PROBLEM_LIMIT);
    }

    public static Result validate(Path cacheDirectory, TextureManifest manifest, int problemLimit) {
        if (problemLimit < 1) {
            throw new IllegalArgumentException("problemLimit must be positive");
        }
        Path root = cacheDirectory.toAbsolutePath().normalize();
        List<Problem> problems = new ArrayList<>();
        long checked = 0;
        long invalid = 0;
        for (Map.Entry<String, TextureManifest.Entry> item : manifest.entries().entrySet()) {
            checked++;
            TextureManifest.Entry expected = item.getValue();
            Path blob = root.resolve(expected.blobRelativePath()).normalize();
            if (!blob.startsWith(root)) {
                invalid++;
                add(problems, problemLimit, new Problem(
                        Kind.INVALID_PATH,
                        item.getKey(),
                        expected.blobRelativePath(),
                        "inside " + root,
                        blob.toString()));
                continue;
            }

            PreparedTexture actual;
            try {
                actual = PreparedTextureIO.read(blob);
            } catch (NoSuchFileException error) {
                invalid++;
                add(problems, problemLimit, new Problem(
                        Kind.BLOB_MISSING,
                        item.getKey(),
                        expected.blobRelativePath(),
                        "prepared texture blob",
                        "missing"));
                continue;
            } catch (IOException error) {
                invalid++;
                add(problems, problemLimit, new Problem(
                        Files.exists(blob) ? Kind.BLOB_INVALID : Kind.BLOB_MISSING,
                        item.getKey(),
                        expected.blobRelativePath(),
                        "valid prepared texture blob",
                        error.getMessage()));
                continue;
            }

            String mismatch = mismatch(expected, actual);
            if (mismatch != null) {
                invalid++;
                add(problems, problemLimit, new Problem(
                        Kind.METADATA_MISMATCH,
                        item.getKey(),
                        expected.blobRelativePath(),
                        "manifest metadata",
                        mismatch));
            }
        }
        return new Result(
                invalid == 0,
                checked,
                invalid,
                List.copyOf(problems),
                invalid > problems.size());
    }

    private static String mismatch(TextureManifest.Entry expected, PreparedTexture actual) {
        if (!expected.sourceSha256().equals(actual.sourceSha256())) {
            return "source SHA-256 differs";
        }
        if (expected.transformation() != actual.transformation()) {
            return "transformation differs";
        }
        if (expected.width() != actual.uploadWidth() || expected.height() != actual.uploadHeight()) {
            return "upload dimensions differ";
        }
        if (expected.channels() != actual.channels()) {
            return "channel count differs";
        }
        if (expected.pixelBytes() != actual.pixelBytes()) {
            return "pixel length differs";
        }
        return null;
    }

    private static void add(List<Problem> problems, int limit, Problem problem) {
        if (problems.size() < limit) {
            problems.add(problem);
        }
    }

    public enum Kind {
        INVALID_PATH,
        BLOB_MISSING,
        BLOB_INVALID,
        METADATA_MISMATCH
    }

    public record Problem(
            Kind kind,
            String logicalPath,
            String blobRelativePath,
            String expected,
            String actual) {
    }

    public record Result(
            boolean valid,
            long checkedEntries,
            long invalidEntries,
            List<Problem> problems,
            boolean truncated) {
    }
}
