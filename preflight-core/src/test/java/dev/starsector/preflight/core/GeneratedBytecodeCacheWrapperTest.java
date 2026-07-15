package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedBytecodeCacheWrapperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missReturnsOriginalMapThenHitReturnsMutableCompleteCopy() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        GeneratedBytecodeContext context = context("0");
        LinkedHashMap<String, byte[]> generated = completeMap();
        AtomicInteger calls = new AtomicInteger();

        GeneratedBytecodeCacheWrapper.Result miss = GeneratedBytecodeCacheWrapper.generate(
                cache,
                context,
                Alpha.class.getName(),
                requested -> {
                    calls.incrementAndGet();
                    return generated;
                });
        assertEquals(GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORED, miss.source());
        assertEquals(GeneratedBytecodeCache.Status.MISS, miss.lookupStatus());
        assertSame(generated, miss.classes());
        assertEquals(1, calls.get());
        assertTrue(miss.cacheUsable());

        GeneratedBytecodeCacheWrapper.Result hit = GeneratedBytecodeCacheWrapper.generate(
                cache,
                context,
                Alpha.class.getName(),
                requested -> {
                    calls.incrementAndGet();
                    throw new AssertionError("generator ran on an exact hit");
                });
        assertEquals(GeneratedBytecodeCacheWrapper.Source.CACHE_HIT, hit.source());
        assertEquals(GeneratedBytecodeCache.Status.HIT, hit.lookupStatus());
        assertEquals(1, calls.get());
        assertEquals(generated.keySet(), hit.classes().keySet());
        assertArrayEquals(generated.get(Alpha.class.getName()), hit.classes().get(Alpha.class.getName()));

        hit.classes().put(Extra.class.getName(), classBytes(Extra.class));
        hit.classes().get(Alpha.class.getName())[4] ^= 1;
        GeneratedBytecodeCacheWrapper.Result secondHit = GeneratedBytecodeCacheWrapper.generate(
                cache,
                context,
                Alpha.class.getName(),
                requested -> {
                    throw new AssertionError("generator ran after cached map mutation");
                });
        assertFalse(secondHit.classes().containsKey(Extra.class.getName()));
        assertArrayEquals(generated.get(Alpha.class.getName()), secondHit.classes().get(Alpha.class.getName()));
    }

    @Test
    void corruptEntryRunsOriginalOnceAndRepairsCompleteBundle() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        GeneratedBytecodeContext context = context("1");
        LinkedHashMap<String, byte[]> generated = completeMap();
        GeneratedBytecodeCacheWrapper.generate(
                cache, context, Alpha.class.getName(), requested -> generated);
        Path path = GeneratedBytecodeCache.bundlePath(cache, context.keySha256(), Alpha.class.getName());
        Files.write(path, new byte[] {1, 2, 3});

        AtomicInteger calls = new AtomicInteger();
        GeneratedBytecodeCacheWrapper.Result repaired = GeneratedBytecodeCacheWrapper.generate(
                cache,
                context,
                Alpha.class.getName(),
                requested -> {
                    calls.incrementAndGet();
                    return generated;
                });
        assertEquals(GeneratedBytecodeCache.Status.CORRUPT, repaired.lookupStatus());
        assertEquals(GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORED, repaired.source());
        assertEquals(1, calls.get());

        GeneratedBytecodeCacheWrapper.Result hit = GeneratedBytecodeCacheWrapper.generate(
                cache,
                context,
                Alpha.class.getName(),
                requested -> {
                    throw new AssertionError("generator ran after repair");
                });
        assertEquals(GeneratedBytecodeCacheWrapper.Source.CACHE_HIT, hit.source());
        assertEquals(generated.keySet(), hit.classes().keySet());
    }

    @Test
    void invalidOutputAndStoreFailureReturnOriginalResult() throws Exception {
        GeneratedBytecodeContext context = context("2");
        LinkedHashMap<String, byte[]> wrong = new LinkedHashMap<>();
        wrong.put(Alpha.class.getName(), classBytes(Extra.class));

        GeneratedBytecodeCacheWrapper.Result invalid = GeneratedBytecodeCacheWrapper.generate(
                temporaryDirectory.resolve("invalid-cache"),
                context,
                Alpha.class.getName(),
                requested -> wrong);
        assertEquals(GeneratedBytecodeCacheWrapper.Source.ORIGINAL_INVALID_OUTPUT, invalid.source());
        assertSame(wrong, invalid.classes());
        assertFalse(invalid.cacheUsable());
        assertTrue(invalid.detail().contains("differs from classfile identity"), invalid.detail());

        GeneratedBytecodeCacheWrapper.Result nullOutput = GeneratedBytecodeCacheWrapper.generate(
                temporaryDirectory.resolve("null-cache"),
                context,
                Alpha.class.getName(),
                requested -> null);
        assertEquals(GeneratedBytecodeCacheWrapper.Source.ORIGINAL_INVALID_OUTPUT, nullOutput.source());
        assertNull(nullOutput.classes());

        Path blockedRoot = temporaryDirectory.resolve("cache-root-file");
        Files.writeString(blockedRoot, "blocked");
        LinkedHashMap<String, byte[]> generated = completeMap();
        GeneratedBytecodeCacheWrapper.Result writeFailure = GeneratedBytecodeCacheWrapper.generate(
                blockedRoot,
                context,
                Alpha.class.getName(),
                requested -> generated);
        assertEquals(GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORE_FAILED, writeFailure.source());
        assertSame(generated, writeFailure.classes());
        assertFalse(writeFailure.cacheUsable());
    }

    @Test
    void originalCheckedExceptionRetainsExactIdentity() {
        GeneratedBytecodeContext context = context("3");
        SyntheticCompilationException expected = new SyntheticCompilationException("synthetic failure");

        SyntheticCompilationException actual = assertThrows(
                SyntheticCompilationException.class,
                () -> GeneratedBytecodeCacheWrapper.generate(
                        temporaryDirectory.resolve("cache"),
                        context,
                        Alpha.class.getName(),
                        requested -> {
                            throw expected;
                        }));
        assertSame(expected, actual);
    }

    private static GeneratedBytecodeContext context(String digit) {
        return new GeneratedBytecodeContext(
                digit.repeat(64),
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "5".repeat(64),
                "6".repeat(64));
    }

    private static LinkedHashMap<String, byte[]> completeMap() throws IOException {
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(Alpha.Inner.class.getName(), classBytes(Alpha.Inner.class));
        classes.put(Alpha.class.getName(), classBytes(Alpha.class));
        return classes;
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test class resource " + resource);
            return input.readAllBytes();
        }
    }

    static final class Alpha {
        static final class Inner {
        }
    }

    static final class Extra {
    }

    static final class SyntheticCompilationException extends Exception {
        SyntheticCompilationException(String message) {
            super(message);
        }
    }
}
