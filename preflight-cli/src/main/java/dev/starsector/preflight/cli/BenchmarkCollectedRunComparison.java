package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares identity-checked collected-run records as one benchmark campaign. */
final class BenchmarkCollectedRunComparison {
    static final String SCHEMA = "starsector-preflight-benchmark-campaign";
    static final int VERSION = 1;

    private static final long MAX_RECORD_BYTES = 64L * 1024 * 1024;
    private static final int MAX_INPUTS = 10_000;
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema", "version", "identity", "scenario", "run", "summary", "adapter", "files");
    private static final Set<String> IDENTITY_KEYS = Set.of(
            "scenario",
            "profileFingerprint",
            "preflightJarSha256",
            "wrapperRuntime",
            "recordingRuntime",
            "launcherKind",
            "texture");
    private static final Set<String> TEXTURE_KEYS = Set.of(
            "mode", "profileFingerprint", "manifestSha256", "indexSha256");
    private static final Set<String> RUN_KEYS = Set.of(
            "directory",
            "started",
            "ended",
            "exitCode",
            "launcherExitCode",
            "outcome",
            "executionFailure",
            "postprocessingFailures",
            "adapterMode",
            "textureAuto");
    private static final Set<String> FILES_KEYS = Set.of(
            "run", "profile", "summary", "adapter", "scenario");
    private static final Set<String> FILE_IDENTITY_KEYS = Set.of(
            "path", "bytes", "sha256");

    private BenchmarkCollectedRunComparison() {
    }

    static int execute(String[] args, int offset) throws IOException {
        Options options = parse(args, offset);
        List<CollectedRun> runs = new ArrayList<>();
        for (Path input : options.inputs()) {
            runs.add(read(input));
        }
        Map<String, Object> result = compare(runs);
        String json = Json.object(result);
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

    static CollectedRun read(Path input) throws IOException {
        Path file = input.toAbsolutePath().normalize().toRealPath();
        if (!Files.isRegularFile(file)) {
            throw new IOException("Expected a collected benchmark run file: " + file);
        }
        byte[] bytes = readBounded(file);
        Map<String, Object> root = StrictJson.object(new String(bytes, StandardCharsets.UTF_8));
        requireKeys(root, ROOT_KEYS, "collected run");
        if (!BenchmarkRunCollector.SCHEMA.equals(requiredString(root, "schema"))) {
            throw invalid(file, "unexpected schema");
        }
        if (requiredInt(root, "version") != BenchmarkRunCollector.VERSION) {
            throw invalid(file, "unsupported version");
        }

        Map<String, Object> identity = requiredObject(root, "identity");
        requireKeys(identity, IDENTITY_KEYS, "identity");
        Map<String, Object> texture = requiredObject(identity, "texture");
        requireKeys(texture, TEXTURE_KEYS, "texture identity");
        validateScalarMap(requiredObject(identity, "wrapperRuntime"), "wrapperRuntime");
        validateScalarMap(requiredObject(identity, "recordingRuntime"), "recordingRuntime");
        validateTexture(texture);

        BenchmarkScenarioResult scenario = BenchmarkScenarioSnapshot.parse(
                file,
                Json.object(requiredObject(root, "scenario")));
        String scenarioId = requiredString(identity, "scenario");
        String profile = requiredSha256(identity, "profileFingerprint");
        if (!scenarioId.equals(scenario.scenario())) {
            throw invalid(file, "identity scenario disagrees with embedded scenario");
        }
        if (!profile.equals(scenario.profileFingerprint())) {
            throw invalid(file, "identity profile disagrees with embedded scenario");
        }
        requiredSha256(identity, "preflightJarSha256");
        requiredString(identity, "launcherKind");

        Map<String, Object> run = requiredObject(root, "run");
        requireKeys(run, RUN_KEYS, "run evidence");
        validateRunEvidence(file, run, scenario);
        int runExit = requiredInt(run, "exitCode");
        if (runExit != scenario.exitCode()) {
            throw invalid(file, "run exit code disagrees with embedded scenario");
        }

        if (!(root.get("summary") instanceof Map<?, ?>)) {
            throw invalid(file, "summary evidence must be an object");
        }

        String adapterMode = requiredString(run, "adapterMode");
        boolean enabled = enabledMode(scenario.mode());
        if (enabled && !"ENABLED".equals(adapterMode)) {
            throw invalid(file, "enabled scenario requires adapterMode=ENABLED");
        }
        if (!enabled && !"OFF".equals(adapterMode)) {
            throw invalid(file, "OFF scenario requires adapterMode=OFF");
        }

        Map<String, Object> files = requiredObject(root, "files");
        requireKeys(files, FILES_KEYS, "evidence files");
        validateFileIdentity(requiredObject(files, "run"), "run evidence file");
        validateFileIdentity(requiredObject(files, "profile"), "profile evidence file");
        validateFileIdentity(requiredObject(files, "summary"), "summary evidence file");
        validateFileIdentity(requiredObject(files, "scenario"), "scenario evidence file");
        Object adapterFile = files.get("adapter");
        Object adapterEvidence = root.get("adapter");

        if (enabled) {
            requireEnabledTextureIdentity(file, texture, profile);
            if (!Boolean.TRUE.equals(optionalBoolean(run, "textureAuto"))) {
                throw invalid(file, "enabled campaign run requires textureAuto=true");
            }
            if (!(adapterEvidence instanceof Map<?, ?>)) {
                throw invalid(file, "enabled scenario lacks adapter evidence");
            }
            if (!(adapterFile instanceof Map<?, ?>)) {
                throw invalid(file, "enabled scenario lacks adapter file identity");
            }
            validateFileIdentity(requiredObject(files, "adapter"), "adapter evidence file");
        } else {
            if (adapterEvidence != null || adapterFile != null) {
                throw invalid(file, "OFF scenario must not carry adapter evidence");
            }
        }

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("path", file);
        source.put("bytes", bytes.length);
        source.put("sha256", Hashes.sha256(bytes));
        return new CollectedRun(
                file,
                Collections.unmodifiableMap(new LinkedHashMap<>(root)),
                Collections.unmodifiableMap(new LinkedHashMap<>(identity)),
                Collections.unmodifiableMap(new LinkedHashMap<>(texture)),
                scenario,
                Collections.unmodifiableMap(source));
    }

    static Map<String, Object> compare(List<CollectedRun> input) {
        if (input.size() < 2) {
            throw new IllegalArgumentException("Collected-run comparison requires at least two records");
        }
        List<CollectedRun> runs = new ArrayList<>(input);
        CampaignIdentity expected = CampaignIdentity.from(runs.get(0));
        Set<String> runIds = new LinkedHashSet<>();
        Set<String> modeIterations = new LinkedHashSet<>();
        EnumMap<BenchmarkScenarioMode, Map<String, Object>> textureByMode =
                new EnumMap<>(BenchmarkScenarioMode.class);

        for (CollectedRun run : runs) {
            CampaignIdentity actual = CampaignIdentity.from(run);
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                        "Collected run identity mismatch for " + run.scenario().runId());
            }
            if (!runIds.add(run.scenario().runId())) {
                throw new IllegalArgumentException("Duplicate benchmark run ID: " + run.scenario().runId());
            }
            String iteration = run.scenario().mode() + "#" + run.scenario().iteration();
            if (!modeIterations.add(iteration)) {
                throw new IllegalArgumentException("Duplicate benchmark mode iteration: " + iteration);
            }
            Map<String, Object> prior = textureByMode.putIfAbsent(run.scenario().mode(), run.textureIdentity());
            if (prior != null && !prior.equals(run.textureIdentity())) {
                throw new IllegalArgumentException(
                        "Texture identity mismatch within mode " + run.scenario().mode());
            }
        }

        runs.sort(Comparator
                .comparingInt((CollectedRun run) -> run.scenario().mode().ordinal())
                .thenComparingInt(run -> run.scenario().iteration())
                .thenComparing(run -> run.scenario().runId()));

        Map<String, Object> modes = new LinkedHashMap<>();
        Map<String, Object> textures = new LinkedHashMap<>();
        for (BenchmarkScenarioMode mode : BenchmarkScenarioMode.values()) {
            List<BenchmarkScenarioResult> group = runs.stream()
                    .filter(run -> run.scenario().mode() == mode)
                    .map(CollectedRun::scenario)
                    .toList();
            if (!group.isEmpty()) {
                modes.put(mode.toString(), modeSummary(group));
                textures.put(mode.toString(), textureByMode.get(mode));
            }
        }

        List<Map<String, Object>> serializedRuns = new ArrayList<>();
        for (CollectedRun run : runs) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("source", run.sourceIdentity());
            value.put("record", run.root());
            serializedRuns.add(value);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schema", SCHEMA);
        output.put("version", VERSION);
        output.put("identity", expected.toMap());
        output.put("textureIdentitiesByMode", textures);
        output.put("totalRuns", runs.size());
        output.put("successfulRuns", runs.stream().filter(run -> run.scenario().exitCode() == 0).count());
        output.put("failedRuns", runs.stream().filter(run -> run.scenario().exitCode() != 0).count());
        output.put("runs", serializedRuns);
        output.put("modes", modes);
        return Collections.unmodifiableMap(output);
    }

    private static byte[] readBounded(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes(Math.toIntExact(MAX_RECORD_BYTES + 1));
            if (bytes.length > MAX_RECORD_BYTES) {
                throw new IOException("Collected benchmark run exceeds " + MAX_RECORD_BYTES + " bytes: " + file);
            }
            return bytes;
        }
    }

    private static void validateRunEvidence(
            Path source,
            Map<String, Object> run,
            BenchmarkScenarioResult scenario) {
        requiredString(run, "directory");
        Instant started = instant(run, "started");
        Instant ended = instant(run, "ended");
        if (ended.isBefore(started)) {
            throw invalid(source, "run ended timestamp precedes started timestamp");
        }
        if (scenario.processStarted().isBefore(started)
                || scenario.firstCombatReady().isAfter(ended)) {
            throw invalid(source, "scenario milestones fall outside the finalized run interval");
        }
        requiredInt(run, "exitCode");
        optionalLong(run, "launcherExitCode");
        String outcome = requiredString(run, "outcome");
        if ("RUNNING".equals(outcome)) {
            throw invalid(source, "run evidence is unfinished");
        }
        optionalString(run, "executionFailure");
        requiredStringList(run, "postprocessingFailures", 16);
        requiredString(run, "adapterMode");
        optionalBoolean(run, "textureAuto");
    }

    private static Map<String, Object> modeSummary(List<BenchmarkScenarioResult> results) {
        List<BenchmarkScenarioResult> successful = results.stream()
                .filter(result -> result.exitCode() == 0)
                .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", results.size());
        summary.put("successfulRuns", successful.size());
        summary.put("failedRuns", results.size() - successful.size());
        summary.put("statistics", successful.isEmpty() ? null : statistics(successful));
        return summary;
    }

    private static Map<String, Object> statistics(List<BenchmarkScenarioResult> results) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("processToMainMenuMs", metric(results, result -> millis(result.processStarted(), result.mainMenuReady())));
        values.put("mainMenuToCampaignReadyMs", metric(results, result -> millis(result.mainMenuReady(), result.campaignReady())));
        values.put("campaignToFirstCombatReadyMs", metric(results, result -> millis(result.campaignReady(), result.firstCombatReady())));
        values.put("processToCampaignReadyMs", metric(results, result -> millis(result.processStarted(), result.campaignReady())));
        values.put("processToFirstCombatReadyMs", metric(results, result -> millis(result.processStarted(), result.firstCombatReady())));
        return values;
    }

    private static Map<String, Object> metric(
            List<BenchmarkScenarioResult> results,
            Metric metric) {
        List<Long> values = results.stream().map(metric::value).sorted().toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("minimum", values.get(0));
        summary.put("median", median(values));
        summary.put("maximum", values.get(values.size() - 1));
        return summary;
    }

    private static Number median(List<Long> sorted) {
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) {
            return sorted.get(middle);
        }
        return BigDecimal.valueOf(sorted.get(middle - 1))
                .add(BigDecimal.valueOf(sorted.get(middle)))
                .divide(BigDecimal.valueOf(2))
                .setScale(1);
    }

    private static void requireEnabledTextureIdentity(
            Path source,
            Map<String, Object> texture,
            String profile) {
        String textureProfile = requiredSha256(texture, "profileFingerprint");
        if (!profile.equals(textureProfile)) {
            throw invalid(source, "enabled texture profile disagrees with campaign profile");
        }
        requiredSha256(texture, "manifestSha256");
        requiredSha256(texture, "indexSha256");
        String mode = requiredString(texture, "mode");
        if (!"COMPATIBILITY".equals(mode) && !"PREPARED_PIXELS".equals(mode)) {
            throw invalid(source, "unsupported enabled texture mode " + mode);
        }
    }

    private static void validateTexture(Map<String, Object> texture) {
        optionalString(texture, "mode");
        optionalSha256(texture, "profileFingerprint");
        optionalSha256(texture, "manifestSha256");
        optionalSha256(texture, "indexSha256");
    }

    private static void validateFileIdentity(Map<String, Object> identity, String name) {
        requireKeys(identity, FILE_IDENTITY_KEYS, name);
        requiredString(identity, "path");
        long bytes = requiredLong(identity, "bytes");
        if (bytes < 0) {
            throw new IllegalArgumentException(name + " byte count must be non-negative");
        }
        requiredSha256(identity, "sha256");
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

    private static Options parse(String[] args, int offset) {
        List<Path> inputs = new ArrayList<>();
        Path output = null;
        for (int i = offset; i < args.length; i++) {
            if ("--output".equals(args[i])) {
                if (output != null) {
                    throw new IllegalArgumentException("Duplicate --output option");
                }
                if (++i >= args.length) {
                    throw new IllegalArgumentException("Missing value for --output");
                }
                output = Path.of(args[i]);
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown collected-run comparison option: " + args[i]);
            } else {
                if (inputs.size() >= MAX_INPUTS) {
                    throw new IllegalArgumentException("Collected-run comparison exceeds " + MAX_INPUTS + " inputs");
                }
                inputs.add(Path.of(args[i]));
            }
        }
        if (inputs.size() < 2) {
            throw new IllegalArgumentException(
                    "Expected: benchmark compare-runs <collected-run.json> <collected-run.json>... [--output <campaign.json>]");
        }
        return new Options(List.copyOf(inputs), output);
    }

    private static boolean enabledMode(BenchmarkScenarioMode mode) {
        return mode.name().startsWith("ENABLED_");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredObject(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected object field " + field);
        }
        return (Map<String, Object>) map;
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
        if (text.length() > 16_384) {
            throw new IllegalArgumentException(field + " value is too long");
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

    private static Instant instant(Map<String, Object> values, String field) {
        try {
            return Instant.parse(requiredString(values, field));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid instant field " + field, error);
        }
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
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("Expected string entries in " + field);
            }
            if (text.length() > 16_384) {
                throw new IllegalArgumentException(field + " entry is too long");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static void requireKeys(Map<String, Object> values, Set<String> keys, String name) {
        if (!values.keySet().equals(keys)) {
            throw new IllegalArgumentException("Unexpected " + name + " fields: " + values.keySet());
        }
    }

    private static IllegalArgumentException invalid(Path source, String reason) {
        return new IllegalArgumentException("Invalid collected benchmark run " + source + ": " + reason);
    }

    private static long millis(Instant start, Instant end) {
        return Duration.between(start, end).toMillis();
    }

    record CollectedRun(
            Path source,
            Map<String, Object> root,
            Map<String, Object> identity,
            Map<String, Object> textureIdentity,
            BenchmarkScenarioResult scenario,
            Map<String, Object> sourceIdentity) {
    }

    private record CampaignIdentity(
            String scenario,
            String profileFingerprint,
            String preflightJarSha256,
            Map<String, Object> wrapperRuntime,
            Map<String, Object> recordingRuntime,
            String launcherKind) {
        static CampaignIdentity from(CollectedRun run) {
            Map<String, Object> identity = run.identity();
            return new CampaignIdentity(
                    requiredString(identity, "scenario"),
                    requiredSha256(identity, "profileFingerprint"),
                    requiredSha256(identity, "preflightJarSha256"),
                    immutableScalarCopy(requiredObject(identity, "wrapperRuntime")),
                    immutableScalarCopy(requiredObject(identity, "recordingRuntime")),
                    requiredString(identity, "launcherKind"));
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("scenario", scenario);
            values.put("profileFingerprint", profileFingerprint);
            values.put("preflightJarSha256", preflightJarSha256);
            values.put("wrapperRuntime", wrapperRuntime);
            values.put("recordingRuntime", recordingRuntime);
            values.put("launcherKind", launcherKind);
            return Collections.unmodifiableMap(values);
        }

        private static Map<String, Object> immutableScalarCopy(Map<String, Object> values) {
            validateScalarMap(values, "campaign identity");
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private record Options(List<Path> inputs, Path output) {
    }

    @FunctionalInterface
    private interface Metric {
        long value(BenchmarkScenarioResult result);
    }
}
