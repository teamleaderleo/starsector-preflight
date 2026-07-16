package dev.starsector.preflight.synthetic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
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
    private static final int MAX_CLASSES = 50_000;
    private static final int MAX_CLASS_NAME_CHARS = 4_096;
    private static final int MAX_CLASS_BYTES = 4 * 1024 * 1024;
    private static final long MAX_TOTAL_CLASS_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DIAGNOSTICS = 256;
    static final int MAX_DIAGNOSTIC_CHARS = 16 * 1024;

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

        BoundedDiagnostics diagnostics = new BoundedDiagnostics();
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
                throw new IOException("Synthetic Java compilation failed: " + diagnostics.text());
            }
            return manager.snapshot();
        }
    }

    private static String className(String logicalPath) throws IOException {
        if (logicalPath == null || logicalPath.length() > MAX_CLASS_NAME_CHARS) {
            throw new IOException("Synthetic Java source path is outside its character limit");
        }
        String normalized = logicalPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String file = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!file.matches("Source[0-9]{5}\\.java")) {
            throw new IOException("Unexpected synthetic Java source path: " + logicalPath);
        }
        return "synthetic.generated." + file.substring(0, file.length() - 5);
    }

    private static final class BoundedDiagnostics implements DiagnosticListener<JavaFileObject> {
        private static final int CONTENT_LIMIT = MAX_DIAGNOSTIC_CHARS - 3;

        private final StringBuilder text = new StringBuilder(Math.min(CONTENT_LIMIT, 1024));
        private int count;
        private boolean truncated;

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (truncated) return;
            if (count >= MAX_DIAGNOSTICS) {
                truncated = true;
                return;
            }
            count++;
            if (!text.isEmpty()) append(" | ");
            if (diagnostic == null) {
                append("UNKNOWN");
                return;
            }
            append(diagnostic.getKind().name());
            append(":");
            append(Long.toString(diagnostic.getLineNumber()));
            append(":");
            if (!truncated) append(diagnostic.getMessage(Locale.ROOT));
        }

        private void append(String value) {
            if (truncated) return;
            String actual = value == null ? "" : value;
            int remaining = CONTENT_LIMIT - text.length();
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            if (actual.length() <= remaining) {
                text.append(actual);
            } else {
                text.append(actual, 0, remaining);
                truncated = true;
            }
        }

        private String text() {
            return truncated ? text + "..." : text.toString();
        }
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
        private final BoundedOutput bytes;
        private boolean opened;

        private ClassObject(
                String className,
                Kind kind,
                OutputBudget totalBudget) {
            super(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind);
            this.bytes = new BoundedOutput(MAX_CLASS_BYTES, totalBudget);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            if (opened) throw new IOException("Generated class output was opened more than once");
            opened = true;
            return bytes;
        }

        private byte[] toByteArray() throws IOException {
            if (!opened) throw new IOException("Generated class output was never opened");
            byte[] result = bytes.toByteArray();
            if (result.length == 0) {
                throw new IOException("Generated class output is empty");
            }
            return result;
        }
    }

    private static final class MemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ClassObject> outputs = new LinkedHashMap<>();
        private final OutputBudget totalBudget = new OutputBudget(MAX_TOTAL_CLASS_BYTES);

        private MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                javax.tools.FileObject sibling) throws IOException {
            if (kind != JavaFileObject.Kind.CLASS) {
                throw new IOException("Unexpected generated output kind: " + kind);
            }
            if (className == null
                    || className.length() > MAX_CLASS_NAME_CHARS
                    || !className.matches("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*")) {
                throw new IOException("Invalid generated binary name: " + className);
            }
            if (outputs.size() >= MAX_CLASSES) {
                throw new IOException("Generated class count exceeds its limit");
            }
            if (outputs.containsKey(className)) {
                throw new IOException("Duplicate generated binary name: " + className);
            }
            ClassObject output = new ClassObject(className, kind, totalBudget);
            outputs.put(className, output);
            return output;
        }

        private Map<String, byte[]> snapshot() throws IOException {
            TreeMap<String, byte[]> sorted = new TreeMap<>();
            long total = 0;
            for (Map.Entry<String, ClassObject> entry : outputs.entrySet()) {
                byte[] bytes = entry.getValue().toByteArray();
                total = Math.addExact(total, bytes.length);
                sorted.put(entry.getKey(), bytes);
            }
            if (sorted.isEmpty()) throw new IOException("Compiler returned no generated classes");
            if (total != totalBudget.totalBytes()) {
                throw new IOException("Generated class accounting mismatch");
            }
            return Collections.unmodifiableMap(sorted);
        }
    }

    static final class OutputBudget {
        private final long maxBytes;
        private long totalBytes;

        OutputBudget(long maxBytes) {
            if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must be nonnegative");
            this.maxBytes = maxBytes;
        }

        synchronized void reserve(int bytes) throws IOException {
            if (bytes < 0 || bytes > maxBytes - totalBytes) {
                throw new IOException("Generated class bundle exceeds its byte limit");
            }
            totalBytes += bytes;
        }

        synchronized long totalBytes() {
            return totalBytes;
        }
    }

    static final class BoundedOutput extends OutputStream {
        private final int maxBytes;
        private final OutputBudget totalBudget;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean closed;

        BoundedOutput(int maxBytes, OutputBudget totalBudget) {
            if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must be nonnegative");
            this.maxBytes = maxBytes;
            this.totalBudget = Objects.requireNonNull(totalBudget, "totalBudget");
        }

        @Override
        public void write(int value) throws IOException {
            ensureOpen();
            reserve(1);
            bytes.write(value);
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, source.length);
            ensureOpen();
            reserve(length);
            bytes.write(source, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }

        private void reserve(int length) throws IOException {
            if (length > maxBytes - bytes.size()) {
                throw new IOException("Generated class bytes exceed the per-class limit");
            }
            totalBudget.reserve(length);
        }

        private void ensureOpen() throws IOException {
            if (closed) throw new IOException("Generated class output is closed");
        }
    }
}
