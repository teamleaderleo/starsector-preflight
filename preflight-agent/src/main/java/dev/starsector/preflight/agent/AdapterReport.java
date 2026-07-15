package dev.starsector.preflight.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe bounded diagnostics for adapter probing and activation. */
final class AdapterReport {
    private static final int CANDIDATE_LIMIT = 500;
    private static final int RANKED_CANDIDATE_LIMIT = 50;
    private static final int DIAGNOSTIC_LIMIT = 200;
    private static final int EVALUATION_LIMIT = 200;
    private static final Comparator<Candidate> CANDIDATE_RANKING = Comparator
            .comparingInt(Candidate::relevanceScore)
            .reversed()
            .thenComparing(Candidate::className)
            .thenComparing(Candidate::sha256);

    private final AdapterMode mode;
    private final Path destination;
    private final Path targetFile;
    private final List<String> prefixes;
    private final Instant startedAt = Instant.now();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final List<Evaluation> evaluations = new ArrayList<>();
    private boolean transformerInstalled;
    private boolean killSwitchActive;
    private int registryTargets;
    private long observedClasses;
    private long parsedClasses;
    private long malformedClasses;
    private long exactMatches;
    private long transformationEligible;
    private long transformationsApplied;
    private long containedFailures;
    private boolean candidateTruncated;
    private boolean diagnosticsTruncated;
    private boolean evaluationsTruncated;

    AdapterReport(AdapterMode mode, Path destination, Path targetFile, List<String> prefixes) {
        this.mode = mode;
        this.destination = destination.toAbsolutePath().normalize();
        this.targetFile = targetFile == null ? null : targetFile.toAbsolutePath().normalize();
        this.prefixes = List.copyOf(prefixes);
    }

    synchronized void transformerInstalled(int targets) {
        transformerInstalled = true;
        registryTargets = targets;
    }

    synchronized void killSwitch(String detail) {
        killSwitchActive = true;
        diagnostic(detail);
    }

    synchronized void observed(ClassSignature signature, ProtectionDomain domain) {
        observedClasses++;
        parsedClasses++;
        String key = signature.internalName() + "@" + signature.sha256();
        if (candidates.containsKey(key)) {
            return;
        }

        String source = codeSource(domain);
        AdapterCandidateScorer.Score score = AdapterCandidateScorer.score(signature, source);
        Candidate candidate = new Candidate(
                signature.internalName(),
                signature.sha256(),
                signature.majorVersion(),
                signature.methods().size(),
                source,
                score.value(),
                score.sourceKind(),
                score.evidence(),
                score.relevantMethods(),
                score.methodsTruncated());
        if (candidates.size() < CANDIDATE_LIMIT) {
            candidates.put(key, candidate);
            return;
        }

        candidateTruncated = true;
        Map.Entry<String, Candidate> worst = candidates.entrySet().stream()
                .max((left, right) -> CANDIDATE_RANKING.compare(left.getValue(), right.getValue()))
                .orElseThrow();
        if (CANDIDATE_RANKING.compare(candidate, worst.getValue()) < 0) {
            candidates.remove(worst.getKey());
            candidates.put(key, candidate);
        }
    }

    synchronized void malformed(String className, Throwable error) {
        observedClasses++;
        malformedClasses++;
        containedFailures++;
        diagnostic("Could not parse " + className + ": " + message(error));
    }

    synchronized void evaluation(AdapterTarget target, AdapterTarget.Match match) {
        if (match.exact()) {
            exactMatches++;
        }
        if (evaluations.size() >= EVALUATION_LIMIT) {
            evaluationsTruncated = true;
        } else {
            evaluations.add(new Evaluation(target.id(), target.internalClassName(), target.planId(), match.exact(), match.problems()));
        }
    }

    synchronized void eligible(AdapterTarget target) {
        transformationEligible++;
        diagnostic("Exact target " + target.id() + " matched, but plan " + target.planId()
                + " is not registered in this build; original bytes retained");
    }

    synchronized void transformed(AdapterTarget target) {
        transformationsApplied++;
        diagnostic("Applied transformation plan " + target.planId() + " to " + target.internalClassName());
    }

    synchronized void contained(String detail, Throwable error) {
        containedFailures++;
        diagnostic(detail + ": " + message(error));
    }

    synchronized void diagnostic(String detail) {
        if (diagnostics.size() >= DIAGNOSTIC_LIMIT) {
            diagnosticsTruncated = true;
        } else {
            diagnostics.add(detail == null ? "" : detail);
        }
    }

    synchronized void write() throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = toJson();
        Path temporary = destination.resolveSibling(destination.getFileName()
                + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private String toJson() {
        List<Candidate> orderedCandidates = candidates.values().stream()
                .sorted(Comparator.comparing(Candidate::className).thenComparing(Candidate::sha256))
                .toList();
        List<Candidate> rankedCandidates = candidates.values().stream()
                .filter(candidate -> candidate.relevanceScore() > 0)
                .sorted(CANDIDATE_RANKING)
                .limit(RANKED_CANDIDATE_LIMIT)
                .toList();
        StringBuilder output = new StringBuilder(16_384).append('{');
        field(output, "generatedAt", Instant.now().toString());
        field(output, "startedAt", startedAt.toString());
        field(output, "mode", mode.name());
        field(output, "destination", destination.toString());
        nullableField(output, "targetFile", targetFile == null ? null : targetFile.toString());
        arrayField(output, "candidatePrefixes", prefixes);
        booleanField(output, "transformerInstalled", transformerInstalled);
        booleanField(output, "killSwitchActive", killSwitchActive);
        numberField(output, "registryTargets", registryTargets);
        numberField(output, "observedClasses", observedClasses);
        numberField(output, "parsedClasses", parsedClasses);
        numberField(output, "retainedCandidates", orderedCandidates.size());
        numberField(output, "rankedCandidatesCount", rankedCandidates.size());
        numberField(output, "malformedClasses", malformedClasses);
        numberField(output, "exactMatches", exactMatches);
        numberField(output, "transformationEligible", transformationEligible);
        numberField(output, "transformationsApplied", transformationsApplied);
        numberField(output, "containedFailures", containedFailures);
        booleanField(output, "liveTransformationPlansRegistered", false);
        booleanField(output, "candidateTruncated", candidateTruncated);
        booleanField(output, "diagnosticsTruncated", diagnosticsTruncated);
        booleanField(output, "evaluationsTruncated", evaluationsTruncated);

        key(output, "rankedCandidates").append('[');
        for (int i = 0; i < rankedCandidates.size(); i++) {
            if (i > 0) output.append(',');
            writeRankedCandidate(output, rankedCandidates.get(i));
        }
        output.append("],");

        key(output, "candidates").append('[');
        for (int i = 0; i < orderedCandidates.size(); i++) {
            if (i > 0) output.append(',');
            writeCandidateSummary(output, orderedCandidates.get(i));
        }
        output.append("],");

        key(output, "evaluations").append('[');
        for (int i = 0; i < evaluations.size(); i++) {
            if (i > 0) output.append(',');
            Evaluation evaluation = evaluations.get(i);
            output.append('{');
            field(output, "targetId", evaluation.targetId());
            field(output, "className", evaluation.className());
            field(output, "planId", evaluation.planId());
            booleanField(output, "exact", evaluation.exact());
            arrayField(output, "problems", evaluation.problems());
            trimComma(output).append('}');
        }
        output.append("],");
        arrayField(output, "diagnostics", diagnostics);
        return trimComma(output).append('}').toString();
    }

    private static void writeCandidateSummary(StringBuilder output, Candidate candidate) {
        output.append('{');
        field(output, "className", candidate.className());
        field(output, "sha256", candidate.sha256());
        numberField(output, "majorVersion", candidate.majorVersion());
        numberField(output, "methodCount", candidate.methodCount());
        nullableField(output, "codeSource", candidate.codeSource());
        field(output, "sourceKind", candidate.sourceKind());
        numberField(output, "relevanceScore", candidate.relevanceScore());
        trimComma(output).append('}');
    }

    private static void writeRankedCandidate(StringBuilder output, Candidate candidate) {
        output.append('{');
        field(output, "className", candidate.className());
        field(output, "sha256", candidate.sha256());
        numberField(output, "majorVersion", candidate.majorVersion());
        numberField(output, "methodCount", candidate.methodCount());
        nullableField(output, "codeSource", candidate.codeSource());
        field(output, "sourceKind", candidate.sourceKind());
        numberField(output, "relevanceScore", candidate.relevanceScore());
        arrayField(output, "evidence", candidate.evidence());
        booleanField(output, "methodsTruncated", candidate.methodsTruncated());
        key(output, "relevantMethods").append('[');
        for (int i = 0; i < candidate.relevantMethods().size(); i++) {
            if (i > 0) output.append(',');
            AdapterCandidateScorer.RelevantMethod method = candidate.relevantMethods().get(i);
            output.append('{');
            field(output, "name", method.name());
            field(output, "descriptor", method.descriptor());
            numberField(output, "access", method.access());
            numberField(output, "score", method.score());
            arrayField(output, "evidence", method.evidence());
            trimComma(output).append('}');
        }
        output.append("],");
        trimComma(output).append('}');
    }

    private static String codeSource(ProtectionDomain domain) {
        try {
            if (domain != null && domain.getCodeSource() != null && domain.getCodeSource().getLocation() != null) {
                return domain.getCodeSource().getLocation().toString();
            }
        } catch (RuntimeException ignored) {
            // Protection-domain metadata is diagnostic only.
        }
        return null;
    }

    private static StringBuilder key(StringBuilder output, String name) {
        return output.append(quote(name)).append(':');
    }

    private static void field(StringBuilder output, String name, String value) {
        key(output, name).append(quote(value)).append(',');
    }

    private static void nullableField(StringBuilder output, String name, String value) {
        key(output, name).append(value == null ? "null" : quote(value)).append(',');
    }

    private static void numberField(StringBuilder output, String name, long value) {
        key(output, name).append(value).append(',');
    }

    private static void booleanField(StringBuilder output, String name, boolean value) {
        key(output, name).append(value).append(',');
    }

    private static void arrayField(StringBuilder output, String name, List<String> values) {
        key(output, name).append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) output.append(',');
            output.append(quote(values.get(i)));
        }
        output.append("],");
    }

    private static StringBuilder trimComma(StringBuilder output) {
        if (!output.isEmpty() && output.charAt(output.length() - 1) == ',') {
            output.setLength(output.length() - 1);
        }
        return output;
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        StringBuilder output = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (c < 0x20) output.append(String.format("\\u%04x", (int) c));
                    else output.append(c);
                }
            }
        }
        return output.append('"').toString();
    }

    private static String message(Throwable error) {
        String text = error.getMessage();
        return error.getClass().getSimpleName() + (text == null || text.isBlank() ? "" : ": " + text);
    }

    private record Candidate(
            String className,
            String sha256,
            int majorVersion,
            int methodCount,
            String codeSource,
            int relevanceScore,
            String sourceKind,
            List<String> evidence,
            List<AdapterCandidateScorer.RelevantMethod> relevantMethods,
            boolean methodsTruncated) {
        private Candidate {
            evidence = List.copyOf(evidence);
            relevantMethods = List.copyOf(relevantMethods);
        }
    }

    private record Evaluation(String targetId, String className, String planId, boolean exact, List<String> problems) {
        private Evaluation {
            problems = List.copyOf(problems);
        }
    }
}
