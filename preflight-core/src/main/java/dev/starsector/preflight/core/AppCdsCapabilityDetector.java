package dev.starsector.preflight.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Proves that one exact Java executable can create and consume a dynamic AppCDS archive.
 * Any failure returns a result that contributes no launch arguments.
 */
public final class AppCdsCapabilityDetector {
    public static final String XSHARE_ON = "-Xshare:on";
    public static final String ARCHIVE_AT_EXIT_PREFIX = "-XX:ArchiveClassesAtExit=";
    public static final String SHARED_ARCHIVE_PREFIX = "-XX:SharedArchiveFile=";

    private static final int MAX_PROCESS_OUTPUT_BYTES = 64 * 1024;
    private static final int MAX_DETAIL_CHARS = 4 * 1024;
    private static final int MAX_CLASS_BYTES = 1024 * 1024;
    private static final long MAX_JAVA_EXECUTABLE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L;
    private static final Duration PROCESS_SHUTDOWN_GRACE = Duration.ofSeconds(2);

    private AppCdsCapabilityDetector() {
    }

    public enum Status {
        SUPPORTED,
        UNSUPPORTED,
        TIMED_OUT,
        ERROR
    }

    /** Immutable detector output. Instances can only be created by the detector. */
    public static final class Result {
        private final Status status;
        private final JavaIdentity javaIdentity;
        private final String detail;
        private final int generationExitCode;
        private final int consumptionExitCode;
        private final boolean outputTruncated;
        private final long proofArchiveBytes;
        private final String proofArchiveSha256;

        private Result(
                Status status,
                JavaIdentity javaIdentity,
                String detail,
                int generationExitCode,
                int consumptionExitCode,
                boolean outputTruncated,
                long proofArchiveBytes,
                String proofArchiveSha256) {
            this.status = Objects.requireNonNull(status, "status");
            if (status == Status.SUPPORTED && javaIdentity == null) {
                throw new IllegalArgumentException("A supported result requires an exact Java identity");
            }
            if (proofArchiveBytes < 0) {
                throw new IllegalArgumentException("proofArchiveBytes must be non-negative");
            }
            this.javaIdentity = javaIdentity;
            this.detail = boundedDetail(detail == null ? "" : detail);
            this.generationExitCode = generationExitCode;
            this.consumptionExitCode = consumptionExitCode;
            this.outputTruncated = outputTruncated;
            this.proofArchiveBytes = proofArchiveBytes;
            this.proofArchiveSha256 = proofArchiveSha256 == null ? "" : proofArchiveSha256;
        }

        public Status status() {
            return status;
        }

        public Path javaExecutable() {
            return javaIdentity == null ? null : javaIdentity.path();
        }

        public long javaExecutableBytes() {
            return javaIdentity == null ? 0 : javaIdentity.bytes();
        }

        public long javaExecutableModifiedMillis() {
            return javaIdentity == null ? 0 : javaIdentity.modifiedMillis();
        }

        public String javaExecutableSha256() {
            return javaIdentity == null ? "" : javaIdentity.sha256();
        }

        public String detail() {
            return detail;
        }

        public int generationExitCode() {
            return generationExitCode;
        }

        public int consumptionExitCode() {
            return consumptionExitCode;
        }

        public boolean outputTruncated() {
            return outputTruncated;
        }

        public long proofArchiveBytes() {
            return proofArchiveBytes;
        }

        public String proofArchiveSha256() {
            return proofArchiveSha256;
        }

        public boolean supported() {
            return status == Status.SUPPORTED && javaIdentity != null;
        }

        /** Flags for a training launch that creates an application archive. */
        public List<String> archiveCreationArguments(Path launchJavaExecutable, Path archive) {
            if (!supportsExactJava(launchJavaExecutable)) return List.of();
            Path safeArchive = safeCreationTarget(archive);
            if (safeArchive == null) return List.of();
            return List.of(XSHARE_ON, ARCHIVE_AT_EXIT_PREFIX + safeArchive);
        }

        /** Flags for a launch that consumes an already-created application archive. */
        public List<String> archiveConsumptionArguments(Path launchJavaExecutable, Path archive) {
            if (!supportsExactJava(launchJavaExecutable)) return List.of();
            Path safeArchive = safeExistingArchive(archive);
            if (safeArchive == null) return List.of();
            return List.of(XSHARE_ON, SHARED_ARCHIVE_PREFIX + safeArchive);
        }

        private boolean supportsExactJava(Path launchJavaExecutable) {
            return supported() && javaIdentity.matches(launchJavaExecutable);
        }
    }

    public static Result detect(Path javaExecutable, Path workDirectory, Duration timeout) {
        JavaIdentity javaIdentity = null;
        Path probeDirectory = null;
        try {
            Objects.requireNonNull(javaExecutable, "javaExecutable");
            Objects.requireNonNull(workDirectory, "workDirectory");
            Objects.requireNonNull(timeout, "timeout");
            long timeoutMillis = timeout.toMillis();
            if (timeoutMillis <= 0) throw new IllegalArgumentException("timeout must be positive");

            javaIdentity = JavaIdentity.capture(javaExecutable);
            Path resolvedJava = javaIdentity.path();

            Files.createDirectories(workDirectory);
            Path resolvedWork = workDirectory.toRealPath();
            if (!Files.isDirectory(resolvedWork, LinkOption.NOFOLLOW_LINKS)) {
                return failure(Status.ERROR, javaIdentity, "Probe work path is not a directory", -1, -1, false);
            }
            probeDirectory = Files.createTempDirectory(resolvedWork, "appcds-capability-");
            Path probeJar = probeDirectory.resolve("probe.jar");
            Path archive = probeDirectory.resolve("probe.jsa");
            createProbeJar(probeJar);

            ProcessResult generation = runProcess(List.of(
                    resolvedJava.toString(),
                    XSHARE_ON,
                    ARCHIVE_AT_EXIT_PREFIX + archive,
                    "-jar",
                    probeJar.toString()), timeoutMillis);
            if (generation.timedOut()) {
                return failure(Status.TIMED_OUT, javaIdentity, "Archive creation timed out", -1, -1,
                        generation.truncated());
            }
            if (generation.exitCode() != 0) {
                return failure(classifyFailure(generation.output()), javaIdentity,
                        "Archive creation failed: " + generation.output(), generation.exitCode(), -1,
                        generation.truncated());
            }
            if (generation.truncated()) {
                return failure(Status.ERROR, javaIdentity, "Archive creation output exceeded its limit",
                        generation.exitCode(), -1, true);
            }
            if (Files.isSymbolicLink(archive)
                    || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
                return failure(Status.ERROR, javaIdentity, "Archive creation produced no regular archive",
                        generation.exitCode(), -1, false);
            }
            long archiveBytes = Files.size(archive);
            if (archiveBytes <= 0 || archiveBytes > MAX_ARCHIVE_BYTES) {
                return failure(Status.ERROR, javaIdentity,
                        "Archive size was outside the allowed range: " + archiveBytes,
                        generation.exitCode(), -1, false);
            }
            String archiveSha256 = sha256Bounded(archive, MAX_ARCHIVE_BYTES);

            ProcessResult consumption = runProcess(List.of(
                    resolvedJava.toString(),
                    XSHARE_ON,
                    SHARED_ARCHIVE_PREFIX + archive,
                    "-jar",
                    probeJar.toString()), timeoutMillis);
            if (consumption.timedOut()) {
                return new Result(Status.TIMED_OUT, javaIdentity, "Archive consumption timed out",
                        generation.exitCode(), -1, consumption.truncated(), archiveBytes, archiveSha256);
            }
            if (consumption.exitCode() != 0) {
                return new Result(classifyFailure(consumption.output()), javaIdentity,
                        "Archive consumption failed: " + consumption.output(), generation.exitCode(),
                        consumption.exitCode(), consumption.truncated(), archiveBytes, archiveSha256);
            }
            if (consumption.truncated()) {
                return new Result(Status.ERROR, javaIdentity, "Archive consumption output exceeded its limit",
                        generation.exitCode(), consumption.exitCode(), true, archiveBytes, archiveSha256);
            }
            if (!consumption.output().contains(AppCdsProbeMain.MARKER)) {
                return new Result(Status.ERROR, javaIdentity, "Archive consumption omitted the probe marker",
                        generation.exitCode(), consumption.exitCode(), false, archiveBytes, archiveSha256);
            }
            if (!javaIdentity.matches(resolvedJava)) {
                return new Result(Status.ERROR, javaIdentity, "Java executable identity changed during probing",
                        generation.exitCode(), consumption.exitCode(), false, archiveBytes, archiveSha256);
            }
            return new Result(Status.SUPPORTED, javaIdentity,
                    "Dynamic AppCDS archive creation and consumption succeeded",
                    generation.exitCode(), consumption.exitCode(), false, archiveBytes, archiveSha256);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return failure(Status.ERROR, javaIdentity, "Capability detection was interrupted", -1, -1, false);
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (IOException | RuntimeException error) {
            return failure(Status.ERROR, javaIdentity,
                    error.getClass().getSimpleName() + ": " + error.getMessage(), -1, -1, false);
        } finally {
            deleteRecursively(probeDirectory);
        }
    }

    private static Result failure(
            Status status,
            JavaIdentity javaIdentity,
            String detail,
            int generationExitCode,
            int consumptionExitCode,
            boolean truncated) {
        return new Result(status, javaIdentity, detail, generationExitCode, consumptionExitCode, truncated, 0, "");
    }

    private static Status classifyFailure(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (normalized.contains("unrecognized vm option")
                || normalized.contains("unrecognized option")
                || normalized.contains("is not supported")
                || normalized.contains("archiveclassesatexit is unsupported")
                || normalized.contains("shared spaces are not supported")) {
            return Status.UNSUPPORTED;
        }
        return Status.ERROR;
    }

    private static void createProbeJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, AppCdsProbeMain.class.getName());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            addClass(output, AppCdsProbeMain.class);
            addClass(output, AppCdsProbeMain.ProbePayload.class);
        }
    }

    private static void addClass(JarOutputStream output, Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        byte[] bytes = readClassBytes(type, entryName);
        JarEntry entry = new JarEntry(entryName);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static byte[] readClassBytes(Class<?> type, String entryName) throws IOException {
        try (InputStream input = type.getResourceAsStream('/' + entryName)) {
            if (input == null) throw new IOException("Missing probe class bytes for " + type.getName());
            byte[] bytes = input.readNBytes(MAX_CLASS_BYTES + 1);
            if (bytes.length > MAX_CLASS_BYTES) {
                throw new IOException("Probe class exceeds its byte limit: " + type.getName());
            }
            return bytes;
        }
    }

    private static ProcessResult runProcess(List<String> command, long timeoutMillis)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(new ArrayList<>(command))
                .redirectErrorStream(true)
                .start();
        OutputCollector collector = new OutputCollector(MAX_PROCESS_OUTPUT_BYTES);
        Thread outputThread = new Thread(() -> collector.drain(process.getInputStream()), "appcds-probe-output");
        outputThread.setDaemon(true);
        outputThread.start();

        boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroy();
            if (!process.waitFor(PROCESS_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(PROCESS_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
        outputThread.join(PROCESS_SHUTDOWN_GRACE.toMillis());
        if (outputThread.isAlive()) {
            process.getInputStream().close();
            outputThread.join(PROCESS_SHUTDOWN_GRACE.toMillis());
        }
        IOException readFailure = collector.failure();
        if (readFailure != null && completed) throw readFailure;
        return new ProcessResult(
                completed ? process.exitValue() : -1,
                !completed,
                collector.truncated(),
                collector.output());
    }

    private static Path safeCreationTarget(Path archive) {
        if (archive == null || archive.getFileName() == null) return null;
        try {
            Path absolute = archive.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent == null) return null;
            Path realParent = parent.toRealPath();
            if (!Files.isDirectory(realParent, LinkOption.NOFOLLOW_LINKS)) return null;
            Path target = realParent.resolve(absolute.getFileName()).normalize();
            if (!target.getParent().equals(realParent)) return null;
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(target)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
                return null;
            }
            return target;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static Path safeExistingArchive(Path archive) {
        if (archive == null) return null;
        try {
            Path absolute = archive.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(absolute)) return null;
            Path real = absolute.toRealPath();
            if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) return null;
            long size = Files.size(real);
            if (size <= 0 || size > MAX_ARCHIVE_BYTES) return null;
            return real;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static String sha256Bounded(Path path, long maxBytes) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream raw = Files.newInputStream(path);
             DigestInputStream input = new DigestInputStream(raw, digest)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = Math.addExact(total, read);
                if (total > maxBytes) throw new IOException("File exceeds its hash byte limit: " + path);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String boundedDetail(String value) {
        String normalized = value.replace('\u0000', '?').strip();
        if (normalized.length() <= MAX_DETAIL_CHARS) return normalized;
        return normalized.substring(0, MAX_DETAIL_CHARS) + "...";
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Probe cleanup is best effort and never changes capability classification.
                }
            });
        } catch (IOException ignored) {
            // Probe cleanup is best effort and never changes capability classification.
        }
    }

    private record JavaIdentity(Path path, long bytes, long modifiedMillis, String sha256) {
        private JavaIdentity {
            path = path.toAbsolutePath().normalize();
            if (bytes <= 0 || bytes > MAX_JAVA_EXECUTABLE_BYTES) {
                throw new IllegalArgumentException("Java executable size is outside the allowed range");
            }
            Objects.requireNonNull(sha256, "sha256");
        }

        private static JavaIdentity capture(Path executable) throws IOException {
            Path real = executable.toRealPath();
            if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Java executable is not a regular file: " + real);
            }
            long bytes = Files.size(real);
            if (bytes <= 0 || bytes > MAX_JAVA_EXECUTABLE_BYTES) {
                throw new IOException("Java executable size is outside the allowed range: " + bytes);
            }
            long modifiedMillis = Files.getLastModifiedTime(real, LinkOption.NOFOLLOW_LINKS).toMillis();
            return new JavaIdentity(real, bytes, modifiedMillis, sha256Bounded(real, MAX_JAVA_EXECUTABLE_BYTES));
        }

        private boolean matches(Path candidate) {
            if (candidate == null) return false;
            try {
                Path real = candidate.toRealPath();
                if (!path.equals(real)) return false;
                if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) return false;
                if (Files.size(real) != bytes) return false;
                if (Files.getLastModifiedTime(real, LinkOption.NOFOLLOW_LINKS).toMillis() != modifiedMillis) return false;
                return sha256.equals(sha256Bounded(real, MAX_JAVA_EXECUTABLE_BYTES));
            } catch (IOException | RuntimeException error) {
                return false;
            }
        }
    }

    private record ProcessResult(int exitCode, boolean timedOut, boolean truncated, String output) {
    }

    private static final class OutputCollector {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final AtomicReference<IOException> failure = new AtomicReference<>();
        private volatile boolean truncated;

        private OutputCollector(int limit) {
            this.limit = limit;
        }

        private void drain(InputStream input) {
            try (input) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    int remaining = limit - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(remaining, read));
                    if (read > remaining) truncated = true;
                }
            } catch (IOException error) {
                failure.compareAndSet(null, error);
            }
        }

        private boolean truncated() {
            return truncated;
        }

        private IOException failure() {
            return failure.get();
        }

        private String output() {
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
