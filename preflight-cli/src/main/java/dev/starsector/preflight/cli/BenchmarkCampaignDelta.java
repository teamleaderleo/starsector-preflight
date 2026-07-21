package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Direct median comparison for the primary warm OFF-versus-ENABLED campaign modes. */
final class BenchmarkCampaignDelta {
    static final int CAMPAIGN_MINIMUM_SUCCESSFUL_RUNS_PER_MODE = 5;

    private static final BenchmarkScenarioMode BASELINE_MODE = BenchmarkScenarioMode.OFF_WARM;
    private static final BenchmarkScenarioMode CANDIDATE_MODE = BenchmarkScenarioMode.ENABLED_WARM_HIT;

    private BenchmarkCampaignDelta() {
    }

    static Map<String, Object> compare(
            List<BenchmarkCollectedRunComparison.CollectedRun> collectedRuns) {
        List<BenchmarkScenarioResult> baseline = successful(collectedRuns, BASELINE_MODE);
        List<BenchmarkScenarioResult> candidate = successful(collectedRuns, CANDIDATE_MODE);
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return null;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("processToMainMenuMs", metric(
                baseline,
                candidate,
                result -> millis(result.processStarted(), result.mainMenuReady())));
        metrics.put("mainMenuToCampaignReadyMs", metric(
                baseline,
                candidate,
                result -> millis(result.mainMenuReady(), result.campaignReady())));
        metrics.put("campaignToFirstCombatReadyMs", metric(
                baseline,
                candidate,
                result -> millis(result.campaignReady(), result.firstCombatReady())));
        metrics.put("processToCampaignReadyMs", metric(
                baseline,
                candidate,
                result -> millis(result.processStarted(), result.campaignReady())));
        metrics.put("processToFirstCombatReadyMs", metric(
                baseline,
                candidate,
                result -> millis(result.processStarted(), result.firstCombatReady())));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("baselineMode", BASELINE_MODE.toString());
        output.put("candidateMode", CANDIDATE_MODE.toString());
        output.put("baselineSuccessfulRuns", baseline.size());
        output.put("candidateSuccessfulRuns", candidate.size());
        output.put("campaignMinimumSuccessfulRunsPerMode", CAMPAIGN_MINIMUM_SUCCESSFUL_RUNS_PER_MODE);
        output.put("campaignMinimumMet",
                baseline.size() >= CAMPAIGN_MINIMUM_SUCCESSFUL_RUNS_PER_MODE
                        && candidate.size() >= CAMPAIGN_MINIMUM_SUCCESSFUL_RUNS_PER_MODE);
        output.put("deltaConvention", "candidate-minus-baseline");
        output.put("improvementConvention", "positive-means-candidate-lower");
        output.put("metrics", Collections.unmodifiableMap(metrics));
        return Collections.unmodifiableMap(output);
    }

    private static List<BenchmarkScenarioResult> successful(
            List<BenchmarkCollectedRunComparison.CollectedRun> runs,
            BenchmarkScenarioMode mode) {
        return runs.stream()
                .map(BenchmarkCollectedRunComparison.CollectedRun::scenario)
                .filter(result -> result.mode() == mode && result.exitCode() == 0)
                .toList();
    }

    private static Map<String, Object> metric(
            List<BenchmarkScenarioResult> baseline,
            List<BenchmarkScenarioResult> candidate,
            Metric metric) {
        BigDecimal baselineMedian = median(baseline, metric);
        BigDecimal candidateMedian = median(candidate, metric);
        BigDecimal delta = candidateMedian.subtract(baselineMedian);
        BigDecimal improvement = baselineMedian.signum() == 0
                ? null
                : baselineMedian.subtract(candidateMedian)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(baselineMedian, 3, RoundingMode.HALF_UP);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("baselineMedianMs", baselineMedian);
        output.put("candidateMedianMs", candidateMedian);
        output.put("deltaMs", delta);
        output.put("improvementPercent", improvement);
        return Collections.unmodifiableMap(output);
    }

    private static BigDecimal median(List<BenchmarkScenarioResult> results, Metric metric) {
        List<Long> values = new ArrayList<>(results.stream().map(metric::value).toList());
        values.sort(Long::compareTo);
        int middle = values.size() / 2;
        if ((values.size() & 1) == 1) {
            return BigDecimal.valueOf(values.get(middle)).setScale(1);
        }
        return BigDecimal.valueOf(values.get(middle - 1))
                .add(BigDecimal.valueOf(values.get(middle)))
                .divide(BigDecimal.valueOf(2))
                .setScale(1);
    }

    private static long millis(java.time.Instant start, java.time.Instant end) {
        return Duration.between(start, end).toMillis();
    }

    @FunctionalInterface
    private interface Metric {
        long value(BenchmarkScenarioResult result);
    }
}
