package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates that an index still describes the files currently on disk. */
public final class ResourceIndexValidator {
    public static final int DEFAULT_PROBLEM_LIMIT = 100;

    private ResourceIndexValidator() {
    }

    public static Result validate(ResourceIndex index) {
        return validate(index, DEFAULT_PROBLEM_LIMIT);
    }

    public static Result validate(ResourceIndex index, int problemLimit) {
        if (problemLimit < 1) {
            throw new IllegalArgumentException("problemLimit must be positive");
        }
        List<Problem> problems = new ArrayList<>();
        boolean[] rootAvailable = new boolean[index.roots().size()];
        for (int i = 0; i < index.roots().size(); i++) {
            ResourceIndex.Root root = index.roots().get(i);
            try {
                PathContainment.realDirectory(root.path());
                rootAvailable[i] = true;
            } catch (IOException | IllegalArgumentException error) {
                add(problems, problemLimit, new Problem(
                        Kind.ROOT_MISSING,
                        root.id(),
                        null,
                        null,
                        root.path().toString(),
                        message(error)));
            }
        }

        long checkedProviders = 0;
        long invalidProviders = 0;
        for (Map.Entry<String, List<ResourceIndex.Provider>> entry : index.entries().entrySet()) {
            for (ResourceIndex.Provider provider : entry.getValue()) {
                checkedProviders++;
                ResourceIndex.Root root = index.roots().get(provider.rootIndex());
                if (!rootAvailable[provider.rootIndex()]) {
                    invalidProviders++;
                    continue;
                }

                Path file;
                try {
                    file = index.resolveExisting(provider);
                } catch (IllegalArgumentException error) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.INVALID_PATH,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            null,
                            error.getMessage()));
                    continue;
                } catch (NoSuchFileException error) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.FILE_MISSING,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            "regular file",
                            "missing"));
                    continue;
                } catch (IOException error) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.FILE_UNREADABLE,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            null,
                            message(error)));
                    continue;
                }

                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(file, BasicFileAttributes.class);
                } catch (NoSuchFileException error) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.FILE_MISSING,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            "regular file",
                            "missing"));
                    continue;
                } catch (IOException error) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.FILE_UNREADABLE,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            null,
                            message(error)));
                    continue;
                }
                if (!attributes.isRegularFile()) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.FILE_MISSING,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            "regular file",
                            attributes.isDirectory() ? "directory" : "non-regular file"));
                    continue;
                }
                if (attributes.size() != provider.size()) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.SIZE_CHANGED,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            Long.toString(provider.size()),
                            Long.toString(attributes.size())));
                    continue;
                }
                long modified = Math.max(0, attributes.lastModifiedTime().toMillis());
                if (modified != provider.modifiedMillis()) {
                    invalidProviders++;
                    add(problems, problemLimit, new Problem(
                            Kind.MODIFIED_CHANGED,
                            root.id(),
                            entry.getKey(),
                            provider.relativePath(),
                            Long.toString(provider.modifiedMillis()),
                            Long.toString(modified)));
                }
            }
        }
        return new Result(
                problems.isEmpty() && invalidProviders == 0,
                checkedProviders,
                invalidProviders,
                List.copyOf(problems),
                invalidProviders > problems.size());
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static void add(List<Problem> problems, int limit, Problem problem) {
        if (problems.size() < limit) {
            problems.add(problem);
        }
    }

    public enum Kind {
        ROOT_MISSING,
        FILE_MISSING,
        FILE_UNREADABLE,
        INVALID_PATH,
        SIZE_CHANGED,
        MODIFIED_CHANGED
    }

    public record Problem(
            Kind kind,
            String rootId,
            String logicalPath,
            String relativePath,
            String expected,
            String actual) {
    }

    public record Result(
            boolean valid,
            long checkedProviders,
            long invalidProviders,
            List<Problem> problems,
            boolean truncated) {
    }
}
