package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Launches the sound-wrapper observation child with Starsector's bundled Java runtime.
 *
 * <p>The normal CLI is often built and invoked with a newer system JDK. Some reviewed Starsector
 * classes are loadable only by the runtime shipped with the game. This launcher keeps discovery
 * on the caller's JDK, validates one exact game Java executable, and uses that same executable to
 * start the evidence child directly. No game file is edited and no optimization is enabled.</p>
 */
public final class SoundWrapperObservationRuntimeLauncher {
    private static final int MAX_RUNTIME_CANDIDATES = 64;
    private static final int MAX_CHILD_OUTPUT_BYTES = 1 * 1024 * 1024;
    private static final long MAX_REPORT_BYTES = 2L * 1024 * 1024;
    private static final long MAX_JAVA_EXECUTABLE_BYTES = 256L * 1024 * 1024;
    private static final Duration CHILD_TIMEOUT = Duration.ofMinutes(2);
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
        return execute(
                options,
                applicationJar,
                InstalledJorbisEquivalenceCommand.JOGG_SHA256,
                InstalledJorbisEquivalenceCommand.JORBIS_SHA256,
                "full");
    }

    static int execute(
            Options options,
            Path applicationJar,
            String expectedJoggSha256,
            String expectedJorbisSha256,
            String fixtureProfile) throws Exception {
        Path game = exactDirectory(options.game(), "Starsector installation");
        Path application = exactFile(applicationJar, "Preflight application JAR");
        JavaSelection java = selectJava(game, options.java());
        SoundWrapperObservationCommand.ObservationPlan plan = SoundWrapperObservationCommand.prepare(
                new SoundWrapperObservationCommand.Options(
                        game, options.jogg(), options.jorbis(), java.executable(), options.output()),
                expectedJoggSha256,
                expectedJorbisSha256,
                application,
                fixtureProfile);
        Path output = options.output() == null
                ? Path.of("sound-wrapper-observation.json").toAbsolutePath().normalize()
                : options.output().toAbsolutePath().normalize();
        prepareOutput(output, plan, java.executable());
        RuntimeEvidence evidence = runtimeEvidence(java);

        List<String> command = new ArrayList<>();
        command.add(java.executable().toString());
        command.add("-cp");
        command.add(String.join(
                System.getProperty("path.separator"),
                plan.classpath().stream().map(Path::toString).toList()));
        command.add(SoundWrapperObservationChild.class.getName());
        command.add("--expected-sound-sha256");
        command.add(plan.expectedSoundArchiveSha256());
        command.add("--expected-jogg-sha256");
        command.add(expectedJoggSha256);
        command.add("--expected-jorbis-sha256");
        command.add(expectedJorbisSha256);
        command.add("--fixture-profile");
        command.add(fixtureProfile);
        command.add("--output");
        command.add(output.toString());

        Process process = new ProcessBuilder(command)
                .directory(plan.soundArchive().getParent().toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream childOutput = new ByteArrayOutputStream();
        Thread reader = boundedReader(process.getInputStream(), childOutput, "Preflight-Sound-Wrapper-Child-Output");
        boolean completed = process.waitFor(CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        reader.join(10_000);
        String console = childOutput.toString(StandardCharsets.UTF_8);
        if (!completed) {
            throw new IOException("Sound-wrapper child exceeded " + CHILD_TIMEOUT + ": " + console);
        }
        int exit = process.exitValue();
        if (!Files.isRegularFile(output)) {
            throw new IOException("Sound-wrapper child did not write its report; exit=" + exit + ": " + console);
        }

        String executableSha256AfterLaunch = hashExecutable(java.executable());
        if (!evidence.executableSha256().equals(executableSha256AfterLaunch)) {
            throw new IOException("Selected Java executable changed between validation and child completion: "
                    + java.executable());
        }
        mergeRuntimeEvidence(output, evidence);
        System.out.println(output);
        System.err.println("sound-wrapper-java=" + java.executable());
        if (!console.isBlank()) System.err.print(console);
        return exit;
    }

    static JavaSelection selectJava(Path game, Path explicitJava) throws IOException {
        return selectJava(game, explicitJava, SoundWrapperObservationRuntimeLauncher::versionOutput);
    }

    static JavaSelection selectJava(Path game, Path explicitJava, JavaProbe probe) throws IOException {
        Path root = exactDirectory(game, "Starsector installation");
        if (explicitJava != null) {
            Path selected = exactJava(explicitJava);
            String version = probe.probe(selected);
            return new JavaSelection(selected, "explicit", version);
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

        List<Path> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(SoundWrapperObservationRuntimeLauncher::runtimeScore)
                        .reversed()
                        .thenComparing(Path::toString))
                .toList();
        IOException lastFailure = null;
        for (Path candidate : ordered) {
            try {
                String version = probe.probe(candidate);
                return new JavaSelection(candidate, "bundled-auto", version);
            } catch (IOException failure) {
                lastFailure = failure;
            }
        }
        throw new IOException(
                "Could not launch any of " + ordered.size() + " bundled Java candidates under " + root
                        + ". Pass --java <path-to-game-java> explicitly.",
                lastFailure);
    }

    static void mergeRuntimeEvidence(Path report, RuntimeEvidence evidence) throws IOException {
        long size = Files.size(report);
        if (size < 2 || size > MAX_REPORT_BYTES) {
            throw new IOException("Sound-wrapper report size is outside 2.." + MAX_REPORT_BYTES + ": " + report);
        }
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
        for (String key : values.keySet()) {
            if (original.contains("\"" + key + "\"")) {
                throw new IOException("Sound-wrapper report already contains reserved runtime key: " + key);
            }
        }
        String prefix = Json.object(values);
        String fields = prefix.substring(1, prefix.length() - 1);
        String body = original.substring(1, original.length() - 1);
        String merged = "{" + fields + (body.isBlank() ? "" : "," + body) + "}" + System.lineSeparator();
        Path parent = report.toAbsolutePath().normalize().getParent();
        Path staged = Files.createTempFile(parent, ".sound-wrapper-runtime-", ".json");
        try {
            Files.writeString(staged, merged, StandardCharsets.UTF_8);
            try {
                Files.move(staged, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, report, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static RuntimeEvidence runtimeEvidence(JavaSelection selection) throws IOException {
        Path executable = selection.executable();
        String version = selection.versionOutput();
        return new RuntimeEvidence(
                executable,
                selection.source(),
                hashExecutable(executable),
                version.length(),
                Hashes.sha256(version.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hashExecutable(Path executable) throws IOException {
        long size = Files.size(executable);
        if (size < 1 || size > MAX_JAVA_EXECUTABLE_BYTES) {
            throw new IOException("Selected Java executable size is outside 1.."
                    + MAX_JAVA_EXECUTABLE_BYTES + ": " + executable);
        }
        return Hashes.sha256(executable);
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

    static void prepareOutput(
            Path output,
            SoundWrapperObservationCommand.ObservationPlan plan,
            Path javaExecutable) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Path gameRoot = plan.game().toRealPath();
        Path existingAncestor = absoluteOutput;
        while (existingAncestor != null && !Files.exists(existingAncestor)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor != null && existingAncestor.toRealPath().startsWith(gameRoot)) {
            throw new IOException("Observation report must be outside the Starsector installation: " + absoluteOutput);
        }
        if (Files.exists(absoluteOutput) && absoluteOutput.toRealPath().startsWith(gameRoot)) {
            throw new IOException("Observation report must be outside the Starsector installation: " + absoluteOutput);
        }

        List<Path> inputs = new ArrayList<>(plan.classpath());
        inputs.add(javaExecutable);
        for (Path input : inputs) {
            if (absoluteOutput.equals(input)
                    || (Files.exists(absoluteOutput) && Files.isSameFile(absoluteOutput, input))) {
                throw new IOException("Observation report path collides with a probe input: " + absoluteOutput);
            }
        }

        Path parent = absoluteOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            if (parent.toRealPath().startsWith(gameRoot)) {
                throw new IOException("Observation report must be outside the Starsector installation: " + absoluteOutput);
            }
        }
        Files.deleteIfExists(absoluteOutput);
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

    @FunctionalInterface
    interface JavaProbe {
        String probe(Path java) throws IOException;
    }

    record JavaSelection(Path executable, String source, String versionOutput) {
        JavaSelection {
            executable = executable.toAbsolutePath().normalize();
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
            if (versionOutput == null) throw new IllegalArgumentException("versionOutput is required");
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
