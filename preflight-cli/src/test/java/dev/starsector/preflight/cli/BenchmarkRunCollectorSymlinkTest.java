package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunCollectorSymlinkTest {
    private static final String PROFILE = "ab".repeat(32);
    private static final String PREFLIGHT = "cd".repeat(32);
    private static final String MANIFEST = "de".repeat(32);
    private static final String INDEX = "ef".repeat(32);

    @TempDir
    Path temporaryDirectory;

    @Test
    void collectsEnabledRunThroughSafeDirectorySymlinkAlias() throws Exception {
        Path realRun = temporaryDirectory.resolve("real-run");
        Files.createDirectories(realRun);
        Path alias = createDirectorySymlinkOrSkip(
                temporaryDirectory.resolve("run-alias"),
                realRun);
        Path scenario = temporaryDirectory.resolve("scenario.json");

        Instant started = Instant.parse("2026-07-21T10:00:00Z");
        Instant process = started.plusSeconds(1);
        Instant mainMenu = process.plusSeconds(15);
        Instant campaign = mainMenu.plusSeconds(45);
        Instant combat = campaign.plusSeconds(30);
        Instant ended = combat.plusSeconds(1);

        Path recordedProfile = alias.resolve("profile.json").toAbsolutePath().normalize();
        Path recordedAdapter = alias.resolve("adapter.json").toAbsolutePath().normalize();

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("started", started);
        run.put("ended", ended);
        run.put("exitCode", 0L);
        run.put("launcherExitCode", 0L);
        run.put("outcome", "COMPLETED");
        run.put("executionFailure", null);
        run.put("postprocessingFailures", List.of());
        run.put("profile", recordedProfile);
        run.put("adapterMode", "ENABLED");
        run.put("adapterReport", recordedAdapter);
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
        Files.writeString(realRun.resolve("run.json"), Json.object(run));

        Files.writeString(realRun.resolve("profile.json"), Json.object(Map.of(
                "profileFingerprint", PROFILE,
                "enabledModIds", List.of("example"))));
        Files.writeString(realRun.resolve("summary.json"), Json.object(summary()));
        Files.writeString(realRun.resolve("adapter.json"), Json.object(adapter()));

        BenchmarkScenarioResult result = new BenchmarkScenarioResult(
                "alias-enabled-1",
                "campaign-combat-v1",
                BenchmarkScenarioMode.ENABLED_WARM_HIT,
                1,
                PROFILE,
                process,
                mainMenu,
                campaign,
                combat,
                0,
                Map.of(),
                Map.of(),
                List.of());
        Files.writeString(scenario, result.toJson());

        Map<String, Object> collected = BenchmarkRunCollector.collect(alias, scenario);
        String json = Json.object(collected);
        assertEquals(BenchmarkRunCollector.SCHEMA, collected.get("schema"));
        assertTrue(json.contains("\"directory\":\"" + escaped(realRun.toRealPath().toString()) + "\""), json);
        assertTrue(json.contains("\"traceDurationMs\":90000"), json);
        assertTrue(json.contains("\"runId\":\"alias-enabled-1\""), json);
    }

    private static Map<String, Object> summary() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("scope", JfrRuntimeIdentity.SCOPE);
        identity.put("complete", true);
        identity.put("comparisonIdentity", Map.of(
                "jvmName", "Synthetic Game VM",
                "jvmVersion", "17.0.12+7",
                "osVersion", "Synthetic OS",
                "cpu", "Synthetic CPU"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceStart", "2026-07-21T10:00:01Z");
        summary.put("traceEnd", "2026-07-21T10:01:31Z");
        summary.put("traceDurationMs", 90_000L);
        summary.put("recordingRuntimeIdentity", identity);
        return summary;
    }

    private static Map<String, Object> adapter() {
        Map<String, Object> adapter = new LinkedHashMap<>();
        adapter.put("mode", "ENABLED");
        adapter.put("transformerInstalled", true);
        adapter.put("transformationsApplied", 1L);
        adapter.put("containedFailures", 0L);
        adapter.put("textureCompatibility", Map.of(
                "attempts", 10L,
                "hits", 10L,
                "misses", 0L,
                "fallbacks", 0L));
        return adapter;
    }

    private static Path createDirectorySymlinkOrSkip(Path link, Path target) throws IOException {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException error) {
            assumeTrue(false, "Symbolic links are unavailable: " + error.getMessage());
            return link;
        }
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
