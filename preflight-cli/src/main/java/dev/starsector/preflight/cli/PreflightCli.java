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
            case "doctor" -> RunCommand.doctor(CommandLine.parse(args, 1));
            case "install" -> InstallCommand.execute(CommandLine.parse(args, 1));
            case "scan" -> ScanCommand.execute(ScanOptions.parse(args, 1));
            case "index" -> IndexCommand.execute(args, 1);
            case "fingerprint" -> requirePathCommand(args, "fingerprint", PreflightCli::fingerprint);
            case "summarize" -> summarizeCommand(args);
            default -> {
                usage();
                yield 2;
            }
        };
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

    private static int fingerprint(Path path) throws IOException {
        System.out.println(ContentFingerprint.compute(path));
        return 0;
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
        System.err.println("  preflight run [--game <path>] [--launcher <path>] [--trace-dir <path>] [--dry-run] [--no-summary] [--no-scan] [-- <launcher args>]");
        System.err.println("  preflight doctor [--game <path>] [--launcher <path>]");
        System.err.println("  preflight install [--game <path>] [--launcher <path>]");
        System.err.println("  preflight scan [--game <path>] [--launcher <path>] [--json <profile.json>]");
        System.err.println("  preflight index build [--game <path>] [--launcher <path>] [--output <index.spfi>]");
        System.err.println("  preflight index inspect <index.spfi>");
        System.err.println("  preflight index query <index.spfi> <logical-path> [--all]");
        System.err.println("  preflight fingerprint <file-or-directory>");
        System.err.println("  preflight summarize <recording.jfr> [--json <report.json>]");
    }

    @FunctionalInterface
    private interface PathCommand {
        int run(Path path) throws Exception;
    }

    private static final class TraceAccumulator {
        private final Map<String, Long> counts = new LinkedHashMap<>();
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
                    fileReadNanos += duration;
                    fileReadBytes += longField(event, "bytesRead");
                }
                case "jdk.FileWrite" -> {
                    fileWriteNanos += duration;
                    fileWriteBytes += longField(event, "bytesWritten");
                }
                case "jdk.Compilation" -> compilationNanos += duration;
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
            values.put("eventTypeCounts", counts);
            return Json.object(values);
        }

        private static long longField(RecordedEvent event, String field) {
            return event.hasField(field) ? event.getLong(field) : 0L;
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
