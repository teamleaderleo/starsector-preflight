package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.AppCdsCapabilityDetector;
import dev.starsector.preflight.core.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

/** Separate-process caller for the production AppCDS capability detector. */
public final class SyntheticAppCdsWorker {
    private SyntheticAppCdsWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: SyntheticAppCdsWorker <work-root> <report.json>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(Path workRoot, Path reportPath) throws Exception {
        Files.createDirectories(workRoot);
        Path java = currentJavaExecutable();
        AppCdsCapabilityDetector.Result result = AppCdsCapabilityDetector.detect(
                java,
                workRoot.resolve("supported-probe"),
                Duration.ofSeconds(20));

        Path applicationArchive = workRoot.resolve("application.jsa");
        List<String> creationArguments = result.archiveCreationArguments(applicationArchive);
        Files.write(applicationArchive, new byte[] {1});
        List<String> consumptionArguments = result.archiveConsumptionArguments(applicationArchive);

        AppCdsCapabilityDetector.Result missingExecutable = AppCdsCapabilityDetector.detect(
                workRoot.resolve("missing-java"),
                workRoot.resolve("missing-probe"),
                Duration.ofSeconds(1));

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-appcds-capability-v1");
        report.put("processId", ProcessHandle.current().pid());
        report.put("status", result.status());
        report.put("supported", result.supported());
        report.put("generationExitCode", result.generationExitCode());
        report.put("consumptionExitCode", result.consumptionExitCode());
        report.put("outputTruncated", result.outputTruncated());
        report.put("proofArchiveBytes", result.proofArchiveBytes());
        report.put("proofArchiveSha256", result.proofArchiveSha256());
        report.put("creationArgumentCount", creationArguments.size());
        report.put("creationArguments", creationArguments);
        report.put("consumptionArgumentCount", consumptionArguments.size());
        report.put("consumptionArguments", consumptionArguments);
        report.put("missingExecutableStatus", missingExecutable.status());
        report.put("missingCreationArgumentCount", missingExecutable.archiveCreationArguments(
                workRoot.resolve("never-created.jsa")).size());
        report.put("missingConsumptionArgumentCount", missingExecutable.archiveConsumptionArguments(
                applicationArchive).size());
        report.put("detail", result.detail());
        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, Json.object(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static Path currentJavaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
