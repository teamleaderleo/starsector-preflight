package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Generic fail-open wrapper for a complete {@code generateBytecodes(name)} result. */
public final class GeneratedBytecodeCacheWrapper {
    private GeneratedBytecodeCacheWrapper() {
    }

    public static <E extends Throwable> Result generate(
            Path cacheRoot,
            GeneratedBytecodeContext context,
            String requestedClassName,
            Generator<E> original) throws E {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(requestedClassName, "requestedClassName");
        Objects.requireNonNull(original, "original");

        String contextKey = context.keySha256();
        GeneratedBytecodeCache.Lookup lookup =
                GeneratedBytecodeCache.lookup(cacheRoot, contextKey, requestedClassName);
        if (lookup.status() == GeneratedBytecodeCache.Status.HIT) {
            return new Result(
                    Source.CACHE_HIT,
                    mutableClasses(lookup.bundle()),
                    lookup.status(),
                    true,
                    "");
        }

        // Deliberately outside every cache catch boundary: original exceptions retain type and identity.
        Map<String, byte[]> generated = original.generate(requestedClassName);

        final GeneratedBytecodeBundle bundle;
        try {
            bundle = new GeneratedBytecodeBundle(contextKey, requestedClassName, generated);
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            return new Result(
                    Source.ORIGINAL_INVALID_OUTPUT,
                    generated,
                    lookup.status(),
                    false,
                    message(error));
        }

        try {
            GeneratedBytecodeCache.write(cacheRoot, bundle);
            return new Result(
                    Source.ORIGINAL_STORED,
                    generated,
                    lookup.status(),
                    true,
                    lookup.detail());
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (IOException | RuntimeException error) {
            return new Result(
                    Source.ORIGINAL_STORE_FAILED,
                    generated,
                    lookup.status(),
                    false,
                    message(error));
        }
    }

    private static Map<String, byte[]> mutableClasses(GeneratedBytecodeBundle bundle) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        bundle.classes().forEach(result::put);
        return result;
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    @FunctionalInterface
    public interface Generator<E extends Throwable> {
        Map<String, byte[]> generate(String requestedClassName) throws E;
    }

    public enum Source {
        CACHE_HIT,
        ORIGINAL_STORED,
        ORIGINAL_STORE_FAILED,
        ORIGINAL_INVALID_OUTPUT
    }

    public record Result(
            Source source,
            Map<String, byte[]> classes,
            GeneratedBytecodeCache.Status lookupStatus,
            boolean cacheUsable,
            String detail) {
        public Result {
            source = Objects.requireNonNull(source, "source");
            lookupStatus = Objects.requireNonNull(lookupStatus, "lookupStatus");
            detail = detail == null ? "" : detail;
            if (source == Source.CACHE_HIT) {
                if (lookupStatus != GeneratedBytecodeCache.Status.HIT || classes == null) {
                    throw new IllegalArgumentException("A cache-hit result requires a HIT lookup and class map");
                }
            } else if (lookupStatus == GeneratedBytecodeCache.Status.HIT) {
                throw new IllegalArgumentException("An original result may not follow a HIT lookup");
            }
        }
    }
}
