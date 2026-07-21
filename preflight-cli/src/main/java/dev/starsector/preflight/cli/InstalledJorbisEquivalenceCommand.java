package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Launches the exact installed decoder jars in an isolated application-classloader child JVM. */
final class InstalledJorbisEquivalenceCommand {
    static final String JOGG_SHA256 = "ed7946260897d97c468a4749b3d9d5e436a268fa948bc32e75a7487130e89379";
    static final String JORBIS_SHA256 = "d049b2a1c6ddefde3a5cbff320c96fdd5aefa09b0d3bbea3fe44839f7e6713f9";
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
    private static final int MAX_CHILD_OUTPUT_BYTES = 1 * 1024 * 1024;
    private static final Duration CHILD_TIMEOUT = Duration.ofMinutes(2);

    private InstalledJorbisEquivalenceCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        Options options = Options.parse(args, offset);
        return execute(options, JOGG_SHA256, JORBIS_SHA256, SelfJar.locate(), "full");
    }

    static int execute(Options options, String expectedJogg, String expectedJorbis, Path applicationJar)
            throws Exception {
        return execute(options, expectedJogg, expectedJorbis, applicationJar, "full");
    }

    static int execute(
            Options options,
            String expectedJogg,
            String expectedJorbis,
            Path applicationJar,
            String fixtureProfile) throws Exception {
        Path jogg = exactJar(options.jogg(), expectedJogg, "Jogg");
        Path jorbis = exactJar(options.jorbis(), expectedJorbis, "JOrbis");
        Path application = applicationJar.toAbsolutePath().normalize();
        if (!Files.isRegularFile(application)) {
            throw new IOException("Preflight application JAR is unavailable: " + application);
        }
        if (!"full".equals(fixtureProfile) && !"ci".equals(fixtureProfile)) {
            throw new IllegalArgumentException("Unknown installed-JOrbis fixture profile: " + fixtureProfile);
        }

        Path output = options.output() == null
                ? Files.createTempFile("preflight-installed-jorbis-", ".json")
                : options.output().toAbsolutePath().normalize();
        boolean temporaryOutput = options.output() == null;
        if (output.equals(jogg) || output.equals(jorbis) || output.equals(application)) {
            throw new IllegalArgumentException("Equivalence report path collides with an input JAR: " + output);
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.deleteIfExists(output);

        Path java = Path.of(System.getProperty("java.home"), "bin", executable("java"));
        String classpath = String.join(
                System.getProperty("path.separator"),
                application.toString(),
                jogg.toString(),
                jorbis.toString());
        List<String> command = List.of(
                java.toString(),
                "-cp",
                classpath,
                InstalledJorbisEquivalenceChild.class.getName(),
                "--expected-jogg-sha256",
                expectedJogg,
                "--expected-jorbis-sha256",
                expectedJorbis,
                "--fixture-profile",
                fixtureProfile,
                "--output",
                output.toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream childOutput = new ByteArrayOutputStream();
        Thread reader = new Thread(
                () -> copyBounded(process.getInputStream(), childOutput),
                "Preflight-JOrbis-Child-Output");
        reader.setDaemon(true);
        reader.start();
        boolean completed = process.waitFor(CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        reader.join(10_000);
        String console = childOutput.toString(StandardCharsets.UTF_8);
        if (!completed) {
            throw new IOException("Installed JOrbis child exceeded " + CHILD_TIMEOUT + ": " + console);
        }
        int exit = process.exitValue();
        if (!Files.isRegularFile(output)) {
            throw new IOException("Installed JOrbis child did not write its report; exit=" + exit + ": " + console);
        }
        String json = Files.readString(output, StandardCharsets.UTF_8);
        if (temporaryOutput) {
            System.out.print(json);
            Files.deleteIfExists(output);
        } else {
            System.out.println(output);
        }
        if (!console.isBlank()) {
            System.err.print(console);
        }
        return exit;
    }

    private static Path exactJar(Path raw, String expectedSha256, String name) throws IOException {
        if (raw == null) {
            throw new IllegalArgumentException(name + " JAR path is required");
        }
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException(name + " JAR is not a regular file: " + path);
        }
        long size = Files.size(path);
        if (size < 1 || size > MAX_JAR_BYTES) {
            throw new IOException(name + " JAR size is outside 1.." + MAX_JAR_BYTES + ": " + path);
        }
        String actual = Hashes.sha256(path);
        if (!expectedSha256.equals(actual)) {
            throw new IOException(name + " JAR SHA-256 differs: expected " + expectedSha256 + ", found " + actual);
        }
        return path;
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[8_192];
        try (input) {
            while (true) {
                int count = input.read(buffer);
                if (count < 0) return;
                int remaining = MAX_CHILD_OUTPUT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(count, remaining));
                }
            }
        } catch (IOException error) {
            if (output.size() < MAX_CHILD_OUTPUT_BYTES) {
                byte[] detail = ("\n[child-output-error] " + error.getMessage()).getBytes(StandardCharsets.UTF_8);
                output.write(detail, 0, Math.min(detail.length, MAX_CHILD_OUTPUT_BYTES - output.size()));
            }
        }
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? name + ".exe" : name;
    }

    record Options(Path jogg, Path jorbis, Path output) {
        static Options parse(String[] args, int offset) {
            Path jogg = null;
            Path jorbis = null;
            Path output = null;
            for (int i = offset; i < args.length; i++) {
                switch (args[i]) {
                    case "--jogg" -> jogg = Path.of(value(args, ++i, "--jogg"));
                    case "--jorbis" -> jorbis = Path.of(value(args, ++i, "--jorbis"));
                    case "--output" -> output = Path.of(value(args, ++i, "--output"));
                    default -> throw new IllegalArgumentException("Unknown installed-JOrbis option: " + args[i]);
                }
            }
            if (jogg == null || jorbis == null) {
                throw new IllegalArgumentException(
                        "Expected: audio jorbis-equivalence --jogg <jogg-0.0.7.jar> --jorbis <jorbis-0.0.15.jar> [--output <report.json>]");
            }
            return new Options(jogg, jorbis, output);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            return args[index];
        }
    }
}
