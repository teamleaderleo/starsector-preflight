package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.BenchmarkScenarioMode;
import dev.starsector.preflight.core.BenchmarkScenarioResult;
import dev.starsector.preflight.core.ClasspathProfileIndex;
import dev.starsector.preflight.core.ClasspathProfileIndexIO;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchmarkCommand {
    private static final int DEFAULT_QUERIES = 5_000;
    private static final long DEFAULT_SEED = 0x5eed_5eedL;
    private static final String DEFAULT_SCENARIO = "startup-campaign-combat-v1";

    private BenchmarkCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        if (offset >= args.length) {
            throw expectedCommand();
        }
        return switch (args[offset]) {
            case "lookups" -> executeLookups(args, offset + 1);
            case "scenario" -> executeScenario(args, offset + 1);
            case "collect" -> BenchmarkRunCollector.execute(args, offset + 1);
            case "compare" -> BenchmarkScenarioComparison.execute(args, offset + 1);
            default -> throw expectedCommand();
        };
    }

    private static int executeLookups(String[] args, int offset) throws Exception {
        Options options = parseLookups(args, offset);
        List<LookupEquivalence.DomainResult> domains = new ArrayList<>();
        if (options.resourceIndex() != null) {
            ResourceIndex index = ResourceIndexIO.read(options.resourceIndex());
            domains.add(LookupEquivalence.resources(index, options.queries(), options.seed()));
        }
        if (options.classpathIndex() != null) {
            ClasspathProfileIndex index = ClasspathProfileIndexIO.read(options.classpathIndex());
            domains.add(LookupEquivalence.classpath(
                    index,
                    options.queries(),
                    options.seed() ^ 0x9e37_79b9_7f4a_7c15L));
        }
        boolean equivalent = domains.stream().allMatch(LookupEquivalence.DomainResult::equivalent);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("queriesPerDomain", options.queries());
        output.put("seed", options.seed());
        output.put("resourceIndex", absolute(options.resourceIndex()));
        output.put("classpathIndex", absolute(options.classpathIndex()));
        output.put("equivalent", equivalent);
        output.put("totalMismatches", domains.stream()
                .mapToLong(LookupEquivalence.DomainResult::mismatches)
                .sum());
        output.put("domains", domains.stream().map(LookupEquivalence.DomainResult::toMap).toList());
        System.out.println(Json.object(output));
        return equivalent ? 0 : 6;
    }

    private static int executeScenario(String[] args, int offset) throws Exception {
        ScenarioOptions options = parseScenario(args, offset);
        BenchmarkScenarioResult result = new BenchmarkScenarioResult(
                options.runId(),
                options.scenario(),
                options.mode(),
                options.iteration(),
                options.profileFingerprint(),
                options.processStarted(),
                options.mainMenuReady(),
                options.campaignReady(),
                options.firstCombatReady(),
                options.exitCode(),
                options.adapterCounters(),
                options.cacheCounters(),
                options.disableReasons());
        String json = result.toJson();
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

    private static Options parseLookups(String[] args, int offset) {
        Path resource = null;
        Path classpath = null;
        int queries = DEFAULT_QUERIES;
        long seed = DEFAULT_SEED;
        for (int i = offset; i < args.length; i++) {
            switch (args[i]) {
                case "--resource-index" -> resource = Path.of(requireValue(args, ++i, "--resource-index"));
                case "--classpath-index" -> classpath = Path.of(requireValue(args, ++i, "--classpath-index"));
                case "--queries" -> queries = parseInt(requireValue(args, ++i, "--queries"), "query count");
                case "--seed" -> seed = parseLong(requireValue(args, ++i, "--seed"), "seed");
                default -> throw new IllegalArgumentException("Unknown lookup benchmark option: " + args[i]);
            }
        }
        if (resource == null && classpath == null) {
            throw new IllegalArgumentException("Lookup benchmark requires --resource-index or --classpath-index");
        }
        if (queries < 1 || queries > 1_000_000) {
            throw new IllegalArgumentException("Lookup query count must be between 1 and 1,000,000");
        }
        return new Options(resource, classpath, queries, seed);
    }

    private static ScenarioOptions parseScenario(String[] args, int offset) {
        String runId = null;
        String scenario = DEFAULT_SCENARIO;
        BenchmarkScenarioMode mode = null;
        int iteration = 1;
        String profileFingerprint = null;
        Instant processStarted = null;
        Instant mainMenuReady = null;
        Instant campaignReady = null;
        Instant firstCombatReady = null;
        Integer exitCode = null;
        Map<String, Long> adapterCounters = new LinkedHashMap<>();
        Map<String, Long> cacheCounters = new LinkedHashMap<>();
        List<String> disableReasons = new ArrayList<>();
        Path output = null;
        for (int i = offset; i < args.length; i++) {
            String option = args[i];
            switch (option) {
                case "--run-id" -> runId = requireValue(args, ++i, option);
                case "--scenario-id" -> scenario = requireValue(args, ++i, option);
                case "--mode" -> mode = BenchmarkScenarioMode.parse(requireValue(args, ++i, option));
                case "--iteration" -> iteration = parseInt(requireValue(args, ++i, option), "iteration");
                case "--profile-fingerprint" -> profileFingerprint = requireValue(args, ++i, option);
                case "--process-start" -> processStarted = parseInstant(requireValue(args, ++i, option), option);
                case "--main-menu-ready" -> mainMenuReady = parseInstant(requireValue(args, ++i, option), option);
                case "--campaign-ready" -> campaignReady = parseInstant(requireValue(args, ++i, option), option);
                case "--first-combat-ready" -> firstCombatReady = parseInstant(requireValue(args, ++i, option), option);
                case "--exit-code" -> exitCode = parseInt(requireValue(args, ++i, option), "exit code");
                case "--adapter-counter" -> addCounter(
                        adapterCounters,
                        requireValue(args, ++i, option),
                        "adapter counter");
                case "--cache-counter" -> addCounter(
                        cacheCounters,
                        requireValue(args, ++i, option),
                        "cache counter");
                case "--disable-reason" -> {
                    if (disableReasons.size() >= BenchmarkScenarioResult.MAX_DISABLE_REASONS) {
                        throw new IllegalArgumentException("Too many disable reasons");
                    }
                    disableReasons.add(requireValue(args, ++i, option));
                }
                case "--output" -> output = Path.of(requireValue(args, ++i, option));
                default -> throw new IllegalArgumentException("Unknown scenario benchmark option: " + option);
            }
        }
        return new ScenarioOptions(
                require(runId, "--run-id"),
                scenario,
                require(mode, "--mode"),
                iteration,
                profileFingerprint,
                require(processStarted, "--process-start"),
                require(mainMenuReady, "--main-menu-ready"),
                require(campaignReady, "--campaign-ready"),
                require(firstCombatReady, "--first-combat-ready"),
                require(exitCode, "--exit-code"),
                Map.copyOf(adapterCounters),
                Map.copyOf(cacheCounters),
                List.copyOf(disableReasons),
                output);
    }

    private static void addCounter(Map<String, Long> counters, String value, String kind) {
        if (counters.size() >= BenchmarkScenarioResult.MAX_COUNTERS_PER_DOMAIN) {
            throw new IllegalArgumentException("Too many " + kind + " entries");
        }
        int separator = value.indexOf('=');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Expected " + kind + " as name=value: " + value);
        }
        String name = value.substring(0, separator);
        long count = parseLong(value.substring(separator + 1), kind + " value");
        if (counters.putIfAbsent(name, count) != null) {
            throw new IllegalArgumentException("Duplicate " + kind + ": " + name);
        }
    }

    private static Instant parseInstant(String raw, String option) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid timestamp for " + option + ": " + raw, error);
        }
    }

    private static int parseInt(String raw, String kind) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + kind + ": " + raw, error);
        }
    }

    private static long parseLong(String raw, String kind) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + kind + ": " + raw, error);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static <T> T require(T value, String option) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required option " + option);
        }
        return value;
    }

    private static Path absolute(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private static IllegalArgumentException expectedCommand() {
        return new IllegalArgumentException(
                "Expected: benchmark lookups ..., benchmark scenario ..., benchmark collect ..., or benchmark compare ...");
    }

    private record Options(Path resourceIndex, Path classpathIndex, int queries, long seed) {
    }

    private record ScenarioOptions(
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
            List<String> disableReasons,
            Path output) {
    }
}
