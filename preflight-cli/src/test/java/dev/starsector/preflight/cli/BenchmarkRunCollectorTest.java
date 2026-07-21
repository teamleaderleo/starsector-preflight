package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunCollectorTest {
    private static final String PROFILE = "ab".repeat(32);
    private static final String PREFLIGHT = "cd".repeat(32);
    private static final String MANIFEST = "de".repeat(32);
    private static final String INDEX = "ef".repeat(32);

    @TempDir
    Path temporaryDirectory;

    @Test
    void collectsFinalizedEnabledRunWithExactEvidenceHashes() throws Exception {
        Fixture fixture = fixture(BenchmarkScenarioMode.ENABLED_WARM_HIT, "ENABLED", 0, PROFILE);
        Path output = temporaryDirectory.resolve("collected.json");

        assertEquals(0, PreflightCli.run(new String[] {
                "benchmark", "collect", fixture.runDirectory().toString(),
                "--scenario", fixture.scenario().toString(),
                "--output", output.toString()
        }));

        String json = Files.readString(output);
        assertTrue(json.contains("\"schema\":\"" + BenchmarkRunCollector.SCHEMA + "\""), json);
        assertTrue(json.contains("\"version\":1"), json);
        assertTrue(json.contains("\"profileFingerprint\":\"" + PROFILE + "\""), json);
        assertTrue(json.contains("\"preflightJarSha256\":\"" + PREFLIGHT + "\""), json);
        assertTrue(json.contains("\"scope\":\"jfr-recorded-process\""), json);
        assertTrue(json.contains("\"transformationsApplied\":1"), json);
        assertTrue(json.contains("\"hits\":4926"), json);
        assertTrue(json.contains("\"sha256\":\"" + Hashes.sha256(fixture.runDirectory().resolve("run.json")) + "\""), json);
        assertTrue(json.contains("\"runId\":\"enabled-warm-hit-1\""), json);
    }

    @Test
    void rejectsProfileExitAndAdapterModeMismatches() throws Exception {
        Fixture wrongProfile = fixture(BenchmarkScenarioMode.OFF_WARM, "OFF", 0, "12".repeat(32));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                wrongProfile.runDirectory(), wrongProfile.scenario()));

        Fixture wrongExit = fixture(BenchmarkScenarioMode.OFF_WARM, "OFF", 7, PROFILE);
        rewriteScenario(wrongExit, BenchmarkScenarioMode.OFF_WARM, 0, PROFILE);
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                wrongExit.runDirectory(), wrongExit.scenario()));

        Fixture wrongMode = fixture(BenchmarkScenarioMode.ENABLED_WARM_HIT, "OFF", 0, PROFILE);
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                wrongMode.runDirectory(), wrongMode.scenario()));
    }

    @Test
    void requiresAdapterEvidenceForEnabledModesAndCompleteRecordedRuntimeIdentity() throws Exception {
        Fixture missingAdapter = fixture(BenchmarkScenarioMode.ENABLED_WARM_HIT, "ENABLED", 0, PROFILE);
        Files.delete(missingAdapter.runDirectory().resolve("adapter.json"));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                missingAdapter.runDirectory(), missingAdapter.scenario()));

        Fixture incompleteRuntime = fixture(BenchmarkScenarioMode.OFF_WARM, "OFF", 0, PROFILE);
        Map<String, Object> summary = summary(false);
        Files.writeString(incompleteRuntime.runDirectory().resolve("summary.json"), Json.object(summary));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                incompleteRuntime.runDirectory(), incompleteRuntime.scenario()));
    }

    @Test
    void rejectsScenarioMilestonesOutsideTheFinalizedRunWindow() throws Exception {
        Fixture fixture = fixture(BenchmarkScenarioMode.OFF_WARM, "OFF", 0, PROFILE);
        Instant start = Instant.parse("2026-07-21T09:59:59Z");
        BenchmarkScenarioResult outside = new BenchmarkScenarioResult(
                "outside",
                "campaign-combat-v1",
                BenchmarkScenarioMode.OFF_WARM,
                1,
                PROFILE,
                start,
                start.plusSeconds(10),
                start.plusSeconds(20),
                start.plusSeconds(30),
                0,
                Map.of(),
                Map.of(),
                List.of());
        Files.writeString(fixture.scenario(), outside.toJson());

        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunCollector.collect(
                fixture.runDirectory(), fixture.scenario()));
    }

    private Fixture fixture(
            BenchmarkScenarioMode mode,
            String adapterMode,
            int exitCode,
            String scenarioProfile) throws Exception {
        Path runDirectory = temporaryDirectory.resolve(mode + "-" + adapterMode + "-" + System.nanoTime());
        Files.createDirectories(runDirectory);
        Instant started = Instant.parse("2026-07-21T10:00:00Z");
        Instant ended = Instant.parse("2026-07-21T10:02:00Z");

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("started", started);
        run.put("ended", ended);
        run.put("exitCode", exitCode);
        run.put("launcherExitCode", exitCode);
        run.put("outcome", exitCode == 0 ? "COMPLETED" : "LAUNCHER_EXIT_NONZERO");
        run.put("executionFailure", null);
        run.put("postprocessingFailures", List.of());
        run.put("adapterMode", adapterMode);
        run.put("runtimeIdentityScope", RunIdentity.SCOPE);
        run.put("preflightJarSha256", PREFLIGHT);
        run.put("wrapperRuntime", Map.of(
                "javaVersion", "17.0.12",
                "javaVmName", "Synthetic Wrapper VM",
                "osArch", "x86_64"));
        run.put("launcherKind", "shell-script");
        run.put("textureAdapterMode", "COMPATIBILITY");
        run.put("textureProfileFingerprint", PROFILE);
        run.put("textureManifestSha256", MANIFEST);
        run.put("textureIndexSha256", INDEX);
        run.put("textureAuto", true);
        Files.writeString(runDirectory.resolve("run.json"), Json.object(run));
        Files.writeString(runDirectory.resolve("profile.json"), Json.object(Map.of(
                "profileFingerprint", PROFILE,
                "enabledModIds", List.of("example"))));
        Files.writeString(runDirectory.resolve("summary.json"), Json.object(summary(true)));
        if (!"OFF".equals(adapterMode)) {
            Files.writeString(runDirectory.resolve("adapter.json"), Json.object(adapter(adapterMode)));
        }

        Path scenario = temporaryDirectory.resolve("scenario-" + System.nanoTime() + ".json");
        BenchmarkScenarioResult scenarioResult = scenario(
                mode,
                exitCode,
                scenarioProfile,
                mode + "-1");
        Files.writeString(scenario, scenarioResult.toJson());
        return new Fixture(runDirectory, scenario);
    }

    private static Map<String, Object> summary(boolean complete) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("scope", JfrRuntimeIdentity.SCOPE);
        identity.put("complete", complete);
        identity.put("comparisonIdentity", Map.of(
                "jvmName", "Synthetic Game VM",
                "jvmVersion", "17.0.12+7",
                "osVersion", "Synthetic OS",
                "cpu", "Synthetic CPU"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceStart", "2026-07-21T10:00:01Z");
        summary.put("traceEnd", "2026-07-21T10:01:59Z");
        summary.put("traceDurationMs", 118_000L);
        summary.put("fileReadMs", 100.0);
        summary.put("fileReadBytes", 1234L);
        summary.put("fileWriteMs", 5.0);
        summary.put("fileWriteBytes", 99L);
        summary.put("compilationMs", 10.0);
        summary.put("gcPauseMs", 2.0);
        summary.put("executionSamples", 10L);
        summary.put("preflightAgentStartedEvents", 1L);
        summary.put("recordingRuntimeIdentity", identity);
        return summary;
    }

    private static Map<String, Object> adapter(String mode) {
        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("attempts", 4929L);
        texture.put("hits", 4926L);
        texture.put("misses", 3L);
        texture.put("fallbacks", 3L);
        Map<String, Object> adapter = new LinkedHashMap<>();
        adapter.put("mode", mode);
        adapter.put("transformerInstalled", true);
        adapter.put("killSwitchActive", false);
        adapter.put("registryTargets", 1L);
        adapter.put("observedClasses", 100L);
        adapter.put("parsedClasses", 100L);
        adapter.put("malformedClasses", 0L);
        adapter.put("exactMatches", 1L);
        adapter.put("sourceBindingRejected", 0L);
        adapter.put("transformationEligible", 1L);
        adapter.put("transformationDeclined", 0L);
        adapter.put("transformationsApplied", 1L);
        adapter.put("containedFailures", 0L);
        adapter.put("liveTransformationPlansRegistered", true);
        adapter.put("textureCompatibility", texture);
        return adapter;
    }

    private static BenchmarkScenarioResult scenario(
            BenchmarkScenarioMode mode,
            int exitCode,
            String profile,
            String runId) {
        Instant process = Instant.parse("2026-07-21T10:00:01Z");
        return new BenchmarkScenarioResult(
                runId,
                "campaign-combat-v1",
                mode,
                1,
                profile,
                process,
                process.plusSeconds(15),
                process.plusSeconds(60),
                process.plusSeconds(90),
                exitCode,
                Map.of(),
                Map.of(),
                exitCode == 0 ? List.of() : List.of("synthetic-failure"));
    }

    private static void rewriteScenario(
            Fixture fixture,
            BenchmarkScenarioMode mode,
            int exitCode,
            String profile) throws Exception {
        Files.writeString(fixture.scenario(), scenario(mode, exitCode, profile, "rewritten").toJson());
    }

    private record Fixture(Path runDirectory, Path scenario) {
    }
}
