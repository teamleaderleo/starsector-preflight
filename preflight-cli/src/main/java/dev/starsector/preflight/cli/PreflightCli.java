package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.ContentFingerprint;
import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public final class PreflightCli {
    private PreflightCli() {
    }

    public static void main(String[] args) {
        try {
            int status = run(args);
            if (status != 0) {
                System.exit(status);
            }
        } catch (Exception error) {
            System.err.println("preflight: " + error.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0
                || "help".equals(args[0])
                || "--help".equals(args[0])
                || "-h".equals(args[0])) {
            usage();
            return args.length == 0 ? 2 : 0;
        }

        return switch (args[0]) {
            case "run" -> RunCommand.execute(CommandLine.parse(args, 1));
            case "prepare" -> PrepareCommand.execute(args, 1);
            case "doctor" -> RunCommand.doctor(CommandLine.parse(args, 1));
            case "install" -> InstallCommand.execute(CommandLine.parse(args, 1));
            case "scan" -> ScanCommand.execute(ScanOptions.parse(args, 1));
            case "index" -> IndexCommand.execute(args, 1);
            case "texture" -> textureCommand(args);
            case "audio" -> audioCommand(args);
            case "classpath" -> ClasspathCommand.execute(args, 1);
            case "benchmark" -> BenchmarkCommand.execute(args, 1);
            case "analyze" -> AnalysisCommand.execute(args, 1);
            case "fingerprint" -> requirePathCommand(args, "fingerprint", PreflightCli::fingerprint);
            case "summarize" -> summarizeCommand(args);
            default -> {
                usage();
                yield 2;
            }
        };
    }

    private static int textureCommand(String[] args) throws Exception {
        if (args.length > 1 && "build".equals(args[1])) {
            return TextureBatchCommand.execute(args, 2);
        }
        if (args.length > 1 && "manifest".equals(args[1])) {
            return TextureManifestCommand.execute(args, 2);
        }
        return TextureCommand.execute(args, 1);
    }

    private static int audioCommand(String[] args) throws Exception {
        if (args.length > 1 && "jorbis-equivalence".equals(args[1])) {
            return InstalledJorbisEquivalenceCommand.execute(args, 2);
        }
        if (args.length > 1 && "sound-wrapper-observe".equals(args[1])) {
            return SoundWrapperObservationCommand.execute(args, 2);
        }
        throw new IllegalArgumentException(
                "Expected: audio <jorbis-equivalence|sound-wrapper-observe> ...");
    }

    private static int requirePathCommand(String[] args, String name, PathCommand command) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: " + name + " <path>");
        }
        return command.run(Path.of(args[1]));
    }

    private static int summarizeCommand(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected: summarize <recording.jfr> [--json <report.json>]");
        }
        return summarize(Path.of(args[1]), outputPath(args));
    }

    private static Path outputPath(String[] args) {
        if (args.length == 4 && "--json".equals(args[2])) {
            return Path.of(args[3]);
        }
        if (args.length == 2) {
            return null;
        }
        throw new IllegalArgumentException("Expected: summarize <recording.jfr> [--json <report.json>]");
    }

    static int summarize(Path recording, Path output) throws IOException {
        TraceAccumulator trace = new TraceAccumulator();
        try (RecordingFile file = new RecordingFile(recording)) {
            while (file.hasMoreEvents()) {
                trace.accept(file.readEvent());
            }
        }

        String json = trace.toJson(recording);
        if (output == null) {
            System.out.println(json);
        } else {
            Path absolute = output.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absolute, json + System.lineSeparator());
            System.out.println(absolute);
        }
        return 0;
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("  preflight run [--game <path>] [--launcher <path>] [--trace-dir <path>] [--dry-run] [--no-summary] [--no-scan] [--adapter-probe | --adapter | --no-adapter] [--adapter-targets <path>] [-- <launcher args>]");
        System.err.println("  preflight prepare [--game <path>] [--launcher <path>] [--cache-dir <path>] [--report <path>] [--workers <count>] [--memory-mb <MiB>] [--deep] [--verify-lookups] [--lookup-queries <count>] [--seed <long>] [--no-resource-index] [--no-classpath] [--no-textures]");
        System.err.println("  preflight doctor [--game <path>] [--launcher <path>]");
        System.err.println("  preflight install [--game <path>] [--launcher <path>]");
        System.err.println("  preflight scan [--game <path>] [--launcher <path>] [--json <profile.json>]");
        System.err.println("  preflight index build [--game <path>] [--launcher <path>] [--output <index.spfi>]");
        System.err.println("  preflight index inspect <index.spfi>");
        System.err.println("  preflight index query <index.spfi> <logical-path> [--all]");
        System.err.println("  preflight index validate <index.spfi>");
        System.err.println("  preflight texture prepare <image> [--output <texture.spft>]");
        System.err.println("  preflight texture inspect <texture.spft>");
        System.err.println("  preflight texture verify <image> <texture.spft>");
        System.err.println("  preflight texture benchmark <image> <texture.spft> [--runs <count>]");
        System.err.println("  preflight texture build [--game <path> | --index <index.spfi>] [--cache-dir <path>] [--workers <count>] [--memory-mb <MiB>]");
        System.err.println("  preflight texture manifest inspect <manifest.spfm>");
        System.err.println("  preflight texture manifest query <manifest.spfm> <logical-path> [--cache-dir <path>]");
        System.err.println("  preflight texture manifest validate <manifest.spfm> [--cache-dir <path>] [--deep]");
        System.err.println("  preflight audio jorbis-equivalence --jogg <jogg-0.0.7.jar> --jorbis <jorbis-0.0.15.jar> [--output <report.json>]");
        System.err.println("  preflight audio sound-wrapper-observe --game <Starsector directory> --jogg <jogg-0.0.7.jar> --jorbis <jorbis-0.0.15.jar> [--output <report.json>]");
        System.err.println("  preflight classpath audit [--game <path>] [--launcher <path>] [--json <report.json>]");
        System.err.println("  preflight classpath index build [--game <path>] [--launcher <path>] [--cache-dir <path>]");
        System.err.println("  preflight classpath index inspect <profile.spfc>");
        System.err.println("  preflight classpath index query <profile.spfc> <entry-name> [--all] [--cache-dir <path>]");
        System.err.println("  preflight classpath index validate <profile.spfc> [--cache-dir <path>] [--deep]");
        System.err.println("  preflight benchmark lookups [--resource-index <index.spfi>] [--classpath-index <profile.spfc>] [--queries <count>] [--seed <long>]");
        System.err.println("  preflight analyze probe <adapter.json> <summary.json> [--json <adapter-analysis.json>]");
        System.err.println("  preflight fingerprint <file-or-directory>");
        System.err.println("  preflight summarize <recording.jfr> [--json <report.json>]");
    }

    @FunctionalInterface
    private interface PathCommand {
        int run(Path path) throws Exception;
    }

    private static final class TraceAccumulator {
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final IoTraceAttribution io = new IoTraceAttribution();
        private final ImageReadStackAttribution imageReadStacks = new ImageReadStackAttribution();
        private final StartupCodeAttribution code = new StartupCodeAttribution();
        private final StartupCpuAttribution cpu = new StartupCpuAttribution();
        private Instant first;
        private Instant last;
        private long fileReadNanos;
        private long fileWriteNanos;
        private long compilationNanos;
        private long gcPauseNanos;
        private long parkNanos;
        private long sleepNanos;
        private long fileReadBytes;
        private long fileWriteBytes;

        void accept(RecordedEvent event) {
            String name = event.getEventType().getName();
            counts.merge(name, 1L, Long::sum);
            Instant start = event.getStartTime();
            Instant end = event.getEndTime();
            first = first == null || start.isBefore(first) ? start : first;
            last = last == null || end.isAfter(last) ? end : last;

            long duration = event.getDuration().toNanos();
            switch (name) {
                case "jdk.FileRead" -> {
                    long bytes = longField(event, "bytesRead");
                    String path = stringField(event, "path");
                    fileReadNanos += duration;
                    fileReadBytes += bytes;
                    io.recordRead(path, bytes, duration);
                    imageReadStacks.record(event, path, bytes, duration);
                }
                case "jdk.FileWrite" -> {
                    long bytes = longField(event, "bytesWritten");
                    fileWriteNanos += duration;
                    fileWriteBytes += bytes;
                    io.recordWrite(stringField(event, "path"), bytes, duration);
                }
                case "jdk.Compilation" -> {
                    compilationNanos += duration;
                    code.recordCompilation(event, duration);
                }
                case "jdk.ClassDefine" -> code.recordClassDefine(event);
                case "jdk.ExecutionSample" -> cpu.record(event);
                case "jdk.GCPhasePause" -> gcPauseNanos += duration;
                case "jdk.ThreadPark" -> parkNanos += duration;
                case "jdk.ThreadSleep" -> sleepNanos += duration;
                default -> {
                }
            }
        }

        String toJson(Path source) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("source", source.toAbsolutePath().normalize().toString());
            values.put("traceStart", first);
            values.put("traceEnd", last);
            values.put("traceDurationMs", first == null || last == null ? 0 : Duration.between(first, last).toMillis());
            values.put("fileReadMs", nanosToMillis(fileReadNanos));
            values.put("fileReadBytes", fileReadBytes);
            values.put("fileWriteMs", nanosToMillis(fileWriteNanos));
            values.put("fileWriteBytes", fileWriteBytes);
            values.put("compilationMs", nanosToMillis(compilationNanos));
            values.put("gcPauseMs", nanosToMillis(gcPauseNanos));
            values.put("threadParkMs", nanosToMillis(parkNanos));
            values.put("threadSleepMs", nanosToMillis(sleepNanos));
            values.put("classLoadEvents", counts.getOrDefault("jdk.ClassLoad", 0L));
            values.put("classDefineEvents", counts.getOrDefault("jdk.ClassDefine", 0L));
            values.put("executionSamples", counts.getOrDefault("jdk.ExecutionSample", 0L));
            values.put("preflightAgentStartedEvents", counts.getOrDefault("preflight.AgentStarted", 0L));
            values.put("ioAttribution", io.toMap());
            values.put("imageReadStackAttribution", imageReadStacks.toMap());
            values.put("codeAttribution", code.toMap());
            values.put("cpuAttribution", cpu.toMap());
            values.put("eventTypeCounts", counts);
            return Json.object(values);
        }

        private static long longField(RecordedEvent event, String field) {
            return event.hasField(field) ? Math.max(0, event.getLong(field)) : 0L;
        }

        private static String stringField(RecordedEvent event, String field) {
            if (!event.hasField(field)) {
                return null;
            }
            try {
                return event.getString(field);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
