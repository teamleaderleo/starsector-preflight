package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.ClasspathProfileIndex;
import dev.starsector.preflight.core.ClasspathProfileIndexIO;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchmarkCommand {
    private static final int DEFAULT_QUERIES = 5_000;
    private static final long DEFAULT_SEED = 0x5eed_5eedL;

    private BenchmarkCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        if (offset >= args.length || !"lookups".equals(args[offset])) {
            throw new IllegalArgumentException(
                    "Expected: benchmark lookups [--resource-index <index.spfi>] [--classpath-index <profile.spfc>] [--queries <count>] [--seed <long>]");
        }
        Options options = parse(args, offset + 1);
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

    private static Options parse(String[] args, int offset) {
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

    private static Path absolute(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private record Options(Path resourceIndex, Path classpathIndex, int queries, long seed) {
    }
}
