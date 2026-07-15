package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.PreparedTextureValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class TextureCommand {
    private static final int DEFAULT_BENCHMARK_RUNS = 5;
    private static volatile long benchmarkSink;

    private TextureCommand() {
    }

    static int execute(String[] args, int offset) throws Exception {
        if (offset >= args.length) {
            throw new IllegalArgumentException("Expected: texture <prepare|inspect|verify|benchmark> ...");
        }
        return switch (args[offset]) {
            case "prepare" -> prepare(parsePrepare(args, offset + 1));
            case "inspect" -> inspect(requirePath(args, offset + 1, "texture inspect <texture.spft>"));
            case "verify" -> verify(parseVerify(args, offset + 1));
            case "benchmark" -> benchmark(parseBenchmark(args, offset + 1));
            default -> throw new IllegalArgumentException("Unknown texture command: " + args[offset]);
        };
    }

    private static int prepare(PrepareOptions options) throws IOException {
        long prepareStarted = System.nanoTime();
        PreparedTexture texture = BulkTexturePreprocessor.prepare(
                options.source(),
                PreparedTexture.Transformation.IDENTITY);
        long prepareNanos = System.nanoTime() - prepareStarted;
        Path output = options.output() == null
                ? Path.of(System.getProperty("user.home"))
                        .resolve(".starsector-preflight")
                        .resolve("textures")
                        .resolve(texture.sourceSha256() + "-identity.spft")
                : options.output().toAbsolutePath().normalize();

        long writeStarted = System.nanoTime();
        PreparedTextureIO.write(output, texture);
        long writeNanos = System.nanoTime() - writeStarted;

        Map<String, Object> result = summary(texture, output);
        result.put("source", options.source().toAbsolutePath().normalize());
        result.put("preprocessor", "bulk-rows-with-reference-fallback");
        result.put("referenceVerificationAvailable", true);
        result.put("prepareMs", nanosToMillis(prepareNanos));
        result.put("writeMs", nanosToMillis(writeNanos));
        System.out.println(Json.object(result));
        return 0;
    }

    private static int inspect(Path blob) throws IOException {
        PreparedTexture texture = PreparedTextureIO.read(blob);
        System.out.println(Json.object(summary(texture, blob.toAbsolutePath().normalize())));
        return 0;
    }

    private static int verify(VerifyOptions options) throws IOException {
        PreparedTexture cached = PreparedTextureIO.read(options.blob());
        PreparedTextureValidator.Result sourceValidation =
                PreparedTextureValidator.validateSource(options.source(), cached);
        PreparedTexture reference = ReferenceTexturePreprocessor.prepare(
                options.source(),
                cached.transformation());
        boolean equivalent = cached.equals(reference);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", options.source().toAbsolutePath().normalize());
        result.put("blob", options.blob().toAbsolutePath().normalize());
        result.put("verificationPreprocessor", "literal-reference");
        result.put("sourceValid", sourceValidation.valid());
        result.put("expectedSourceSha256", sourceValidation.expectedSha256());
        result.put("actualSourceSha256", sourceValidation.actualSha256());
        result.put("equivalentToReference", equivalent);
        result.put("valid", sourceValidation.valid() && equivalent);
        System.out.println(Json.object(result));
        return sourceValidation.valid() && equivalent ? 0 : 5;
    }

    private static int benchmark(BenchmarkOptions options) throws IOException {
        PreparedTexture cached = PreparedTextureIO.read(options.blob());
        PreparedTextureValidator.Result sourceValidation =
                PreparedTextureValidator.validateSource(options.source(), cached);
        if (!sourceValidation.valid()) {
            throw new IOException("Prepared texture source hash does not match " + options.source());
        }

        // Untimed passes initialize ImageIO and verify all three paths before measurement.
        PreparedTexture warmReference = ReferenceTexturePreprocessor.prepare(
                options.source(),
                cached.transformation());
        PreparedTexture warmBulk = BulkTexturePreprocessor.prepare(
                options.source(),
                cached.transformation());
        PreparedTexture warmCached = PreparedTextureIO.read(options.blob());
        if (!warmReference.equals(warmBulk)) {
            throw new IOException("Bulk texture conversion differs from the literal reference conversion");
        }
        if (!warmReference.equals(warmCached)) {
            throw new IOException("Prepared texture blob differs from the reference conversion");
        }
        benchmarkSink ^= warmReference.pixelBytes() + warmBulk.pixelBytes() + warmCached.pixelBytes();

        long[] referenceNanos = new long[options.runs()];
        long[] bulkNanos = new long[options.runs()];
        long[] readNanos = new long[options.runs()];
        for (int i = 0; i < options.runs(); i++) {
            if ((i & 1) == 0) {
                bulkNanos[i] = measureBulk(options, cached);
                referenceNanos[i] = measureReference(options, cached);
            } else {
                referenceNanos[i] = measureReference(options, cached);
                bulkNanos[i] = measureBulk(options, cached);
            }

            long started = System.nanoTime();
            PreparedTexture restored = PreparedTextureIO.read(options.blob());
            readNanos[i] = System.nanoTime() - started;
            benchmarkSink ^= restored.pixelBytes() + restored.color1Rgba();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", options.source().toAbsolutePath().normalize());
        result.put("blob", options.blob().toAbsolutePath().normalize());
        result.put("runs", options.runs());
        result.put("sourceBytes", Files.size(options.source()));
        result.put("blobBytes", Files.size(options.blob()));
        result.put("pixelBytes", cached.pixelBytes());
        result.put("referencePrepare", timing(referenceNanos));
        result.put("bulkPrepare", timing(bulkNanos));
        result.put("blobRead", timing(readNanos));
        result.put("referenceToBlobMedianSpeedup",
                median(referenceNanos) / (double) Math.max(1L, median(readNanos)));
        result.put("referenceToBulkMedianSpeedup",
                median(referenceNanos) / (double) Math.max(1L, median(bulkNanos)));
        result.put("bulkToBlobMedianSpeedup",
                median(bulkNanos) / (double) Math.max(1L, median(readNanos)));
        // Preserve the original field for downstream report consumers.
        result.put("medianSpeedup",
                median(referenceNanos) / (double) Math.max(1L, median(readNanos)));
        result.put("timingAssertionsEnforced", false);
        System.out.println(Json.object(result));
        return 0;
    }

    private static long measureReference(BenchmarkOptions options, PreparedTexture cached) throws IOException {
        long started = System.nanoTime();
        PreparedTexture prepared = ReferenceTexturePreprocessor.prepare(
                options.source(),
                cached.transformation());
        long elapsed = System.nanoTime() - started;
        benchmarkSink ^= prepared.pixelBytes() + prepared.color0Rgba();
        return elapsed;
    }

    private static long measureBulk(BenchmarkOptions options, PreparedTexture cached) throws IOException {
        long started = System.nanoTime();
        PreparedTexture prepared = BulkTexturePreprocessor.prepare(
                options.source(),
                cached.transformation());
        long elapsed = System.nanoTime() - started;
        benchmarkSink ^= prepared.pixelBytes() + prepared.color2Rgba();
        return elapsed;
    }

    private static Map<String, Object> summary(PreparedTexture texture, Path blob) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blob", blob);
        result.put("blobBytes", Files.isRegularFile(blob) ? Files.size(blob) : null);
        result.put("formatVersion", PreparedTexture.FORMAT_VERSION);
        result.put("sourceSha256", texture.sourceSha256());
        result.put("transformation", texture.transformation());
        result.put("originalWidth", texture.originalWidth());
        result.put("originalHeight", texture.originalHeight());
        result.put("uploadWidth", texture.uploadWidth());
        result.put("uploadHeight", texture.uploadHeight());
        result.put("channels", texture.channels());
        result.put("hasAlpha", texture.hasAlpha());
        result.put("pixelBytes", texture.pixelBytes());
        result.put("color0", color(texture.color0Rgba()));
        result.put("color1", color(texture.color1Rgba()));
        result.put("color2", color(texture.color2Rgba()));
        return result;
    }

    private static Map<String, Object> color(int rgba) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rgbaHex", String.format("%08x", rgba));
        value.put("red", PreparedTexture.red(rgba));
        value.put("green", PreparedTexture.green(rgba));
        value.put("blue", PreparedTexture.blue(rgba));
        value.put("alpha", PreparedTexture.alpha(rgba));
        return value;
    }

    private static Map<String, Object> timing(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        long sum = 0;
        for (long value : sorted) {
            sum += value;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minMs", nanosToMillis(sorted[0]));
        result.put("medianMs", nanosToMillis(medianSorted(sorted)));
        result.put("maxMs", nanosToMillis(sorted[sorted.length - 1]));
        result.put("meanMs", nanosToMillis(sum / sorted.length));
        result.put("samplesMs", Arrays.stream(values).mapToDouble(TextureCommand::nanosToMillis).boxed().toList());
        return result;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return medianSorted(sorted);
    }

    private static long medianSorted(long[] sorted) {
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) {
            return sorted[middle];
        }
        return sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static PrepareOptions parsePrepare(String[] args, int offset) {
        if (offset >= args.length) {
            throw new IllegalArgumentException("Expected: texture prepare <image> [--output <texture.spft>]");
        }
        Path source = Path.of(args[offset]);
        Path output = null;
        for (int i = offset + 1; i < args.length; i++) {
            if (args[i].equals("--output")) {
                output = Path.of(requireValue(args, ++i, "--output"));
            } else {
                throw new IllegalArgumentException("Unknown texture prepare option: " + args[i]);
            }
        }
        return new PrepareOptions(source, output);
    }

    private static VerifyOptions parseVerify(String[] args, int offset) {
        if (offset + 2 != args.length) {
            throw new IllegalArgumentException("Expected: texture verify <image> <texture.spft>");
        }
        return new VerifyOptions(Path.of(args[offset]), Path.of(args[offset + 1]));
    }

    private static BenchmarkOptions parseBenchmark(String[] args, int offset) {
        if (offset + 1 >= args.length) {
            throw new IllegalArgumentException(
                    "Expected: texture benchmark <image> <texture.spft> [--runs <count>]");
        }
        Path source = Path.of(args[offset]);
        Path blob = Path.of(args[offset + 1]);
        int runs = DEFAULT_BENCHMARK_RUNS;
        for (int i = offset + 2; i < args.length; i++) {
            if (args[i].equals("--runs")) {
                String raw = requireValue(args, ++i, "--runs");
                try {
                    runs = Integer.parseInt(raw);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Invalid benchmark run count: " + raw, error);
                }
            } else {
                throw new IllegalArgumentException("Unknown texture benchmark option: " + args[i]);
            }
        }
        if (runs < 1 || runs > 100) {
            throw new IllegalArgumentException("Benchmark runs must be between 1 and 100");
        }
        return new BenchmarkOptions(source, blob, runs);
    }

    private static Path requirePath(String[] args, int offset, String usage) {
        if (offset >= args.length || offset + 1 != args.length) {
            throw new IllegalArgumentException("Expected: " + usage);
        }
        return Path.of(args[offset]);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private record PrepareOptions(Path source, Path output) {
    }

    private record VerifyOptions(Path source, Path blob) {
    }

    private record BenchmarkOptions(Path source, Path blob, int runs) {
    }
}
