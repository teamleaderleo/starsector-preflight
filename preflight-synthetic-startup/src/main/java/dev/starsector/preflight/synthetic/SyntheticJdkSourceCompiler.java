package dev.starsector.preflight.synthetic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Complete-map compiler seam for the deterministic generated source set. */
final class SyntheticJdkSourceCompiler {
    static final List<String> OPTIONS = List.of(
            "-g:none",
            "-proc:none",
            "-encoding",
            "UTF-8",
            "--release",
            "17");

    private static final int MAX_SOURCES = 10_000;
    private static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_TOTAL_SOURCE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_CLASS_BYTES = 4 * 1024 * 1024;
    private static final long MAX_TOTAL_CLASS_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DIAGNOSTIC_CHARS = 16 * 1024;

    private SyntheticJdkSourceCompiler() {
    }

    static Map<String, byte[]> compile(Map<String, byte[]> sources, AtomicInteger calls)
            throws IOException {
        if (calls != null) calls.incrementAndGet();
        if (sources == null || sources.isEmpty() || sources.size() > MAX_SOURCES) {
            throw new IOException("Synthetic Java source count is outside its limit");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("JDK compiler is unavailable");

        long sourceBytes = 0;
        List<JavaFileObject> units = new ArrayList<>(sources.size());
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(sources).entrySet()) {
            String className = className(entry.getKey());
            byte[] bytes = entry.getValue();
            if (bytes == null || bytes.length > MAX_SOURCE_BYTES) {
                throw new IOException("Synthetic Java source exceeds its byte limit: " + className);
            }
            sourceBytes = Math.addExact(sourceBytes, bytes.length);
            if (sourceBytes > MAX_TOTAL_SOURCE_BYTES) {
                throw new IOException("Synthetic Java source set exceeds its byte limit");
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))) {
                throw new IOException("Synthetic Java source is not canonical UTF-8: " + className);
            }
            units.add(new SourceObject(className, text));
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(
                diagnostics,
                Locale.ROOT,
                StandardCharsets.UTF_8);
             MemoryFileManager manager = new MemoryFileManager(standard)) {
            Boolean success = compiler.getTask(
                    null,
                    manager,
                    diagnostics,
                    OPTIONS,
                    null,
                    units).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IOException("Synthetic Java compilation failed: " + diagnostics(diagnostics));
            }
            return manager.snapshot();
        }
    }

    private static String className(String logicalPath) throws IOException {
        String normalized = logicalPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String file = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!file.matches("Source[0-9]{5}\\.java")) {
            throw new IOException("Unexpected synthetic Java source path: " + logicalPath);
        }
        return "synthetic.generated." + file.substring(0, file.length() - 5);
    }

    private static String diagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (text.length() >= MAX_DIAGNOSTIC_CHARS) break;
            if (!text.isEmpty()) text.append(" | ");
            text.append(diagnostic.getKind())
                    .append(':')
                    .append(diagnostic.getLineNumber())
                    .append(':')
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        if (text.length() > MAX_DIAGNOSTIC_CHARS) {
            return text.substring(0, MAX_DIAGNOSTIC_CHARS) + "...";
        }
        return text.toString();
    }

    private static final class SourceObject extends SimpleJavaFileObject {
        private final String source;

        private SourceObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class ClassObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private ClassObject(String className, Kind kind) {
            super(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public ByteArrayOutputStream openOutputStream() {
            return bytes;
        }

        private byte[] toByteArray() throws IOException {
            byte[] result = bytes.toByteArray();
            if (result.length == 0 || result.length > MAX_CLASS_BYTES) {
                throw new IOException("Generated class bytes are outside the per-class limit");
            }
            return result;
        }
    }

    private static final class MemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ClassObject> outputs = new LinkedHashMap<>();

        private MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                javax.tools.FileObject sibling) throws IOException {
            if (!className.matches("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*")) {
                throw new IOException("Invalid generated binary name: " + className);
            }
            if (outputs.containsKey(className)) {
                throw new IOException("Duplicate generated binary name: " + className);
            }
            ClassObject output = new ClassObject(className, kind);
            outputs.put(className, output);
            return output;
        }

        private Map<String, byte[]> snapshot() throws IOException {
            TreeMap<String, byte[]> sorted = new TreeMap<>();
            long total = 0;
            for (Map.Entry<String, ClassObject> entry : outputs.entrySet()) {
                byte[] bytes = entry.getValue().toByteArray();
                total = Math.addExact(total, bytes.length);
                if (total > MAX_TOTAL_CLASS_BYTES) {
                    throw new IOException("Generated class bundle exceeds its byte limit");
                }
                sorted.put(entry.getKey(), bytes);
            }
            if (sorted.isEmpty()) throw new IOException("Compiler returned no generated classes");
            return Collections.unmodifiableMap(sorted);
        }
    }
}
