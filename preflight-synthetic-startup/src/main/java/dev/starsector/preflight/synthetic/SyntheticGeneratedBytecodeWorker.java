package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.GeneratedBytecodeCacheWrapper;
import dev.starsector.preflight.core.GeneratedBytecodeContext;
import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Isolated synthetic complete-map compiler used by cross-process wrapper tests. */
public final class SyntheticGeneratedBytecodeWorker {
    static final String REQUESTED_CLASS = "synthetic.generated.Fixture";
    private static final String SOURCE = """
            package synthetic.generated;
            public class Fixture {
                public static class Inner { public int value() { return 7; } }
                public Runnable anonymous() {
                    return new Runnable() { public void run() { new Inner().value(); } };
                }
            }
            """;

    private SyntheticGeneratedBytecodeWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: SyntheticGeneratedBytecodeWorker <cache-root> <report.json>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(Path cacheRoot, Path reportPath) throws Exception {
        AtomicInteger generationCalls = new AtomicInteger();
        GeneratedBytecodeContext context = context();
        GeneratedBytecodeCacheWrapper.Result result = GeneratedBytecodeCacheWrapper.generate(
                cacheRoot,
                context,
                REQUESTED_CLASS,
                requested -> {
                    generationCalls.incrementAndGet();
                    return compileCompleteMap();
                });

        Map<String, byte[]> classes = result.classes();
        if (classes == null) throw new IOException("Synthetic compiler returned no class map");
        if (!classes.keySet().containsAll(List.of(
                REQUESTED_CLASS,
                REQUESTED_CLASS + "$Inner",
                REQUESTED_CLASS + "$1"))) {
            throw new IOException("Synthetic compiler returned an incomplete class map: " + classes.keySet());
        }
        MapClassLoader loader = new MapClassLoader(classes);
        Class<?> fixtureClass = loader.loadClass(REQUESTED_CLASS);
        Object fixture = fixtureClass.getConstructor().newInstance();
        Runnable runnable = (Runnable) fixtureClass.getMethod("anonymous").invoke(fixture);
        runnable.run();

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-bytecode-wrapper-v1");
        report.put("processId", ProcessHandle.current().pid());
        report.put("source", result.source());
        report.put("lookupStatus", result.lookupStatus());
        report.put("generationCalls", generationCalls.get());
        report.put("classCount", classes.size());
        report.put("classNames", classes.keySet().stream().sorted().toList());
        report.put("completeMapSha256", completeMapSha256(classes));
        report.put("contextKeySha256", context.keySha256());
        report.put("cacheUsable", result.cacheUsable());
        report.put("detail", result.detail());
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, Json.object(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    static GeneratedBytecodeContext context() {
        return new GeneratedBytecodeContext(
                hash("synthetic-starsector-build-v1"),
                hash("synthetic-javac:" + System.getProperty("java.version")),
                hash("synthetic-empty-classpath-v1"),
                Hashes.sha256(SOURCE.getBytes(StandardCharsets.UTF_8)),
                hash("-g:none|-proc:none"),
                hash("synthetic-parent:" + SyntheticGeneratedBytecodeWorker.class.getClassLoader().getClass().getName()),
                hash("synthetic-default-protection-domain-v1"));
    }

    private static Map<String, byte[]> compileCompleteMap() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("Synthetic bytecode worker requires a JDK compiler");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
             MemoryFileManager memory = new MemoryFileManager(standard)) {
            JavaFileObject source = new SourceFile(REQUESTED_CLASS, SOURCE);
            Boolean success = compiler.getTask(
                    null,
                    memory,
                    diagnostics,
                    List.of("-g:none", "-proc:none"),
                    null,
                    List.of(source)).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IOException("Synthetic compilation failed: " + diagnostics.getDiagnostics());
            }
            return memory.classBytes();
        }
    }

    private static String completeMapSha256(Map<String, byte[]> classes) {
        MessageDigest digest = sha256Digest();
        TreeMap<String, byte[]> ordered = new TreeMap<>(classes);
        ordered.forEach((name, bytes) -> {
            updateLength(digest, name.getBytes(StandardCharsets.UTF_8));
            updateLength(digest, bytes);
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateLength(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hash(String value) {
        return Hashes.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class ClassOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private ClassOutput(String className, Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        private byte[] bytes() {
            return bytes.toByteArray();
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ClassOutput> outputs = new TreeMap<>();

        private MemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) {
            ClassOutput output = new ClassOutput(className, kind);
            outputs.put(className, output);
            return output;
        }

        private Map<String, byte[]> classBytes() {
            LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
            outputs.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().bytes()));
            return result;
        }
    }

    private static final class MapClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        private MapClassLoader(Map<String, byte[]> classes) {
            super(SyntheticGeneratedBytecodeWorker.class.getClassLoader());
            this.classes = new TreeMap<>(classes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
