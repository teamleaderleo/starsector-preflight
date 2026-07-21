package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkScenarioComparisonTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsEveryRunAndSummarizesOnlySuccessfulRuns() throws Exception {
        String profile = "ab".repeat(32);
        Path offOne = write(result("off-1", BenchmarkScenarioMode.OFF_WARM, 1, profile, 10_000, 0));
        Path offTwo = write(result("off-2", BenchmarkScenarioMode.OFF_WARM, 2, profile, 12_000, 0));
        Path offFailed = write(result("off-failed", BenchmarkScenarioMode.OFF_WARM, 3, profile, 5_000, 7));
        Path enabledOne = write(result("enabled-1", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, profile, 8_000, 0));
        Path enabledTwo = write(result("enabled-2", BenchmarkScenarioMode.ENABLED_WARM_HIT, 2, profile, 9_000, 0));
        Path comparison = temporaryDirectory.resolve("comparison.json");

        assertEquals(0, PreflightCli.run(new String[] {
                "benchmark", "compare",
                offOne.toString(),
                offTwo.toString(),
                offFailed.toString(),
                enabledOne.toString(),
                enabledTwo.toString(),
                "--output", comparison.toString()
        }));

        String json = Files.readString(comparison);
        assertTrue(json.contains("\"schema\":\"starsector-preflight-benchmark-comparison\""), json);
        assertTrue(json.contains("\"totalRuns\":5"), json);
        assertTrue(json.contains("\"successfulRuns\":4"), json);
        assertTrue(json.contains("\"failedRuns\":1"), json);
        assertTrue(json.contains("\"runId\":\"off-failed\""), json);
        assertTrue(json.contains("\"off-warm\":{\"totalRuns\":3,\"successfulRuns\":2,\"failedRuns\":1"), json);
        assertTrue(json.contains("\"processToMainMenuMs\":{\"minimum\":10000,\"median\":11000.0,\"maximum\":12000}"), json);
        assertTrue(json.contains("\"enabled-warm-hit\":{\"totalRuns\":2,\"successfulRuns\":2,\"failedRuns\":0"), json);
        assertTrue(json.contains("\"processToMainMenuMs\":{\"minimum\":8000,\"median\":8500.0,\"maximum\":9000}"), json);
    }

    @Test
    void rejectsMixedProfilesAndDuplicateModeIterations() throws Exception {
        Path first = write(result("first", BenchmarkScenarioMode.OFF_WARM, 1, "ab".repeat(32), 10_000, 0));
        Path otherProfile = write(result("other", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, "cd".repeat(32), 8_000, 0));
        assertThrows(IllegalArgumentException.class, () -> PreflightCli.run(new String[] {
                "benchmark", "compare", first.toString(), otherProfile.toString()
        }));

        Path duplicate = write(result("duplicate", BenchmarkScenarioMode.OFF_WARM, 1, "ab".repeat(32), 11_000, 0));
        assertThrows(IllegalArgumentException.class, () -> PreflightCli.run(new String[] {
                "benchmark", "compare", first.toString(), duplicate.toString()
        }));
    }

    @Test
    void rejectsDurationsThatDisagreeWithMilestones() throws Exception {
        Path valid = write(result("valid", BenchmarkScenarioMode.OFF_WARM, 1, "ab".repeat(32), 10_000, 0));
        String changed = Files.readString(valid).replace("\"processToMainMenuMs\":10000", "\"processToMainMenuMs\":9999");
        Path tampered = temporaryDirectory.resolve("tampered.json");
        Files.writeString(tampered, changed);

        assertThrows(IllegalArgumentException.class, () -> BenchmarkScenarioComparison.read(tampered));
    }

    private Path write(BenchmarkScenarioResult result) throws Exception {
        Path output = temporaryDirectory.resolve(result.runId() + ".json");
        Files.writeString(output, result.toJson());
        return output;
    }

    private static BenchmarkScenarioResult result(
            String runId,
            BenchmarkScenarioMode mode,
            int iteration,
            String profile,
            long mainMenuMillis,
            int exitCode) {
        Instant start = Instant.parse("2026-07-21T10:00:00Z");
        Instant mainMenu = start.plusMillis(mainMenuMillis);
        Instant campaign = mainMenu.plusSeconds(30);
        Instant combat = campaign.plusSeconds(20);
        return new BenchmarkScenarioResult(
                runId,
                "campaign-combat-v1",
                mode,
                iteration,
                profile,
                start,
                mainMenu,
                campaign,
                combat,
                exitCode,
                Map.of("texture.hits", mode == BenchmarkScenarioMode.ENABLED_WARM_HIT ? 100L : 0L),
                Map.of("entries", 100L),
                exitCode == 0 ? List.of() : List.of("synthetic-failure"));
    }
}
