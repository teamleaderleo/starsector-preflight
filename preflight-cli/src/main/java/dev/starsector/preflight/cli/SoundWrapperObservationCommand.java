package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Runs the exact Starsector sound wrapper in an isolated evidence-only child JVM. */
final class SoundWrapperObservationCommand {
    private static final long MAX_JAR_BYTES = 256L * 1024 * 1024;
    private static final long MAX_CLASSPATH_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_DISCOVERED_JARS = 512;
    private static final int MAX_CLASSPATH_JARS = 256;

    private SoundWrapperObservationCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        Options options = Options.parse(args, offset);
        return SoundWrapperObservationRuntimeLauncher.execute(
                new SoundWrapperObservationRuntimeLauncher.Options(
                        options.game(), options.jogg(), options.jorbis(), options.java(), options.output()),
                SelfJar.locate());
    }

    static int execute(Options options, Path applicationJar, String fixtureProfile) throws Exception {
        return execute(
                options,
                InstalledJorbisEquivalenceCommand.JOGG_SHA256,
                InstalledJorbisEquivalenceCommand.JORBIS_SHA256,
                applicationJar,
                fixtureProfile);
    }

    static int execute(
            Options options,
            String expectedJoggSha256,
            String expectedJorbisSha256,
            Path applicationJar,
            String fixtureProfile) throws Exception {
        return SoundWrapperObservationRuntimeLauncher.execute(
                new SoundWrapperObservationRuntimeLauncher.Options(
                        options.game(), options.jogg(), options.jorbis(), options.java(), options.output()),
                applicationJar,
                expectedJoggSha256,
                expectedJorbisSha256,
                fixtureProfile);
    }

    static ObservationPlan prepare(
            Options options,
            String expectedJoggSha256,
            String expectedJorbisSha256,
            Path applicationJar,
            String fixtureProfile) throws Exception {
        Path game = exactDirectory(options.game(), "Starsector installation");
        Path jogg = exactJar(options.jogg(), expectedJoggSha256, "Jogg");
        Path jorbis = exactJar(options.jorbis(), expectedJorbisSha256, "JOrbis");
        Path application = applicationJar.toAbsolutePath().normalize();
        if (!Files.isRegularFile(application)) {
            throw new IOException("Preflight application JAR is unavailable: " + application);
        }
        if (!"full".equals(fixtureProfile) && !"ci".equals(fixtureProfile)) {
            throw new IllegalArgumentException("Unknown sound-wrapper fixture profile: " + fixtureProfile);
        }

        Path soundArchive = findSoundArchive(game);
        String expectedSoundArchiveSha256 = Hashes.sha256(soundArchive);
        List<Path> classpath = classpath(application, soundArchive, jogg, jorbis);
        if (classpath.isEmpty() || !application.equals(classpath.get(0))) {
            throw new IOException("Preflight application JAR must be first on the sound-wrapper classpath");
        }
        return new ObservationPlan(game, soundArchive, expectedSoundArchiveSha256, classpath);
    }

    static Path findSoundArchive(Path game) throws IOException {
        Path root = game.toAbsolutePath().normalize();
        List<Path> jars;
        try (var stream = Files.find(
                root,
                9,
                (path, attributes) -> attributes.isRegularFile()
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
            jars = stream.sorted().limit(MAX_DISCOVERED_JARS + 1L).toList();
        }
        if (jars.size() > MAX_DISCOVERED_JARS) {
            throw new IOException("Starsector JAR discovery exceeded " + MAX_DISCOVERED_JARS + " entries under " + root);
        }

        List<Path> matches = new ArrayList<>();
        for (Path jar : jars) {
            long size = Files.size(jar);
            if (size < 1 || size > MAX_JAR_BYTES) continue;
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (zip.getEntry("sound/J.class") != null && zip.getEntry("sound/F.class") != null) {
                    matches.add(jar.toAbsolutePath().normalize());
                }
            } catch (IOException ignored) {
                // Malformed unrelated archives cannot become a sound-wrapper provider.
            }
        }
        if (matches.isEmpty()) {
            throw new IOException("Could not locate one archive containing both sound/J.class and sound/F.class under " + root);
        }
        if (matches.size() != 1) {
            throw new IOException("Ambiguous sound-wrapper provider archives: " + matches);
        }
        return matches.get(0);
    }

    private static List<Path> classpath(Path application, Path soundArchive, Path jogg, Path jorbis)
            throws IOException {
        LinkedHashSet<Path> ordered = new LinkedHashSet<>();
        ordered.add(application);
        ordered.add(soundArchive);
        ordered.add(jogg);
        ordered.add(jorbis);

        Path directory = soundArchive.getParent();
        if (directory != null) {
            try (var stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(ordered::add);
            }
        }
        if (ordered.size() > MAX_CLASSPATH_JARS) {
            throw new IOException("Sound-wrapper classpath exceeded " + MAX_CLASSPATH_JARS + " JARs");
        }
        long total = 0;
        for (Path path : ordered) {
            if (!Files.isRegularFile(path)) throw new IOException("Classpath entry is not a regular file: " + path);
            long size = Files.size(path);
            if (size < 1 || size > MAX_JAR_BYTES) {
                throw new IOException("Classpath JAR size is outside 1.." + MAX_JAR_BYTES + ": " + path);
            }
            total = Math.addExact(total, size);
            if (total > MAX_CLASSPATH_BYTES) {
                throw new IOException("Sound-wrapper classpath exceeds " + MAX_CLASSPATH_BYTES + " bytes");
            }
        }
        return List.copyOf(ordered);
    }

    private static Path exactDirectory(Path raw, String name) throws IOException {
        if (raw == null) throw new IllegalArgumentException(name + " path is required");
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IOException(name + " is not a directory: " + path);
        return path;
    }

    private static Path exactJar(Path raw, String expectedSha256, String name) throws IOException {
        if (raw == null) throw new IllegalArgumentException(name + " JAR path is required");
        Path path = raw.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException(name + " JAR is not a regular file: " + path);
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

    record ObservationPlan(
            Path game,
            Path soundArchive,
            String expectedSoundArchiveSha256,
            List<Path> classpath) {
    }

    record Options(Path game, Path jogg, Path jorbis, Path java, Path output) {
        Options(Path game, Path jogg, Path jorbis, Path output) {
            this(game, jogg, jorbis, null, output);
        }

        static Options parse(String[] args, int offset) {
            Path game = null;
            Path jogg = null;
            Path jorbis = null;
            Path java = null;
            Path output = null;
            for (int i = offset; i < args.length; i++) {
                switch (args[i]) {
                    case "--game" -> game = Path.of(value(args, ++i, "--game"));
                    case "--jogg" -> jogg = Path.of(value(args, ++i, "--jogg"));
                    case "--jorbis" -> jorbis = Path.of(value(args, ++i, "--jorbis"));
                    case "--java" -> java = Path.of(value(args, ++i, "--java"));
                    case "--output" -> output = Path.of(value(args, ++i, "--output"));
                    default -> throw new IllegalArgumentException("Unknown sound-wrapper option: " + args[i]);
                }
            }
            if (game == null || jogg == null || jorbis == null) {
                throw new IllegalArgumentException(
                        "Expected: audio sound-wrapper-observe --game <Starsector directory> --jogg <jogg-0.0.7.jar> --jorbis <jorbis-0.0.15.jar> [--java <game-java>] [--output <report.json>]");
            }
            return new Options(game, jogg, jorbis, java, output);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            return args[index];
        }
    }
}
