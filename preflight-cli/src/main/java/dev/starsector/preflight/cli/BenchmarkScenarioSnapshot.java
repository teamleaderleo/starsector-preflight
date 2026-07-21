package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict scenario parser for a caller-owned immutable text snapshot. */
final class BenchmarkScenarioSnapshot {
    private static final String SCHEMA = "starsector-preflight-benchmark-scenario";

    private BenchmarkScenarioSnapshot() {
    }

    static BenchmarkScenarioResult parse(Path source, String json) {
        Map<String, Object> root = StrictJson.object(json);
        requireKeys(root, Set.of(
                "schema", "version", "identity", "milestones", "durationsMs", "telemetry", "exit"),
                "result");
        if (!SCHEMA.equals(text(root, "schema"))) {
            throw invalid(source, "unexpected schema");
        }
        if (integer(root, "version") != BenchmarkScenarioResult.VERSION) {
            throw invalid(source, "unsupported version");
        }

        Map<String, Object> identity = object(root, "identity");
        requireKeys(identity, Set.of(
                "runId", "scenario", "mode", "iteration", "profileFingerprint"), "identity");
        Map<String, Object> milestones = object(root, "milestones");
        requireKeys(milestones, Set.of(
                "processStarted", "mainMenuReady", "campaignReady", "firstCombatReady"), "milestones");
        Map<String, Object> telemetry = object(root, "telemetry");
        requireKeys(telemetry, Set.of(
                "adapterCounters", "cacheCounters", "disableReasons"), "telemetry");
        Map<String, Object> exit = object(root, "exit");
        requireKeys(exit, Set.of("code", "successful"), "exit");

        BenchmarkScenarioResult result = new BenchmarkScenarioResult(
                text(identity, "runId"),
                text(identity, "scenario"),
                BenchmarkScenarioMode.parse(text(identity, "mode")),
                integer(identity, "iteration"),
                optionalText(identity, "profileFingerprint"),
                instant(milestones, "processStarted"),
                instant(milestones, "mainMenuReady"),
                instant(milestones, "campaignReady"),
                instant(milestones, "firstCombatReady"),
                integer(exit, "code"),
                counters(telemetry, "adapterCounters"),
                counters(telemetry, "cacheCounters"),
                reasons(telemetry, "disableReasons"));
        if (bool(exit, "successful") != (result.exitCode() == 0)) {
            throw invalid(source, "exit.successful disagrees with exit.code");
        }
        verifyDurations(source, object(root, "durationsMs"), result);
        return result;
    }

    private static void verifyDurations(
            Path source,
            Map<String, Object> durations,
            BenchmarkScenarioResult result) {
        requireKeys(durations, Set.of(
                "processToMainMenuMs",
                "mainMenuToCampaignReadyMs",
                "campaignToFirstCombatReadyMs",
                "processToCampaignReadyMs",
                "processToFirstCombatReadyMs"), "durationsMs");
        verifyDuration(source, durations, "processToMainMenuMs", millis(result.processStarted(), result.mainMenuReady()));
        verifyDuration(source, durations, "mainMenuToCampaignReadyMs", millis(result.mainMenuReady(), result.campaignReady()));
        verifyDuration(source, durations, "campaignToFirstCombatReadyMs", millis(result.campaignReady(), result.firstCombatReady()));
        verifyDuration(source, durations, "processToCampaignReadyMs", millis(result.processStarted(), result.campaignReady()));
        verifyDuration(source, durations, "processToFirstCombatReadyMs", millis(result.processStarted(), result.firstCombatReady()));
    }

    private static void verifyDuration(Path source, Map<String, Object> values, String name, long expected) {
        if (longValue(values, name) != expected) {
            throw invalid(source, "duration " + name + " disagrees with milestones");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String name) {
        Object value = parent.get(name);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected object field " + name);
        }
        return (Map<String, Object>) map;
    }

    private static String text(Map<String, Object> values, String name) {
        String value = optionalText(values, name);
        if (value == null) {
            throw new IllegalArgumentException("Expected string field " + name);
        }
        return value;
    }

    private static String optionalText(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Expected string or null field " + name);
        }
        return text;
    }

    private static int integer(Map<String, Object> values, String name) {
        long value = longValue(values, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer field is out of range: " + name);
        }
        return (int) value;
    }

    private static long longValue(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Long number)) {
            throw new IllegalArgumentException("Expected integer field " + name);
        }
        return number;
    }

    private static boolean bool(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("Expected boolean field " + name);
        }
        return flag;
    }

    private static Instant instant(Map<String, Object> values, String name) {
        try {
            return Instant.parse(text(values, name));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid instant field " + name, error);
        }
    }

    private static Map<String, Long> counters(Map<String, Object> telemetry, String name) {
        Map<String, Object> values = object(telemetry, name);
        Map<String, Long> counters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!(entry.getValue() instanceof Long number)) {
                throw new IllegalArgumentException("Expected integer counter " + name + "." + entry.getKey());
            }
            counters.put(entry.getKey(), number);
        }
        return counters;
    }

    private static List<String> reasons(Map<String, Object> telemetry, String name) {
        Object value = telemetry.get(name);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected array field " + name);
        }
        List<String> reasons = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String reason)) {
                throw new IllegalArgumentException("Expected string entries in " + name);
            }
            reasons.add(reason);
        }
        return reasons;
    }

    private static void requireKeys(Map<String, Object> values, Set<String> expected, String name) {
        if (!values.keySet().equals(expected)) {
            throw new IllegalArgumentException("Unexpected " + name + " fields: " + values.keySet());
        }
    }

    private static IllegalArgumentException invalid(Path source, String reason) {
        return new IllegalArgumentException("Invalid benchmark scenario result " + source + ": " + reason);
    }

    private static long millis(Instant start, Instant end) {
        return Duration.between(start, end).toMillis();
    }
}
