package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.GeneratedBytecodeBundle;
import dev.starsector.preflight.core.GeneratedBytecodeCache;
import dev.starsector.preflight.core.GeneratedBytecodeContext;
import dev.starsector.preflight.core.Hashes;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

/** Synthetic complete-source compilation backed by the generated-bytecode bundle cache. */
final class SyntheticGeneratedBytecodeWork {
    private static final int MAX_SOURCES = 10_000;
    private static final int MAX_CLASSES = 20_000;
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_CLASS_BYTES = 512L * 1024 * 1024;

    private SyntheticGeneratedBytecodeWork() {
    }

    record Result(
            long sourceCount,
            long compilerCalls,
            long cacheHits,
            long cacheMisses,
            long cacheCorruptFallbacks,
            long cacheErrors,
            long cacheWriteErrors,
            long classCount,
            long classBytes,
            String classMapSha256) {
    }

    static Result run(
            SyntheticExtendedResourceIndex index,
            Path cacheRoot,
            String providerDigest) throws IOException {
        List<Map.Entry<String, SyntheticExtendedResourceIndex.Provider>> sources = index.providers().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().endsWith(".java"))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        if (sources.isEmpty()) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, Hashes.sha256(new byte[0]));
        }
        if (sources.size() > MAX_SOURCES) throw new IOException("Synthetic Java source count exceeds limit");
        for (var source : sources) {
            if (source.getValue().kind() != SyntheticExtendedResourceIndex.Kind.LOOSE) {
                throw new IOException("Synthetic Java source must be loose: " + source.getKey());
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("System Java compiler is unavailable");
        String compilerIdentity = String.join("\n",
                "synthetic-jdk-compiler-v1",
                System.getProperty("java.version", ""),
                System.getProperty("java.runtime.version", ""),
                System.getProperty("java.vendor", ""),
                System.getProperty("java.vm.version", ""),
                compiler.getClass().getName(),
                compiler.getClass().getModule().getName(),
                compiler.getClass().getModule().getDescriptor() == null
                        ? ""
                        : compiler.getClass().getModule().getDescriptor().rawVersion().orElse(""));
        String sourceGraph = digestSources(sources);
        String requestedClass = requestedClass(sources.get(0).getKey());
        GeneratedBytecodeContext context = new GeneratedBytecodeContext(
                Hashes.sha256("synthetic-starsector-build-v1".getBytes(StandardCharsets.UTF_8)),
                Hashes.sha256(compilerIdentity.getBytes(StandardCharsets.UTF_8)),
                Hashes.sha256(("empty-user-classpath\n" + providerDigest).getBytes(StandardCharsets.UTF_8)),
                sourceGraph,
                Hashes.sha256("-g:none\n-encoding\nUTF-8\n-classpath\nEMPTY\n".getBytes(StandardCharsets.UTF_8)),
                Hashes.sha256("synthetic-parent-loader-v1".getBytes(StandardCharsets.UTF_8)),
                Hashes.sha256("synthetic-protection-domain-v1".getBytes(StandardCharsets.UTF_8)));

        GeneratedBytecodeCache.Lookup lookup = GeneratedBytecodeCache.lookup(
                cacheRoot,
                context.keySha256(),
                requestedClass);
        if (lookup.status() == GeneratedBytecodeCache.Status.HIT) {
            GeneratedBytecodeBundle bundle = lookup.bundle();
            return new Result(
                    sources.size(), 0, 1, 0, 0, 0, 0,
                    bundle.classCount(), bundle.totalBytecodeBytes(), digestClassMap(bundle.classes()));
        }

        long misses = lookup.status() == GeneratedBytecodeCache.Status.MISS ? 1 : 0;
        long corrupt = lookup.status() == GeneratedBytecodeCache.Status.CORRUPT ? 1 : 0;
        long errors = lookup.status() == GeneratedBytecodeCache.Status.ERROR ? 1 : 0;
        Path output = Files.createTempDirectory("preflight-synthetic-bytecode-");
        try {
            List<String> arguments = new ArrayList<>();
            arguments.add("-g:none");
            arguments.add("-encoding");
            arguments.add("UTF-8");
            arguments.add("-classpath");
            arguments.add("");
            arguments.add("-d");
            arguments.add(output.toString());
            for (var source : sources) arguments.add(index.loosePath(source.getKey()).toString());
            int exit = compiler.run(null, null, null, arguments.toArray(String[]::new));
            if (exit != 0) throw new IOException("Synthetic Java compilation failed with exit code " + exit);
            LinkedHashMap<String, byte[]> classes = readClasses(output);
            GeneratedBytecodeBundle bundle = new GeneratedBytecodeBundle(
                    context.keySha256(),
                    requestedClass,
                    classes);
            long writeErrors = 0;
            try {
                GeneratedBytecodeCache.write(cacheRoot, bundle);
            } catch (IOException | RuntimeException error) {
                writeErrors++;
            }
            return new Result(
                    sources.size(), 1, 0, misses, corrupt, errors, writeErrors,
                    bundle.classCount(), bundle.totalBytecodeBytes(), digestClassMap(bundle.classes()));
        } finally {
            deleteTree(output);
        }
    }

    private static String digestSources(
            List<Map.Entry<String, SyntheticExtendedResourceIndex.Provider>> sources) {
        java.security.MessageDigest digest = digest();
        for (var source : sources) {
            update(digest, source.getKey());
            update(digest, source.getValue().sha256());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String requestedClass(String logicalPath) throws IOException {
        String name = Path.of(logicalPath).getFileName().toString();
        if (!name.endsWith(".java") || name.length() <= 5) {
            throw new IOException("Invalid synthetic Java source path");
        }
        return "synthetic.generated." + name.substring(0, name.length() - 5);
    }

    private static LinkedHashMap<String, byte[]> readClasses(Path root) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .limit(MAX_CLASSES + 1L)
                    .toList();
        }
        if (files.isEmpty() || files.size() > MAX_CLASSES) {
            throw new IOException("Synthetic generated class count is invalid");
        }
        LinkedHashMap<String, byte[]> classes = new LinkedHashMap<>();
        long total = 0;
        for (Path file : files) {
            if (Files.isSymbolicLink(file)) throw new IOException("Synthetic generated class is symbolic");
            long size = Files.size(file);
            if (size < 10 || size > MAX_CLASS_BYTES) {
                throw new IOException("Synthetic generated class size is invalid");
            }
            total = Math.addExact(total, size);
            if (total > MAX_TOTAL_CLASS_BYTES) {
                throw new IOException("Synthetic generated bytecode exceeds total limit");
            }
            String relative = root.relativize(file).toString().replace('\\', '/');
            String binary = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
            classes.put(binary, Files.readAllBytes(file));
        }
        return classes;
    }

    private static String digestClassMap(Map<String, byte[]> classes) {
        java.security.MessageDigest digest = digest();
        classes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            update(digest, entry.getKey());
            digest.update(Hashes.sha256Bytes(entry.getValue()));
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(entry.getValue().length).array());
        });
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static java.security.MessageDigest digest() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void update(java.security.MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void deleteTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
