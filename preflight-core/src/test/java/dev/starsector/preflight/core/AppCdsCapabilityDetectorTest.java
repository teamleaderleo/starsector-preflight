package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class AppCdsCapabilityDetectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    void currentJdkCreatesAndConsumesAProbeArchive() throws Exception {
        Path java = currentJavaExecutable();
        AppCdsCapabilityDetector.Result result = AppCdsCapabilityDetector.detect(
                java,
                temporaryDirectory.resolve("probe"),
                Duration.ofSeconds(20));

        assertEquals(AppCdsCapabilityDetector.Status.SUPPORTED, result.status(), result.detail());
        assertTrue(result.supported());
        assertEquals(0, result.generationExitCode());
        assertEquals(0, result.consumptionExitCode());
        assertFalse(result.outputTruncated());
        assertTrue(result.proofArchiveBytes() > 0);
        assertEquals(64, result.proofArchiveSha256().length());

        Path applicationArchive = temporaryDirectory.resolve("application.jsa");
        assertEquals(
                List.of(
                        AppCdsCapabilityDetector.XSHARE_ON,
                        AppCdsCapabilityDetector.ARCHIVE_AT_EXIT_PREFIX + applicationArchive.toAbsolutePath().normalize()),
                result.archiveCreationArguments(applicationArchive));

        Files.write(applicationArchive, new byte[] {1});
        assertEquals(
                List.of(
                        AppCdsCapabilityDetector.XSHARE_ON,
                        AppCdsCapabilityDetector.SHARED_ARCHIVE_PREFIX + applicationArchive.toRealPath()),
                result.archiveConsumptionArguments(applicationArchive));
    }

    @Test
    void everyFailureStatusContributesNoArchiveFlags() throws Exception {
        Path creationTarget = temporaryDirectory.resolve("new.jsa");
        Path existingArchive = temporaryDirectory.resolve("existing.jsa");
        Files.write(existingArchive, new byte[] {1});

        for (AppCdsCapabilityDetector.Status status : List.of(
                AppCdsCapabilityDetector.Status.UNSUPPORTED,
                AppCdsCapabilityDetector.Status.TIMED_OUT,
                AppCdsCapabilityDetector.Status.ERROR)) {
            AppCdsCapabilityDetector.Result result = new AppCdsCapabilityDetector.Result(
                    status, null, "failure", -1, -1, false, 0, "");
            assertTrue(result.archiveCreationArguments(creationTarget).isEmpty());
            assertTrue(result.archiveConsumptionArguments(existingArchive).isEmpty());
        }
    }

    @Test
    void invalidExecutableAndUnsafeArchiveTargetsFailClosed() throws Exception {
        AppCdsCapabilityDetector.Result missingJava = AppCdsCapabilityDetector.detect(
                temporaryDirectory.resolve("missing-java"),
                temporaryDirectory.resolve("probe"),
                Duration.ofSeconds(1));
        assertEquals(AppCdsCapabilityDetector.Status.ERROR, missingJava.status());
        assertTrue(missingJava.archiveCreationArguments(temporaryDirectory.resolve("unused.jsa")).isEmpty());

        AppCdsCapabilityDetector.Result supported = new AppCdsCapabilityDetector.Result(
                AppCdsCapabilityDetector.Status.SUPPORTED,
                currentJavaExecutable(),
                "synthetic supported result",
                0,
                0,
                false,
                1,
                "0".repeat(64));
        Path directoryTarget = temporaryDirectory.resolve("directory.jsa");
        Files.createDirectory(directoryTarget);
        assertTrue(supported.archiveCreationArguments(directoryTarget).isEmpty());
        assertTrue(supported.archiveConsumptionArguments(temporaryDirectory.resolve("missing.jsa")).isEmpty());
        Path emptyArchive = temporaryDirectory.resolve("empty.jsa");
        Files.createFile(emptyArchive);
        assertTrue(supported.archiveConsumptionArguments(emptyArchive).isEmpty());
    }

    private static Path currentJavaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
