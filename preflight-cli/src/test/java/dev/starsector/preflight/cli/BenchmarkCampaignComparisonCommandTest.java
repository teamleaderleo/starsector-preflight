package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkCampaignComparisonCommandTest {
    private static final String PROFILE = "ab".repeat(32);
    private static final String PREFLIGHT = "cd".repeat(32);
    private static final String MANIFEST = "de".repeat(32);
    private static final String INDEX = "ef".repeat(32);

    @TempDir
    Path temporaryDirectory;

    @Test
    void cliEmitsVersionTwoPrimaryComparison() throws Exception {
        Path offOne = write(record("off-1", BenchmarkScenarioMode.OFF_WARM, 1, 10_000));
        Path offTwo = write(record("off-2", BenchmarkScenarioMode.OFF_WARM, 2, 12_000));
        Path enabledOne = write(record("enabled-1", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000));
        Path enabledTwo = write(record("enabled-2", BenchmarkScenarioMode.ENABLED_WARM_HIT, 2, 9_000));
        Path output = temporaryDirectory.resolve("campaign.json");

        assertEquals(0, PreflightCli.run(new String[] {
                "benchmark", "compare-runs",
                offOne.toString(), offTwo.toString(),
                enabledOne.toString(), enabledTwo.toString(),
                "--output", output.toString()
        }));

        String json = Files.readString(output);
        assertTrue(json.contains("\"version\":2"), json);
        assertTrue(json.contains("\"primaryComparison\":{"), json);
        assertTrue(json.contains("\"baselineMode\":\"off-warm\""), json);
        assertTrue(json.contains("\"candidateMode\":\"enabled-warm-hit\""), json);
        assertTrue(json.contains("\"baselineMedianMs\":11000.0"), json);
        assertTrue(json.contains("\"candidateMedianMs\":8500.0"), json);
        assertTrue(json.contains("\"deltaMs\":-2500.0"), json);
        assertTrue(json.contains("\"improvementPercent\":22.727"), json);
    }

    private Path write(Map<String, Object> value) throws Exception {
        String runId = (String) scenarioIdentity(value).get("runId");
        Path output = temporaryDirectory.resolve(runId + ".json");
        Files.writeString(output, Json.object(value));
        return output;
    }

    private static Map<String, Object> record(
            String runId,
            BenchmarkScenarioMode mode,
            int iteration,
            long processToMainMenuMs) {
        BenchmarkScenarioResult scenario = scenario(runId, mode, iteration, processToMainMenuMs);
        boolean enabled = mode == BenchmarkScenarioMode.ENABLED_WARM_HIT;

        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("mode", "COMPATIBILITY");
        texture.put("profileFingerprint", enabled ? PROFILE : null);
        texture.put("manifestSha256", enabled ? MANIFEST : null);
        texture.put("indexSha256", enabled ? INDEX : null);

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("scenario", scenario.scenario());
        identity.put("profileFingerprint", PROFILE);
        identity.put("preflightJarSha256", PREFLIGHT);
        identity.put("wrapperRuntime", Map.of(
                "javaVersion", "17.0.12",
                "javaVmName", "Synthetic Wrapper VM",
                "osArch", "x86_64"));
        identity.put("recordingRuntime", Map.of(
                "jvmName", "Synthetic Game VM",
                "jvmVersion", "17.0.12+7",
                "osVersion", "Synthetic OS",
                "cpu", "Synthetic CPU"));
        identity.put("launcherKind", "shell-script");
        identity.put("texture", texture);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("directory", "/runs/" + runId);
        run.put("started", "2026-07-21T10:00:00Z");
        run.put("ended", "2026-07-21T10:02:00Z");
        run.put("exitCode", 0L);
        run.put("launcherExitCode", 0L);
        run.put("outcome", "COMPLETED");
        run.put("executionFailure", null);
        run.put("postprocessingFailures", List.of());
        run.put("adapterMode", enabled ? "ENABLED" : "OFF");
        run.put("textureAuto", enabled);

        Map<String, Object> files = new LinkedHashMap<>();
        files.put("run", fileIdentity("run.json", "01"));
        files.put("profile", fileIdentity("profile.json", "02"));
        files.put("summary", fileIdentity("summary.json", "03"));
        files.put("adapter", enabled ? fileIdentity("adapter.json", "04") : null);
        files.put("scenario", fileIdentity("scenario.json", "05"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", BenchmarkRunCollector.SCHEMA);
        root.put("version", BenchmarkRunCollector.VERSION);
        root.put("identity", identity);
        root.put("scenario", scenario.toMap());
        root.put("run", run);
        root.put("summary", Map.of("traceDurationMs", 100_000L));
        root.put("adapter", enabled ? Map.of("transformationsApplied", 1L) : null);
        root.put("files", files);
        return root;
    }

    private static Map<String, Object> fileIdentity(String path, String hashByte) {
        return Map.of(
                "path", path,
                "bytes", 100L,
                "sha256", hashByte.repeat(32));
    }

    private static BenchmarkScenarioResult scenario(
            String runId,
            BenchmarkScenarioMode mode,
            int iteration,
            long processToMainMenuMs) {
        Instant process = Instant.parse("2026-07-21T10:00:01Z");
        Instant mainMenu = process.plusMillis(processToMainMenuMs);
        return new BenchmarkScenarioResult(
                runId,
                "campaign-combat-v1",
                mode,
                iteration,
                PROFILE,
                process,
                mainMenu,
                mainMenu.plusSeconds(30),
                mainMenu.plusSeconds(50),
                0,
                Map.of(),
                Map.of(),
                List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scenarioIdentity(Map<String, Object> root) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("scenario")).get("identity");
    }
}
