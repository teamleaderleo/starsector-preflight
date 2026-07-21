package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CLI output layer for collected-run campaign reports. */
final class BenchmarkCampaignComparisonCommand {
    static final int VERSION = 2;
    private static final int MAX_INPUTS = 10_000;

    private BenchmarkCampaignComparisonCommand() {
    }

    static int execute(String[] args, int offset) throws IOException {
        Options options = parse(args, offset);
        List<BenchmarkCollectedRunComparison.CollectedRun> runs = new ArrayList<>();
        for (Path input : options.inputs()) {
            runs.add(BenchmarkCollectedRunComparison.read(input));
        }

        Map<String, Object> output = new LinkedHashMap<>(BenchmarkCollectedRunComparison.compare(runs));
        output.put("version", VERSION);
        output.put("primaryComparison", BenchmarkCampaignDelta.compare(runs));
        String json = Json.object(output);

        if (options.output() == null) {
            System.out.println(json);
        } else {
            Path outputPath = options.output().toAbsolutePath().normalize();
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, json + System.lineSeparator());
            System.out.println(outputPath);
        }
        return 0;
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
                    throw new IllegalArgumentException(
                            "Collected-run comparison exceeds " + MAX_INPUTS + " inputs");
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

    private record Options(List<Path> inputs, Path output) {
    }
}
