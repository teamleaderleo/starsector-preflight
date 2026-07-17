package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkScenarioResultTest {
    @Test
    void serializesDeterministicFixture() {
        BenchmarkScenarioResult result = new BenchmarkScenarioResult(
                "run-20260717-100000",
                "campaign-combat-v1",
                BenchmarkScenarioMode.ENABLED_WARM_HIT,
                2,
                "ab".repeat(32),
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:12Z"),
                Instant.parse("2026-07-17T10:01:02Z"),
                Instant.parse("2026-07-17T10:01:32Z"),
                0,
                Map.of("texture.misses", 1L, "texture.hits", 5L),
                Map.of("entries", 3L, "bytes", 4096L),
                List.of("kill-switch", "identity-mismatch", "kill-switch"));

        assertEquals(
                "{\"schema\":\"starsector-preflight-benchmark-scenario\",\"version\":1,"
                        + "\"identity\":{\"runId\":\"run-20260717-100000\",\"scenario\":\"campaign-combat-v1\","
                        + "\"mode\":\"enabled-warm-hit\",\"iteration\":2,\"profileFingerprint\":\""
                        + "ab".repeat(32)
                        + "\"},"
                        + "\"milestones\":{\"processStarted\":\"2026-07-17T10:00:00Z\","
                        + "\"mainMenuReady\":\"2026-07-17T10:00:12Z\","
                        + "\"campaignReady\":\"2026-07-17T10:01:02Z\","
                        + "\"firstCombatReady\":\"2026-07-17T10:01:32Z\"},"
                        + "\"durationsMs\":{\"processToMainMenuMs\":12000,"
                        + "\"mainMenuToCampaignReadyMs\":50000,"
                        + "\"campaignToFirstCombatReadyMs\":30000,"
                        + "\"processToCampaignReadyMs\":62000,"
                        + "\"processToFirstCombatReadyMs\":92000},"
                        + "\"telemetry\":{\"adapterCounters\":{\"texture.hits\":5,\"texture.misses\":1},"
                        + "\"cacheCounters\":{\"bytes\":4096,\"entries\":3},"
                        + "\"disableReasons\":[\"identity-mismatch\",\"kill-switch\"]},"
                        + "\"exit\":{\"code\":0,\"successful\":true}}",
                result.toJson());
    }

    @Test
    void rejectsMilestonesThatMoveBackward() {
        assertThrows(IllegalArgumentException.class, () -> fixture(
                Instant.parse("2026-07-17T10:00:10Z"),
                Instant.parse("2026-07-17T10:00:09Z"),
                Map.of()));
    }

    @Test
    void boundsCounterCollections() {
        Map<String, Long> counters = new LinkedHashMap<>();
        for (int i = 0; i <= BenchmarkScenarioResult.MAX_COUNTERS_PER_DOMAIN; i++) {
            counters.put("counter." + i, (long) i);
        }
        assertThrows(IllegalArgumentException.class, () -> fixture(
                Instant.parse("2026-07-17T10:00:10Z"),
                Instant.parse("2026-07-17T10:00:20Z"),
                counters));
    }

    private static BenchmarkScenarioResult fixture(
            Instant mainMenu,
            Instant campaign,
            Map<String, Long> counters) {
        return new BenchmarkScenarioResult(
                "run-1",
                "scenario-1",
                BenchmarkScenarioMode.OFF_WARM,
                1,
                null,
                Instant.parse("2026-07-17T10:00:00Z"),
                mainMenu,
                campaign,
                Instant.parse("2026-07-17T10:00:30Z"),
                0,
                counters,
                Map.of(),
                List.of());
    }
}
