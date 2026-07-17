package dev.starsector.preflight.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Versioned, deterministic result for one manually driven startup benchmark scenario. */
public record BenchmarkScenarioResult(
        String runId,
        String scenario,
        BenchmarkScenarioMode mode,
        int iteration,
        String profileFingerprint,
        Instant processStarted,
        Instant mainMenuReady,
        Instant campaignReady,
        Instant firstCombatReady,
        int exitCode,
        Map<String, Long> adapterCounters,
        Map<String, Long> cacheCounters,
        List<String> disableReasons) {
    public static final int VERSION = 1;
    public static final int MAX_COUNTERS_PER_DOMAIN = 64;
    public static final int MAX_DISABLE_REASONS = 16;

    public BenchmarkScenarioResult {
        runId = requiredText(runId, "runId", 128);
        scenario = requiredText(scenario, "scenario", 128);
        mode = Objects.requireNonNull(mode, "mode");
        if (iteration < 1 || iteration > 1_000_000) {
            throw new IllegalArgumentException("iteration must be between 1 and 1,000,000");
        }
        profileFingerprint = optionalFingerprint(profileFingerprint);
        processStarted = Objects.requireNonNull(processStarted, "processStarted");
        mainMenuReady = Objects.requireNonNull(mainMenuReady, "mainMenuReady");
        campaignReady = Objects.requireNonNull(campaignReady, "campaignReady");
        firstCombatReady = Objects.requireNonNull(firstCombatReady, "firstCombatReady");
        requireOrdered(processStarted, mainMenuReady, "mainMenuReady");
        requireOrdered(mainMenuReady, campaignReady, "campaignReady");
        requireOrdered(campaignReady, firstCombatReady, "firstCombatReady");
        if (exitCode < 0) {
            throw new IllegalArgumentException("exitCode must be non-negative");
        }
        adapterCounters = normalizeCounters(adapterCounters, "adapterCounters");
        cacheCounters = normalizeCounters(cacheCounters, "cacheCounters");
        disableReasons = normalizeReasons(disableReasons);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("runId", runId);
        identity.put("scenario", scenario);
        identity.put("mode", mode);
        identity.put("iteration", iteration);
        identity.put("profileFingerprint", profileFingerprint);

        Map<String, Object> milestones = new LinkedHashMap<>();
        milestones.put("processStarted", processStarted);
        milestones.put("mainMenuReady", mainMenuReady);
        milestones.put("campaignReady", campaignReady);
        milestones.put("firstCombatReady", firstCombatReady);

        Map<String, Object> durations = new LinkedHashMap<>();
        durations.put("processToMainMenuMs", millis(processStarted, mainMenuReady));
        durations.put("mainMenuToCampaignReadyMs", millis(mainMenuReady, campaignReady));
        durations.put("campaignToFirstCombatReadyMs", millis(campaignReady, firstCombatReady));
        durations.put("processToCampaignReadyMs", millis(processStarted, campaignReady));
        durations.put("processToFirstCombatReadyMs", millis(processStarted, firstCombatReady));

        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("adapterCounters", adapterCounters);
        telemetry.put("cacheCounters", cacheCounters);
        telemetry.put("disableReasons", disableReasons);

        Map<String, Object> exit = new LinkedHashMap<>();
        exit.put("code", exitCode);
        exit.put("successful", exitCode == 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", "starsector-preflight-benchmark-scenario");
        result.put("version", VERSION);
        result.put("identity", identity);
        result.put("milestones", milestones);
        result.put("durationsMs", durations);
        result.put("telemetry", telemetry);
        result.put("exit", exit);
        return Collections.unmodifiableMap(result);
    }

    public String toJson() {
        return Json.object(toMap());
    }

    private static String requiredText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be present");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalFingerprint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("profileFingerprint must be a 64-character SHA-256 value");
        }
        return normalized;
    }

    private static void requireOrdered(Instant earlier, Instant later, String laterName) {
        if (later.isBefore(earlier)) {
            throw new IllegalArgumentException(laterName + " must be at or after the preceding milestone");
        }
    }

    private static Map<String, Long> normalizeCounters(Map<String, Long> counters, String name) {
        Map<String, Long> input = counters == null ? Map.of() : counters;
        if (input.size() > MAX_COUNTERS_PER_DOMAIN) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_COUNTERS_PER_DOMAIN + " entries");
        }
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, Long> entry : input.entrySet()) {
            String counter = requiredText(entry.getKey(), name + " key", 64);
            if (!counter.matches("[a-z][a-z0-9_.-]*")) {
                throw new IllegalArgumentException("Invalid " + name + " key: " + counter);
            }
            Long value = Objects.requireNonNull(entry.getValue(), name + " value");
            if (value < 0) {
                throw new IllegalArgumentException(name + " values must be non-negative");
            }
            if (sorted.put(counter, value) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " key: " + counter);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static List<String> normalizeReasons(List<String> reasons) {
        List<String> input = reasons == null ? List.of() : reasons;
        if (input.size() > MAX_DISABLE_REASONS) {
            throw new IllegalArgumentException("disableReasons exceeds " + MAX_DISABLE_REASONS + " entries");
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String reason : input) {
            sorted.add(requiredText(reason, "disableReason", 128));
        }
        return Collections.unmodifiableList(new ArrayList<>(sorted));
    }

    private static long millis(Instant start, Instant end) {
        return Duration.between(start, end).toMillis();
    }
}
