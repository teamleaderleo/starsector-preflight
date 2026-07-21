package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkCampaignDeltaTest {
    @Test
    void reportsWarmMedianDeltaAndPositiveImprovement() {
        List<BenchmarkCollectedRunComparison.CollectedRun> runs = List.of(
                collected(result("off-1", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0)),
                collected(result("off-2", BenchmarkScenarioMode.OFF_WARM, 2, 12_000, 0)),
                collected(result("enabled-1", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000, 0)),
                collected(result("enabled-2", BenchmarkScenarioMode.ENABLED_WARM_HIT, 2, 9_000, 0)),
                collected(result("enabled-failed", BenchmarkScenarioMode.ENABLED_WARM_HIT, 3, 2_000, 7)));

        Map<String, Object> comparison = BenchmarkCampaignDelta.compare(runs);
        assertEquals("off-warm", comparison.get("baselineMode"));
        assertEquals("enabled-warm-hit", comparison.get("candidateMode"));
        assertEquals(2, comparison.get("baselineSuccessfulRuns"));
        assertEquals(2, comparison.get("candidateSuccessfulRuns"));

        Map<String, Object> metric = metric(comparison, "processToMainMenuMs");
        assertEquals(new BigDecimal("11000.0"), metric.get("baselineMedianMs"));
        assertEquals(new BigDecimal("8500.0"), metric.get("candidateMedianMs"));
        assertEquals(new BigDecimal("-2500.0"), metric.get("deltaMs"));
        assertEquals(new BigDecimal("22.727"), metric.get("improvementPercent"));
    }

    @Test
    void omitsPrimaryComparisonUntilBothModesHaveSuccessfulRuns() {
        List<BenchmarkCollectedRunComparison.CollectedRun> runs = List.of(
                collected(result("off", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0)),
                collected(result("enabled-failed", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000, 7)));

        assertNull(BenchmarkCampaignDelta.compare(runs));
    }

    @Test
    void usesNullImprovementWhenBaselineMedianIsZero() {
        List<BenchmarkCollectedRunComparison.CollectedRun> runs = List.of(
                collected(result("off", BenchmarkScenarioMode.OFF_WARM, 1, 0, 0)),
                collected(result("enabled", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 0, 0)));

        Map<String, Object> comparison = BenchmarkCampaignDelta.compare(runs);
        assertNull(metric(comparison, "processToMainMenuMs").get("improvementPercent"));
    }

    private static BenchmarkCollectedRunComparison.CollectedRun collected(BenchmarkScenarioResult scenario) {
        return new BenchmarkCollectedRunComparison.CollectedRun(
                Path.of(scenario.runId() + ".json"),
                Map.of(),
                Map.of(),
                Map.of(),
                scenario,
                Map.of());
    }

    private static BenchmarkScenarioResult result(
            String runId,
            BenchmarkScenarioMode mode,
            int iteration,
            long processToMainMenuMs,
            int exitCode) {
        Instant process = Instant.parse("2026-07-21T10:00:00Z");
        Instant mainMenu = process.plusMillis(processToMainMenuMs);
        Instant campaign = mainMenu.plusSeconds(30);
        Instant combat = campaign.plusSeconds(20);
        return new BenchmarkScenarioResult(
                runId,
                "campaign-combat-v1",
                mode,
                iteration,
                "ab".repeat(32),
                process,
                mainMenu,
                campaign,
                combat,
                exitCode,
                Map.of(),
                Map.of(),
                List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metric(Map<String, Object> comparison, String name) {
        Map<String, Object> metrics = (Map<String, Object>) comparison.get("metrics");
        return (Map<String, Object>) metrics.get(name);
    }
}
