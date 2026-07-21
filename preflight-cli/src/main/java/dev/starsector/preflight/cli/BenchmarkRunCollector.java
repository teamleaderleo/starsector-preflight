package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.PathContainment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects one finalized run directory and one scenario record into a comparison-ready record. */
final class BenchmarkRunCollector {
    static final String SCHEMA = "starsector-preflight-benchmark-run";
    static final int VERSION = 1;

    private static final long MAX_RUN_BYTES = 2L * 1024 * 1024;
    private static final long MAX_PROFILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_SUMMARY_BYTES = 32L * 1024 * 1024;
    private static final long MAX_ADAPTER_BYTES = 16L * 1024 * 1024;
    private static final long MAX_SCENARIO_BYTES = 1024L * 1024L;

    private BenchmarkRunCollector() {
    }

    static int execute(String[] args, int offset) throws IOException {
        Options options = parse(args, offset);
        Map<String, Object> record = collect(options.runDirectory(), options.scenario());
        String json = Json.object(record);
        if (options.output() == null) {
            System.out.println(json);
        } else {
            Path output = options.output().toAbsolutePath().normalize();
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, json + System.lineSeparator());
            System.out.println(output);
        }
        return 0;
    }

    static Map<String, Object> collect(Path runDirectory, Path scenarioPath) throws IOException {
        Path runRoot = PathContainment.realDirectory(runDirectory);
        Path runFile = requiredRunFile(runRoot, "run.json");
        Path profileFile = requiredRunFile(runRoot, "profile.json");
        Path summaryFile = requiredRunFile(runRoot, "summary.json");
        Path adapterFile = optionalRunFile(runRoot, "adapter.json");
        Path scenarioFile = scenarioPath.toAbsolutePath().normalize().toRealPath();
        if (!Files.isRegularFile(scenarioFile)) {
            throw new IOException("Expected benchmark scenario evidence: " + scenarioFile);
        }

        JsonSnapshot runSnapshot = readSnapshot(runFile, MAX_RUN_BYTES, "run evidence");
        JsonSnapshot profileSnapshot = readSnapshot(profileFile, MAX_PROFILE_BYTES, "profile evidence");
        JsonSnapshot summarySnapshot = readSnapshot(summaryFile, MAX_SUMMARY_BYTES, "summary evidence");
        JsonSnapshot adapterSnapshot = adapterFile == null
                ? null
                : readSnapshot(adapterFile, MAX_ADAPTER_BYTES, "adapter evidence");
        ScenarioSnapshot scenarioSnapshot = readScenarioSnapshot(scenarioFile);

        Map<String, Object> run = runSnapshot.object();
        Map<String, Object> profile = profileSnapshot.object();
        Map<String, Object> summary = summarySnapshot.object();
        Map<String, Object> adapter = adapterSnapshot == null ? null : adapterSnapshot.object();
        BenchmarkScenarioResult scenario = scenarioSnapshot.result();

        requireRecordedExistingPath(runRoot, run, "profile", profileFile);
        requireRecordedAdapterPath(runRoot, run, adapterFile);

        String profileFingerprint = requiredSha256(profile, "profileFingerprint");
        if (!profileFingerprint.equals(scenario.profileFingerprint())) {
            throw new IllegalArgumentException("Scenario profile fingerprint does not match profile.json");
        }

        Instant runStarted = requiredInstant(run, "started");
        Instant runEnded = requiredInstant(run, "ended");
        if (runEnded.isBefore(runStarted)) {
            throw new IllegalArgumentException("run.json ended timestamp precedes started timestamp");
        }
        if (scenario.processStarted().isBefore(runStarted)
                || scenario.firstCombatReady().isAfter(runEnded)) {
            throw new IllegalArgumentException("Scenario milestones fall outside the finalized run interval");
        }

        int runExitCode = requiredInt(run, "exitCode");
        if (runExitCode != scenario.exitCode()) {
            throw new IllegalArgumentException("Scenario exit code does not match run.json");
        }
        String outcome = requiredString(run, "outcome");
        if ("RUNNING".equals(outcome)) {
            throw new IllegalArgumentException("run.json is not finalized");
        }

        String adapterMode = requiredString(run, "adapterMode");
        boolean enabledMode = enabledMode(scenario.mode());
        if (enabledMode && !"ENABLED".equals(adapterMode)) {
            throw new IllegalArgumentException("Enabled benchmark mode requires adapterMode=ENABLED");
        }
        if (!enabledMode && !"OFF".equals(adapterMode)) {
            throw new IllegalArgumentException("OFF benchmark mode requires adapterMode=OFF");
        }
        if (enabledMode && adapter == null) {
            throw new IllegalArgumentException("Enabled benchmark mode requires adapter.json");
        }
        if (adapter != null && !adapterMode.equals(requiredString(adapter, "mode"))) {
            throw new IllegalArgumentException("adapter.json mode does not match run.json adapterMode");
        }

        if (!RunIdentity.SCOPE.equals(requiredString(run, "runtimeIdentityScope"))) {
            throw new IllegalArgumentException("run.json wrapper runtime identity scope is unsupported");
        }
        String preflightJarSha256 = requiredSha256(run, "preflightJarSha256");
        Map<String, Object> wrapperRuntime = requiredObject(run, "wrapperRuntime");
        validateScalarMap(wrapperRuntime, "run.json wrapperRuntime");

        Map<String, Object> recordingIdentity = requiredObject(summary, "recordingRuntimeIdentity");
        if (!JfrRuntimeIdentity.SCOPE.equals(requiredString(recordingIdentity, "scope"))) {
            throw new IllegalArgumentException("summary.json recording runtime identity scope is unsupported");
        }
        if (!requiredBoolean(recordingIdentity, "complete")) {
            throw new IllegalArgumentException("summary.json recording runtime identity is incomplete");
        }
        Map<String, Object> comparisonIdentity = requiredObject(recordingIdentity, "comparisonIdentity");
        validateScalarMap(comparisonIdentity, "summary.json comparisonIdentity");

        Map<String, Object> files = new LinkedHashMap<>();
        files.put("run", fileIdentity(runRoot, runSnapshot));
        files.put("profile", fileIdentity(runRoot, profileSnapshot));
        files.put("summary", fileIdentity(runRoot, summarySnapshot));
        files.put("adapter", adapterSnapshot == null ? null : fileIdentity(runRoot, adapterSnapshot));
        files.put("scenario", externalFileIdentity(scenarioSnapshot));

        Map<String, Object> textureIdentity = new LinkedHashMap<>();
        textureIdentity.put("mode", optionalString(run, "textureAdapterMode"));
        textureIdentity.put("profileFingerprint", optionalSha256(run, "textureProfileFingerprint"));
        textureIdentity.put("manifestSha256", optionalSha256(run, "textureManifestSha256"));
        textureIdentity.put("indexSha256", optionalSha256(run, "textureIndexSha256"));

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("scenario", scenario.scenario());
        identity.put("profileFingerprint", profileFingerprint);
        identity.put("preflightJarSha256", preflightJarSha256);
        identity.put("wrapperRuntime", orderedCopy(wrapperRuntime));
        identity.put("recordingRuntime", orderedCopy(comparisonIdentity));
        identity.put("launcherKind", requiredString(run, "launcherKind"));
        identity.put("texture", Collections.unmodifiableMap(textureIdentity));

        Map<String, Object> runEvidence = new LinkedHashMap<>();
        runEvidence.put("directory", runRoot);
        runEvidence.put("started", runStarted);
        runEvidence.put("ended", runEnded);
        runEvidence.put("exitCode", runExitCode);
        runEvidence.put("launcherExitCode", optionalLong(run, "launcherExitCode"));
        runEvidence.put("outcome", outcome);
        runEvidence.put("executionFailure", optionalString(run, "executionFailure"));
        runEvidence.put("postprocessingFailures", requiredStringList(run, "postprocessingFailures", 16));
        runEvidence.put("adapterMode", adapterMode);
        runEvidence.put("textureAuto", optionalBoolean(run, "textureAuto"));

        Map<String, Object> summaryEvidence = new LinkedHashMap<>();
        copyOptional(summaryEvidence, summary, "traceStart");
        copyOptional(summaryEvidence, summary, "traceEnd");
        copyOptional(summaryEvidence, summary, "fileReadMs");
        copyOptional(summaryEvidence, summary, "fileReadBytes");
        copyOptional(summaryEvidence, summary, "fileWriteMs");
        copyOptional(summaryEvidence, summary, "fileWriteBytes");
        copyOptional(summaryEvidence, summary, "compilationMs");
        copyOptional(summaryEvidence, summary, "gcPauseMs");
        copyOptional(summaryEvidence, summary, "executionSamples");
        copyOptional(summaryEvidence, summary, "preflightAgentStartedEvents");

        Map<String, Object> adapterEvidence = adapter == null ? null : selectedAdapterEvidence(adapter);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schema", SCHEMA);
        output.put("version", VERSION);
        output.put("identity", identity);
        output.put("scenario", scenario.toMap());
        output.put("run", runEvidence);
        output.put("summary", summaryEvidence);
        output.put("adapter", adapterEvidence);
        output.put("files", files);
        return Collections.unmodifiableMap(output);
    }

    private static Map<String, Object> selectedAdapterEvidence(Map<String, Object> adapter) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String field : List.of(
                "transformerInstalled",
                "killSwitchActive",
                "registryTargets",
                "observedClasses",
                "parsedClasses",
                "malformedClasses",
                "exactMatches",
                "sourceBindingRejected",
                "transformationEligible",
                "transformationDeclined",
                "transformationsApplied",
                "containedFailures",
                "liveTransformationPlansRegistered")) {
            copyOptional(evidence, adapter, field);
        }
        Object texture = adapter.get("textureCompatibility");
        if (texture instanceof Map<?, ?>) {
            evidence.put("textureCompatibility", texture);
        }
        return Collections.unmodifiableMap(evidence);
    }

    private static Map<String, Object> fileIdentity(Path runRoot, JsonSnapshot snapshot) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("path", runRoot.relativize(snapshot.path()));
        identity.put("bytes", snapshot.bytes().length);
        identity.put("sha256", Hashes.sha256(snapshot.bytes()));
        return Collections.unmodifiableMap(identity);
    }

    private static Map<String, Object> externalFileIdentity(ScenarioSnapshot snapshot) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("path", snapshot.path());
        identity.put("bytes", snapshot.bytes().length);
        identity.put("sha256", Hashes.sha256(snapshot.bytes()));
        return Collections.unmodifiableMap(identity);
    }

    private static Path requiredRunFile(Path runRoot, String name) throws IOException {
        Path file = PathContainment.existingInsideRealRoot(runRoot, runRoot.resolve(name));
        if (!Files.isRegularFile(file)) {
            throw new IOException("Expected run evidence file: " + file);
        }
        return file;
    }

    private static Path optionalRunFile(Path runRoot, String name) throws IOException {
        Path candidate = runRoot.resolve(name).normalize();
        if (!Files.exists(candidate)) {
            return null;
        }
        Path file = PathContainment.existingInsideRealRoot(runRoot, candidate);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Expected optional run evidence to be a regular file: " + file);
        }
        return file;
    }

    private static JsonSnapshot readSnapshot(Path file, long maximumBytes, String kind) throws IOException {
        byte[] bytes = readBounded(file, maximumBytes, kind);
        return new JsonSnapshot(file, bytes, StrictJson.object(new String(bytes, StandardCharsets.UTF_8)));
    }

    private static ScenarioSnapshot readScenarioSnapshot(Path file) throws IOException {
        byte[] bytes = readBounded(file, MAX_SCENARIO_BYTES, "scenario evidence");
        BenchmarkScenarioResult result = BenchmarkScenarioSnapshot.parse(
                file,
                new String(bytes, StandardCharsets.UTF_8));
        return new ScenarioSnapshot(file, bytes, result);
    }

    private static byte[] readBounded(Path file, long maximumBytes, String kind) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes(Math.toIntExact(maximumBytes + 1));
            if (bytes.length > maximumBytes) {
                throw new IOException(kind + " exceeds " + maximumBytes + " bytes: " + file);
            }
            return bytes;
        }
    }

    private static void requireRecordedExistingPath(
            Path runRoot,
            Map<String, Object> run,
            String field,
            Path expected) throws IOException {
        Path recorded = Path.of(requiredString(run, field)).toAbsolutePath().normalize();
        Path real = PathContainment.existingInsideRealRoot(runRoot, recorded);
        if (!real.equals(expected)) {
            throw new IllegalArgumentException("run.json " + field + " does not identify " + expected.getFileName());
        }
    }

    private static void requireRecordedAdapterPath(
            Path runRoot,
            Map<String, Object> run,
            Path existingAdapter) throws IOException {
        Path recorded = Path.of(requiredString(run, "adapterReport")).toAbsolutePath().normalize();
        Path fileName = recorded.getFileName();
        Path parent = recorded.getParent();
        if (fileName == null
                || !"adapter.json".equals(fileName.toString())
                || parent == null
                || !parent.toRealPath().equals(runRoot)) {
            throw new IllegalArgumentException("run.json adapterReport does not identify adapter.json in the run directory");
        }
        if (existingAdapter != null) {
            Path real = PathContainment.existingInsideRealRoot(runRoot, recorded);
            if (!real.equals(existingAdapter)) {
                throw new IllegalArgumentException("run.json adapterReport disagrees with collected adapter evidence");
            }
        }
    }

    private static boolean enabledMode(BenchmarkScenarioMode mode) {
        return mode.name().startsWith("ENABLED_");
    }

    private static Options parse(String[] args, int offset) {
        Path runDirectory = null;
        Path scenario = null;
        Path output = null;
        for (int i = offset; i < args.length; i++) {
            String value = args[i];
            if ("--scenario".equals(value)) {
                scenario = Path.of(requireValue(args, ++i, value));
            } else if ("--output".equals(value)) {
                output = Path.of(requireValue(args, ++i, value));
            } else if (value.startsWith("--")) {
                throw new IllegalArgumentException("Unknown benchmark collection option: " + value);
            } else if (runDirectory == null) {
                runDirectory = Path.of(value);
            } else {
                throw new IllegalArgumentException("Unexpected benchmark collection argument: " + value);
            }
        }
        if (runDirectory == null || scenario == null) {
            throw new IllegalArgumentException(
                    "Expected: benchmark collect <run-directory> --scenario <scenario-result.json> [--output <run-record.json>]");
        }
        return new Options(runDirectory, scenario, output);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static String requiredString(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Expected non-empty string field " + field);
        }
        return text;
    }

    private static String optionalString(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Expected string or null field " + field);
        }
        return text;
    }

    private static String requiredSha256(Map<String, Object> values, String field) {
        String value = requiredString(values, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected lowercase SHA-256 field " + field);
        }
        return value;
    }

    private static String optionalSha256(Map<String, Object> values, String field) {
        String value = optionalString(values, field);
        if (value != null && !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected lowercase SHA-256 or null field " + field);
        }
        return value;
    }

    private static Instant requiredInstant(Map<String, Object> values, String field) {
        try {
            return Instant.parse(requiredString(values, field));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid instant field " + field, error);
        }
    }

    private static int requiredInt(Map<String, Object> values, String field) {
        long value = requiredLong(values, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer field out of range: " + field);
        }
        return (int) value;
    }

    private static long requiredLong(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Long number)) {
            throw new IllegalArgumentException("Expected integer field " + field);
        }
        return number;
    }

    private static Long optionalLong(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long number)) {
            throw new IllegalArgumentException("Expected integer or null field " + field);
        }
        return number;
    }

    private static boolean requiredBoolean(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("Expected boolean field " + field);
        }
        return flag;
    }

    private static Boolean optionalBoolean(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("Expected boolean or null field " + field);
        }
        return flag;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredObject(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected object field " + field);
        }
        return (Map<String, Object>) map;
    }

    private static List<String> requiredStringList(
            Map<String, Object> values,
            String field,
            int maximumEntries) {
        Object value = values.get(field);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected array field " + field);
        }
        if (list.size() > maximumEntries) {
            throw new IllegalArgumentException(field + " exceeds " + maximumEntries + " entries");
        }
        List<String> output = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("Expected string entries in " + field);
            }
            if (text.length() > 16_384) {
                throw new IllegalArgumentException(field + " entry is too long");
            }
            output.add(text);
        }
        return List.copyOf(output);
    }

    private static void validateScalarMap(Map<String, Object> values, String name) {
        if (values.isEmpty() || values.size() > 64) {
            throw new IllegalArgumentException(name + " must contain 1-64 fields");
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().isBlank() || entry.getKey().length() > 128) {
                throw new IllegalArgumentException("Invalid key in " + name);
            }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Long || value instanceof Boolean)) {
                throw new IllegalArgumentException(name + " values must be scalar");
            }
            if (value instanceof String text && text.length() > 16_384) {
                throw new IllegalArgumentException(name + " value is too long");
            }
        }
    }

    private static void copyOptional(Map<String, Object> target, Map<String, Object> source, String field) {
        if (source.containsKey(field)) {
            target.put(field, source.get(field));
        }
    }

    private static Map<String, Object> orderedCopy(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record JsonSnapshot(Path path, byte[] bytes, Map<String, Object> object) {
    }

    private record ScenarioSnapshot(Path path, byte[] bytes, BenchmarkScenarioResult result) {
    }

    private record Options(Path runDirectory, Path scenario, Path output) {
    }
}
