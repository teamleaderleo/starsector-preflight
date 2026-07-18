package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Re-launches the sound-wrapper observation command with Starsector's bundled Java runtime.
 *
 * <p>The normal CLI is often built and invoked with a newer system JDK. Some reviewed Starsector
 * classes are loadable only by the runtime shipped with the game. This launcher keeps the outer
 * discovery process on the caller's JDK, then starts the unchanged shaded CLI with the selected
 * game runtime. The unchanged CLI subsequently starts its evidence child from that same java.home.
 * No game file is edited and no optimization is enabled.</p>
 */
public final class SoundWrapperObservationRuntimeLauncher {
    private static final int MAX_RUNTIME_CANDIDATES = 64;
    private static final int MAX_CHILD_OUTPUT_BYTES = 1 * 1024 * 1024;
    private static final long MAX_JAVA_EXECUTABLE_BYTES = 256L * 1024 * 1024;
    private static final Duration CHILD_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(15);

    private SoundWrapperObservationRuntimeLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Path application = SelfJar.locate().toAbsolutePath().normalize();
        int exit = execute(options, application);
        System.exit(exit);
    }

    static int execute(Options options, Path applicationJar) throws Exception {
        Path game = exactDirectory(options.game(), "Starsector installation");
        Path application = exactFile(applicationJar, "Preflight application JAR");
        JavaSelection java = selectJava(game, options.java());
        Path output = options.output() == null
                ? Path.of("sound-wrapper-observation.json").toAbsolutePath().normalize()
                : options.output().toAbsolutePath().normalize();
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);

        List<String> command = new ArrayList<>();
        command.add(java.executable().toString());
        command.add("-jar");
        command.add(application.toString());
        command.add("audio");
        command.add("sound-wrapper-observe");
        command.add("--game");
        command.add(game.toString());
        command.add("--jogg");
        command.add(options.jogg().toAbsolutePath().normalize().toString());
        command.add("--jorbis");
        command.add(options.jorbis().toAbsolutePath().normalize().toString());
        command.add("--output");
        command.add(output.toString());

        Process process = new ProcessBuilder(command)
                .directory(Path.of("").toAbsolutePath().normalize().toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream childOutput = new ByteArrayOutputStream();
        Thread reader = boundedReader(process.getInputStream(), childOutput, "Preflight-Sound-Wrapper-Runtime-Output");
        boolean completed = process.waitFor(CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        reader.join(10_000);
        String console = childOutput.toString(StandardCharsets.UTF_8);
        if (!completed) {
            throw new IOException("Bundled-runtime sound-wrapper command exceeded " + CHILD_TIMEOUT + ": " + console);
        }
        int exit = process.exitValue();
        if (!Files.isRegularFile(output)) {
            throw new IOException("Bundled-runtime command did not write its report; exit=" + exit + ": " + console);
        }

        RuntimeEvidence evidence = runtimeEvidence(java);
        mergeRuntimeEvidence(output, evidence);
        System.out.println(output);
        System.err.println("sound-wrapper-java=" + java.executable());
        if (!console.isBlank()) System.err.print(console);
        return exit;
    }

    static JavaSelection selectJava(Path game, Path explicitJava) throws IOException {
        Path root = exactDirectory(game, "Starsector installation");
        if (explicitJava != null) {
            return new JavaSelection(exactJava(explicitJava), "explicit");
        }

        Set<Path> candidates = new LinkedHashSet<>();
        for (String relative : List.of(
                "Contents/Home/bin/java",
                "Contents/PlugIns/runtime/Contents/Home/bin/java",
                "Contents/PlugIns/jre/Contents/Home/bin/java",
                "Contents/Resources/Java/runtime/Contents/Home/bin/java",
                "Contents/Resources/Java/jre/Contents/Home/bin/java",
                "Contents/Resources/Java/runtime/bin/java",
                "Contents/Resources/Java/jre/bin/java",
                "runtime/Contents/Home/bin/java",
                "jre/Contents/Home/bin/java",
                "runtime/bin/java",
                "jre/bin/java",
                "jdk/bin/java")) {
            Path candidate = root.resolve(relative).toAbsolutePath().normalize();
            if (isUsableJava(candidate)) candidates.add(candidate);
        }

        try (Stream<Path> paths = Files.find(
                root,
                12,
                (path, attributes) -> {
                    String expected = isWindows() ? "java.exe" : "java";
                    return path.getFileName() != null
                            && expected.equalsIgnoreCase(path.getFileName().toString())
                            && path.getParent() != null
                            && "bin".equalsIgnoreCase(path.getParent().getFileName().toString())
                            && (attributes.isRegularFile() || Files.isRegularFile(path));
                })) {
            paths.limit(MAX_RUNTIME_CANDIDATES + 1L)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(SoundWrapperObservationRuntimeLauncher::isUsableJava)
                    .forEach(candidates::add);
        }
        if (candidates.size() > MAX_RUNTIME_CANDIDATES) {
            throw new IOException("Bundled Java discovery exceeded " + MAX_RUNTIME_CANDIDATES + " candidates under " + root);
        }
        if (candidates.isEmpty()) {
            throw new IOException("Could not locate a bundled Java executable under " + root
                    + ". Pass --java <path-to-game-java> explicitly.");
        }

        Path selected = candidates.stream()
                .sorted(Comparator.comparingInt(SoundWrapperObservationRuntimeLauncher::runtimeScore)
                        .reversed()
                        .thenComparing(Path::toString))
                .findFirst()
                .orElseThrow();
        return new JavaSelection(selected, "bundled-auto");
    }

    static void mergeRuntimeEvidence(Path report, RuntimeEvidence evidence) throws IOException {
        String original = Files.readString(report, StandardCharsets.UTF_8).trim();
        if (original.length() < 2 || original.charAt(0) != '{' || original.charAt(original.length() - 1) != '}') {
            throw new IOException("Sound-wrapper report is not one JSON object: " + report);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("childJavaExecutable", evidence.executable().toString());
        values.put("childJavaSelectionSource", evidence.selectionSource());
        values.put("childJavaExecutableSha256", evidence.executableSha256());
        values.put("childJavaVersionOutputLength", evidence.versionOutputLength());
        values.put("childJavaVersionOutputSha256", evidence.versionOutputSha256());
        String prefix = Json.object(values);
        String fields = prefix.substring(1, prefix.length() - 1);
        String body = original.substring(1, original.length() - 1);
        String merged = "{" + fields + (body.isBlank() ? "" : "," + body) + "}" + System.lineSeparator();
        Files.writeString(report, merged, StandardCharsets.UTF_8);
    }

    private static RuntimeEvidence runtimeEvidence(JavaSelection selection) throws IOException {
        Path executable = selection.executable();
        long size = Files.size(executable);
        if (size < 1 || size > MAX_JAVA_EXECUTABLE_BYTES) {
            throw new IOException("Selected Java executable size is outside 1.."
                    + MAX_JAVA_EXECUTABLE_BYTES + ": " + executable);
        }
        String version = versionOutput(executable);
        return new RuntimeEvidence(
                executable,
                selection.source(),
                Hashes.sha256(executable),
                version.length(),
                Hashes.sha256(version.getBytes(StandardCharsets.UTF_8)));
    }

    private static String versionOutput(Path java) throws IOException {
        Process process = new ProcessBuilder(java.toString(), "-version")
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = boundedReader(process.getInputStream(), output, "Preflight-Java-Version-Output");
        try {
            boolean completed = process.waitFor(VERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Selected Java -version exceeded " + VERSION_TIMEOUT + ": " + java);
            }
            reader.join(5_000);
            if (process.exitValue() != 0) {
                throw new IOException("Selected Java -version failed with exit " + process.exitValue() + ": " + java);
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking selected Java", interrupted);
        }
    }

    private static Thread boundedReader(InputStream input, ByteArrayOutputStream output, String name) {
        Thread reader = new Thread(() -> copyBounded(input, output), name);
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[8_192];
        try (input) {
            while (true) {
                int count = input.read(buffer);
                if (count < 0) return;
                int remaining = MAX_CHILD_OUTPUT_BYTES - output.size();
                if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
            }
        } catch (IOException error) {
            if (output.size() < MAX_CHILD_OUTPUT_BYTES) {
                byte[] detail = ("\n[output-error] " + error.getClass().getName())
                        .getBytes(StandardCharsets.UTF_8);
                output.write(detail, 0, Math.min(detail.length, MAX_CHILD_OUTPUT_BYTES - output.size()));
            }
        }
    }

    private static int runtimeScore(Path path) {
        String value = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        int score = 0;
        if (value.endsWith("/contents/home/bin/java") || value.endsWith("/contents/home/bin/java.exe")) score += 1_000;
        if (value.contains("/contents/plugins/")) score += 300;
        if (value.contains("/contents/resources/java/")) score += 250;
        if (value.contains("/runtime/")) score += 200;
        if (value.contains("/jre/")) score += 180;
        if (value.contains("/jdk/")) score += 160;
        if (Files.isExecutable(path)) score += 20;
        return score;
    }

    private static boolean isUsableJava(Path path) {
        return Files.isRegularFile(path) && (isWindows() || Files.isExecutable(path));
    }

    private static Path exactJava(Path raw) throws IOException {
        Path path = exactFile(raw, "Java executable");
        if (!isWindows() && !Files.isExecutable(path)) {
            throw new IOException("Java executable is not executable: " + path);
        }
        return path;
    }

    private static Path exactDirectory(Path raw, String name) throws IOException {
        if (raw == null) throw new IllegalArgumentException(name + " path is required");
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IOException(name + " is not a directory: " + path);
        return path;
    }

    private static Path exactFile(Path raw, String name) throws IOException {
        if (raw == null) throw new IllegalArgumentException(name + " path is required");
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException(name + " is not a regular file: " + path);
        return path;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    record JavaSelection(Path executable, String source) {
        JavaSelection {
            executable = executable.toAbsolutePath().normalize();
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        }
    }

    record RuntimeEvidence(
            Path executable,
            String selectionSource,
            String executableSha256,
            int versionOutputLength,
            String versionOutputSha256) {
    }

    record Options(Path game, Path jogg, Path jorbis, Path java, Path output) {
        static Options parse(String[] args) {
            Path game = null;
            Path jogg = null;
            Path jorbis = null;
            Path java = null;
            Path output = null;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--game" -> game = Path.of(value(args, ++i, "--game"));
                    case "--jogg" -> jogg = Path.of(value(args, ++i, "--jogg"));
                    case "--jorbis" -> jorbis = Path.of(value(args, ++i, "--jorbis"));
                    case "--java" -> java = Path.of(value(args, ++i, "--java"));
                    case "--output" -> output = Path.of(value(args, ++i, "--output"));
                    default -> throw new IllegalArgumentException("Unknown bundled-runtime option: " + args[i]);
                }
            }
            if (game == null || jogg == null || jorbis == null) {
                throw new IllegalArgumentException(
                        "Expected: --game <Starsector directory> --jogg <jogg-0.0.7.jar> --jorbis <jorbis-0.0.15.jar> [--java <game-java>] [--output <report.json>]");
            }
            return new Options(game, jogg, jorbis, java, output);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            return args[index];
        }
    }
}
