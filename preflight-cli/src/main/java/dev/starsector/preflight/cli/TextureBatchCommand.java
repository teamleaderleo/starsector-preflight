package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.ResourceIndexValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class TextureBatchCommand {
    private static final long MIB = 1024L * 1024L;

    private TextureBatchCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        Options options = parse(args, offset);
        Path cacheDirectory = options.cacheDirectory() == null
                ? Path.of(System.getProperty("user.home")).resolve(".starsector-preflight").resolve("cache")
                : options.cacheDirectory().toAbsolutePath().normalize();

        ResourceIndex index;
        Path indexPath;
        if (options.index() != null) {
            indexPath = options.index().toAbsolutePath().normalize();
            index = ResourceIndexIO.read(indexPath);
            ResourceIndexValidator.Result validation = ResourceIndexValidator.validate(index);
            if (!validation.valid()) {
                throw new IOException("Resource index is stale: " + validation.invalidProviders()
                        + " provider entries differ from disk");
            }
        } else {
            DiscoveryResult discovery = StarsectorDiscovery.discover(
                    Platform.current(),
                    Path.of(System.getProperty("user.home")),
                    Path.of(System.getProperty("user.dir")),
                    System.getenv(),
                    options.game(),
                    options.launcher());
            LaunchTarget target = discovery.selected();
            if (target == null) {
                System.err.println("Preflight could not locate Starsector. Run `doctor` or provide --game.");
                return 3;
            }
            ResourceIndexBuilder.BuildResult built = ResourceIndexBuilder.build(target.installRoot());
            index = built.index();
            indexPath = cacheDirectory.resolve("indexes").resolve(index.profileFingerprint() + ".spfi");
            ResourceIndexIO.write(indexPath, index);
        }

        TextureBatchBuilder.Result result = TextureBatchBuilder.build(
                index,
                cacheDirectory,
                new TextureBatchBuilder.Options(options.workers(), options.memoryBudgetBytes()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cacheDirectory", cacheDirectory);
        report.put("index", indexPath);
        report.put("profileFingerprint", index.profileFingerprint());
        report.put("manifest", result.manifestPath());
        report.put("manifestEntries", result.manifest().entryCount());
        report.put("candidateEntries", result.candidateEntries());
        report.put("hashedEntries", result.hashedEntries());
        report.put("uniqueContent", result.uniqueContent());
        report.put("cacheHitBlobs", result.cacheHitBlobs());
        report.put("builtBlobs", result.builtBlobs());
        report.put("failedBlobs", result.failedBlobs());
        report.put("quarantinedBlobs", result.quarantinedBlobs());
        report.put("deduplicatedEntries", result.deduplicatedEntries());
        report.put("sourceBytes", result.sourceBytes());
        report.put("uniquePixelBytes", result.uniquePixelBytes());
        report.put("uniqueBlobBytes", result.uniqueBlobBytes());
        report.put("workers", options.workers());
        report.put("memoryBudgetBytes", options.memoryBudgetBytes());
        report.put("durationMs", result.durationMillis());
        report.put("diagnostics", result.diagnostics());
        System.out.println(Json.object(report));
        return result.failedBlobs() == 0 ? 0 : 6;
    }

    private static Options parse(String[] args, int offset) {
        Path game = null;
        Path launcher = null;
        Path index = null;
        Path cacheDirectory = null;
        int workers = defaultWorkers();
        long memoryBudgetBytes = defaultMemoryBudgetBytes();
        for (int i = offset; i < args.length; i++) {
            switch (args[i]) {
                case "--game" -> game = Path.of(requireValue(args, ++i, "--game"));
                case "--launcher" -> launcher = Path.of(requireValue(args, ++i, "--launcher"));
                case "--index" -> index = Path.of(requireValue(args, ++i, "--index"));
                case "--cache-dir" -> cacheDirectory = Path.of(requireValue(args, ++i, "--cache-dir"));
                case "--workers" -> workers = integer(requireValue(args, ++i, "--workers"), "workers");
                case "--memory-mb" -> {
                    long memoryMb = positiveLong(requireValue(args, ++i, "--memory-mb"), "memory-mb");
                    memoryBudgetBytes = Math.multiplyExact(memoryMb, MIB);
                }
                default -> throw new IllegalArgumentException("Unknown texture build option: " + args[i]);
            }
        }
        if (index != null && (game != null || launcher != null)) {
            throw new IllegalArgumentException("Use either --index or game discovery options, not both");
        }
        return new Options(game, launcher, index, cacheDirectory, workers, memoryBudgetBytes);
    }

    private static int defaultWorkers() {
        return Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static long defaultMemoryBudgetBytes() {
        long preferred = Runtime.getRuntime().maxMemory() / 4;
        return Math.max(128L * MIB, Math.min(512L * MIB, preferred));
    }

    private static int integer(String raw, String name) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + name + ": " + raw, error);
        }
    }

    private static long positiveLong(String raw, String name) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + name + ": " + raw, error);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private record Options(
            Path game,
            Path launcher,
            Path index,
            Path cacheDirectory,
            int workers,
            long memoryBudgetBytes) {
    }
}
