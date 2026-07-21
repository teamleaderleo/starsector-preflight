package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class BenchmarkCollectedRunComparisonTest {
    private static final String PROFILE = "ab".repeat(32);
    private static final String PREFLIGHT = "cd".repeat(32);
    private static final String MANIFEST = "de".repeat(32);
    private static final String INDEX = "ef".repeat(32);

    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesStableCollectedRunsAndRetainsFailures() throws Exception {
        Path offOne = write(record("off-1", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null));
        Path offTwo = write(record("off-2", BenchmarkScenarioMode.OFF_WARM, 2, 12_000, 0, null));
        Path enabledOne = write(record(
                "enabled-1", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000, 0, enabledTexture()));
        Path enabledTwo = write(record(
                "enabled-2", BenchmarkScenarioMode.ENABLED_WARM_HIT, 2, 9_000, 0, enabledTexture()));
        Path enabledFailed = write(record(
                "enabled-failed", BenchmarkScenarioMode.ENABLED_WARM_HIT, 3, 6_000, 7, enabledTexture()));
        Path output = temporaryDirectory.resolve("campaign.json");

        assertEquals(0, PreflightCli.run(new String[] {
                "benchmark", "compare-runs",
                offOne.toString(), offTwo.toString(),
                enabledOne.toString(), enabledTwo.toString(), enabledFailed.toString(),
                "--output", output.toString()
        }));

        String json = Files.readString(output);
        assertTrue(json.contains("\"schema\":\"" + BenchmarkCollectedRunComparison.SCHEMA + "\""), json);
        assertTrue(json.contains("\"totalRuns\":5"), json);
        assertTrue(json.contains("\"successfulRuns\":4"), json);
        assertTrue(json.contains("\"failedRuns\":1"), json);
        assertTrue(json.contains("\"runId\":\"enabled-failed\""), json);
        assertTrue(json.contains("\"off-warm\":{\"totalRuns\":2,\"successfulRuns\":2,\"failedRuns\":0"), json);
        assertTrue(json.contains("\"processToMainMenuMs\":{\"minimum\":10000,\"median\":11000.0,\"maximum\":12000}"), json);
        assertTrue(json.contains("\"enabled-warm-hit\":{\"totalRuns\":3,\"successfulRuns\":2,\"failedRuns\":1"), json);
        assertTrue(json.contains("\"processToMainMenuMs\":{\"minimum\":8000,\"median\":8500.0,\"maximum\":9000}"), json);
        assertTrue(json.contains("\"manifestSha256\":\"" + MANIFEST + "\""), json);
        assertTrue(json.contains("\"source\":{\"path\":"), json);
    }

    @Test
    void rejectsMixedBinaryRuntimeProfileAndLauncherIdentities() throws Exception {
        Map<String, Object> first = record(
                "first", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);

        Map<String, Object> otherBinary = deepCopy(first);
        identity(otherBinary).put("preflightJarSha256", "12".repeat(32));
        assertMismatch(first, otherBinary);

        Map<String, Object> otherWrapper = deepCopy(first);
        runtime(identity(otherWrapper), "wrapperRuntime").put("javaVersion", "17.0.13");
        assertMismatch(first, otherWrapper);

        Map<String, Object> otherChild = deepCopy(first);
        runtime(identity(otherChild), "recordingRuntime").put("jvmVersion", "17.0.13+1");
        assertMismatch(first, otherChild);

        Map<String, Object> otherProfile = deepCopy(first);
        identity(otherProfile).put("profileFingerprint", "34".repeat(32));
        scenarioIdentity(otherProfile).put("profileFingerprint", "34".repeat(32));
        assertMismatch(first, otherProfile);

        Map<String, Object> otherLauncher = deepCopy(first);
        identity(otherLauncher).put("launcherKind", "windows-script");
        assertMismatch(first, otherLauncher);
    }

    @Test
    void rejectsTextureDriftWithinOneMode() throws Exception {
        Map<String, Object> first = record(
                "enabled-1", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000, 0, enabledTexture());
        Map<String, Object> second = record(
                "enabled-2", BenchmarkScenarioMode.ENABLED_WARM_HIT, 2, 9_000, 0, enabledTexture());
        texture(second).put("manifestSha256", "11".repeat(32));

        assertComparisonRejected(first, second);
    }

    @Test
    void rejectsEnabledRunsWithoutExactArtifactHashes() throws Exception {
        Map<String, Object> missing = enabledTexture();
        missing.put("manifestSha256", null);
        Path file = write(record(
                "enabled", BenchmarkScenarioMode.ENABLED_WARM_HIT, 1, 8_000, 0, missing));

        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.read(file));
    }

    @Test
    void rejectsDuplicateRunIdsAndModeIterations() throws Exception {
        Map<String, Object> first = record(
                "duplicate", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);
        Map<String, Object> duplicateId = record(
                "duplicate", BenchmarkScenarioMode.OFF_WARM, 2, 11_000, 0, null);
        assertComparisonRejected(first, duplicateId);

        Map<String, Object> duplicateIteration = record(
                "other", BenchmarkScenarioMode.OFF_WARM, 1, 11_000, 0, null);
        assertComparisonRejected(first, duplicateIteration);
    }

    @Test
    void rejectsUnexpectedCollectedRunFields() throws Exception {
        Map<String, Object> value = record(
                "extra-field", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);
        value.put("untrusted", true);
        Path file = write(value);

        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.read(file));
    }

    @Test
    void revalidatesFinalizedRunIntervalAndOutcome() throws Exception {
        Map<String, Object> outside = record(
                "outside", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);
        run(outside).put("ended", "2026-07-21T10:00:05Z");
        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.read(write(outside)));

        Map<String, Object> running = record(
                "running", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);
        run(running).put("outcome", "RUNNING");
        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.read(write(running)));
    }

    @Test
    void rejectsAdapterEvidenceOnOffRuns() throws Exception {
        Map<String, Object> off = record(
                "off-adapter", BenchmarkScenarioMode.OFF_WARM, 1, 10_000, 0, null);
        off.put("adapter", Map.of("transformationsApplied", 0L));
        files(off).put("adapter", fileIdentity("adapter.json", "04"));

        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.read(write(off)));
    }

    private void assertMismatch(Map<String, Object> first, Map<String, Object> second) throws Exception {
        assertComparisonRejected(first, second);
    }

    private void assertComparisonRejected(Map<String, Object> first, Map<String, Object> second) throws Exception {
        Path firstFile = write(first);
        Path secondFile = write(second);
        assertThrows(IllegalArgumentException.class, () -> BenchmarkCollectedRunComparison.compare(List.of(
                BenchmarkCollectedRunComparison.read(firstFile),
                BenchmarkCollectedRunComparison.read(secondFile))));
    }

    private Path write(Map<String, Object> record) throws Exception {
        Path output = temporaryDirectory.resolve(
                scenarioIdentity(record).get("runId") + "-" + System.nanoTime() + ".json");
        Files.writeString(output, Json.object(record));
        return output;
    }

    private static Map<String, Object> record(
            String runId,
            BenchmarkScenarioMode mode,
            int iteration,
            long mainMenuMillis,
            int exitCode,
            Map<String, Object> texture) {
        BenchmarkScenarioResult scenario = scenario(runId, mode, iteration, mainMenuMillis, exitCode);
        boolean enabled = mode.name().startsWith("ENABLED_");

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("scenario", scenario.scenario());
        identity.put("profileFingerprint", PROFILE);
        identity.put("preflightJarSha256", PREFLIGHT);
        identity.put("wrapperRuntime", new LinkedHashMap<>(Map.of(
                "javaVersion", "17.0.12",
                "javaVmName", "Synthetic Wrapper VM",
                "osArch", "x86_64")));
        identity.put("recordingRuntime", new LinkedHashMap<>(Map.of(
                "jvmName", "Synthetic Game VM",
                "jvmVersion", "17.0.12+7",
                "osVersion", "Synthetic OS",
                "cpu", "Synthetic CPU")));
        identity.put("launcherKind", "shell-script");
        identity.put("texture", texture == null ? offTexture() : new LinkedHashMap<>(texture));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("directory", "/runs/" + runId);
        run.put("started", "2026-07-21T10:00:00Z");
        run.put("ended", "2026-07-21T10:02:00Z");
        run.put("exitCode", exitCode);
        run.put("launcherExitCode", exitCode);
        run.put("outcome", exitCode == 0 ? "COMPLETED" : "LAUNCHER_EXIT_NONZERO");
        run.put("executionFailure", null);
        run.put("postprocessingFailures", List.of());
        run.put("adapterMode", enabled ? "ENABLED" : "OFF");
        run.put("textureAuto", enabled);

        Map<String, Object> files = new LinkedHashMap<>();
        files.put("run", fileIdentity("run.json", "01"));
        files.put("profile", fileIdentity("profile.json", "02"));
        files.put("summary", fileIdentity("summary.json", "03"));
        files.put("adapter", enabled ? fileIdentity("adapter.json", "04") : null);
        files.put("scenario", fileIdentity("/scenario.json", "05"));

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
            long mainMenuMillis,
            int exitCode) {
        Instant start = Instant.parse("2026-07-21T10:00:01Z");
        Instant mainMenu = start.plusMillis(mainMenuMillis);
        return new BenchmarkScenarioResult(
                runId,
                "campaign-combat-v1",
                mode,
                iteration,
                PROFILE,
                start,
                mainMenu,
                mainMenu.plusSeconds(30),
                mainMenu.plusSeconds(50),
                exitCode,
                Map.of(),
                Map.of(),
                exitCode == 0 ? List.of() : List.of("synthetic-failure"));
    }

    private static Map<String, Object> enabledTexture() {
        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("mode", "COMPATIBILITY");
        texture.put("profileFingerprint", PROFILE);
        texture.put("manifestSha256", MANIFEST);
        texture.put("indexSha256", INDEX);
        return texture;
    }

    private static Map<String, Object> offTexture() {
        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("mode", "COMPATIBILITY");
        texture.put("profileFingerprint", null);
        texture.put("manifestSha256", null);
        texture.put("indexSha256", null);
        return texture;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> identity(Map<String, Object> root) {
        return (Map<String, Object>) root.get("identity");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runtime(Map<String, Object> identity, String field) {
        return (Map<String, Object>) identity.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> texture(Map<String, Object> root) {
        return (Map<String, Object>) identity(root).get("texture");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(Map<String, Object> root) {
        return (Map<String, Object>) root.get("run");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> files(Map<String, Object> root) {
        return (Map<String, Object>) root.get("files");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scenarioIdentity(Map<String, Object> root) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("scenario")).get("identity");
    }

    private static Map<String, Object> deepCopy(Map<String, Object> root) {
        return StrictJson.object(Json.object(root));
    }
}
