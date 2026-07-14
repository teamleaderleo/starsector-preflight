package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RunCommand {
    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private RunCommand() {
    }

    static int execute(CommandLine options) throws Exception {
        Platform platform = Platform.current();
        Path home = Path.of(System.getProperty("user.home"));
        DiscoveryResult discovery = StarsectorDiscovery.discover(
                platform,
                home,
                Path.of(System.getProperty("user.dir")),
                System.getenv(),
                options.game(),
                options.launcher());
        LaunchTarget target = discovery.selected();
        if (target == null) {
            printDiscovery(discovery);
            return 3;
        }

        Path runDirectory = options.traceDirectory() == null
                ? home.resolve(".starsector-preflight").resolve("runs").resolve(RUN_ID.format(Instant.now()))
                : options.traceDirectory().toAbsolutePath().normalize();
        Path recording = runDirectory.resolve("startup.jfr");
        Path report = runDirectory.resolve("summary.json");
        Path metadata = runDirectory.resolve("run.json");
        Path profile = runDirectory.resolve("profile.json");
        Path agentJar = SelfJar.locate();
        String javaToolOptions = AgentInjection.append(System.getenv("JAVA_TOOL_OPTIONS"), agentJar, recording);

        List<String> command = new ArrayList<>(target.command());
        command.addAll(options.forwardedArgs());
        printPlan(target, runDirectory, command, javaToolOptions, discovery);
        if (options.dryRun()) {
            return 0;
        }

        Files.createDirectories(runDirectory);
        if (options.scan()) {
            try {
                System.out.println("Preflight is scanning the enabled mod profile...");
                ProfileCensus.Result census = ProfileCensus.scan(target.installRoot());
                Files.writeString(profile, census.toJson() + System.lineSeparator());
                System.out.println("Preflight profile: " + profile);
            } catch (Exception error) {
                System.err.println("Preflight profile scan skipped: " + error.getMessage());
            }
        }

        Path recordedProfile = Files.isRegularFile(profile) ? profile : null;
        Instant started = Instant.now();
        writeMetadata(metadata, target, command, started, null, null, recordedProfile);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(target.workingDirectory().toFile());
        builder.environment().put("JAVA_TOOL_OPTIONS", javaToolOptions);
        builder.environment().put("PREFLIGHT_RUN_DIR", runDirectory.toString());
        builder.inheritIO();

        int exitCode = builder.start().waitFor();
        Instant ended = Instant.now();
        writeMetadata(metadata, target, command, started, ended, exitCode, recordedProfile);

        if (options.summarize() && Files.isRegularFile(recording)) {
            PreflightCli.summarize(recording, report);
            System.out.println("Preflight report: " + report);
        } else if (!Files.exists(recording)) {
            System.err.println("Preflight recording was not created. Run `doctor` and inspect the selected launcher.");
        }
        return exitCode;
    }

    static int doctor(CommandLine options) throws IOException {
        DiscoveryResult discovery = StarsectorDiscovery.discover(
                Platform.current(),
                Path.of(System.getProperty("user.home")),
                Path.of(System.getProperty("user.dir")),
                System.getenv(),
                options.game(),
                options.launcher());
        printDiscovery(discovery);
        return discovery.selected() == null ? 3 : 0;
    }

    private static void printPlan(
            LaunchTarget target,
            Path runDirectory,
            List<String> command,
            String javaToolOptions,
            DiscoveryResult discovery) {
        System.out.println("Preflight selected:");
        System.out.println("  install:  " + target.installRoot());
        System.out.println("  launcher: " + target.launcher());
        System.out.println("  kind:     " + target.kind());
        System.out.println("  run data: " + runDirectory);
        System.out.println("  command:  " + renderCommand(command));
        System.out.println("  JAVA_TOOL_OPTIONS: " + javaToolOptions);
        for (String diagnostic : discovery.diagnostics()) {
            System.out.println("  note: " + diagnostic);
        }
    }

    private static void printDiscovery(DiscoveryResult discovery) {
        System.out.println("Starsector Preflight doctor");
        if (discovery.selected() != null) {
            System.out.println("Selected: " + discovery.selected().launcher());
        }
        if (discovery.candidates().isEmpty()) {
            System.out.println("Candidates: none");
        }
        for (LaunchTarget target : discovery.candidates()) {
            System.out.println("Candidate score=" + target.score()
                    + " kind=" + target.kind()
                    + " path=" + target.launcher()
                    + " source=" + target.source());
        }
        for (String diagnostic : discovery.diagnostics()) {
            System.out.println("Note: " + diagnostic);
        }
    }

    private static String renderCommand(List<String> command) {
        return command.stream()
                .map(RunCommand::displayQuote)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static String displayQuote(String value) {
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return '"' + value.replace("\"", "\\\"") + '"';
        }
        return value;
    }

    private static void writeMetadata(
            Path path,
            LaunchTarget target,
            List<String> command,
            Instant started,
            Instant ended,
            Integer exitCode,
            Path profile) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("started", started);
        values.put("ended", ended);
        values.put("exitCode", exitCode);
        values.put("platform", Platform.current());
        values.put("javaVersion", System.getProperty("java.version"));
        values.put("installRoot", target.installRoot());
        values.put("launcher", target.launcher());
        values.put("launcherKind", target.kind());
        values.put("command", renderCommand(command));
        values.put("profile", profile);
        Files.writeString(path, Json.object(values) + System.lineSeparator());
    }
}
