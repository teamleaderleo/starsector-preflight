package dev.starsector.preflight.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ranks observed classes as likely image or texture-loading integration points. */
final class AdapterCandidateScorer {
    private static final int RELEVANT_METHOD_LIMIT = 16;
    private static final int METHOD_SCORE_CONTRIBUTION_LIMIT = 5;

    private AdapterCandidateScorer() {
    }

    static Score score(ClassSignature signature, String codeSource) {
        String className = signature.internalName().toLowerCase(Locale.ROOT);
        Set<String> evidence = new LinkedHashSet<>();
        int classScore = 0;
        classScore += tokenScore(className, "texture", 60, "class name contains texture", evidence);
        classScore += tokenScore(className, "image", 45, "class name contains image", evidence);
        classScore += tokenScore(className, "sprite", 40, "class name contains sprite", evidence);
        classScore += tokenScore(className, "atlas", 35, "class name contains atlas", evidence);
        classScore += tokenScore(className, "graphic", 25, "class name contains graphic", evidence);
        classScore += tokenScore(className, "asset", 18, "class name contains asset", evidence);
        classScore += tokenScore(className, "loader", 18, "class name contains loader", evidence);
        classScore += tokenScore(className, "render", 12, "class name contains render", evidence);
        classScore += tokenScore(className, "resource", 10, "class name contains resource", evidence);
        classScore += tokenScore(className, "icon", 8, "class name contains icon", evidence);
        if (className.contains("test") || className.contains("synthetic")) {
            classScore -= 100;
            evidence.add("synthetic/test class penalty");
        }

        String sourceKind = sourceKind(codeSource);
        if ("STARSECTOR_CORE".equals(sourceKind)) {
            classScore += 10;
            evidence.add("loaded from Starsector core");
        } else if ("FAST_RENDERING".equals(sourceKind)) {
            classScore += 8;
            evidence.add("loaded from Fast Rendering");
        }

        List<MethodScore> methodScores = new ArrayList<>();
        for (ClassSignature.Method method : signature.methods()) {
            MethodScore methodScore = scoreMethod(method);
            if (methodScore.score() > 0) {
                methodScores.add(methodScore);
            }
        }
        methodScores.sort(Comparator.comparingInt(MethodScore::score)
                .reversed()
                .thenComparing(score -> score.method().name())
                .thenComparing(score -> score.method().descriptor())
                .thenComparingInt(score -> score.method().access()));

        int score = Math.max(0, classScore);
        for (int i = 0; i < Math.min(METHOD_SCORE_CONTRIBUTION_LIMIT, methodScores.size()); i++) {
            MethodScore method = methodScores.get(i);
            score += method.score();
            evidence.add("method " + method.method().name() + method.method().descriptor()
                    + " scored " + method.score());
        }

        List<RelevantMethod> relevantMethods = methodScores.stream()
                .limit(RELEVANT_METHOD_LIMIT)
                .map(value -> new RelevantMethod(
                        value.method().name(),
                        value.method().descriptor(),
                        value.method().access(),
                        value.score(),
                        value.evidence()))
                .toList();
        return new Score(
                score,
                sourceKind,
                List.copyOf(evidence),
                relevantMethods,
                methodScores.size() > RELEVANT_METHOD_LIMIT);
    }

    private static MethodScore scoreMethod(ClassSignature.Method method) {
        String name = method.name().toLowerCase(Locale.ROOT);
        String descriptor = method.descriptor().toLowerCase(Locale.ROOT);
        Set<String> evidence = new LinkedHashSet<>();
        int score = 0;
        score += tokenScore(name, "texture", 35, "method name contains texture", evidence);
        score += tokenScore(name, "image", 30, "method name contains image", evidence);
        score += tokenScore(name, "sprite", 30, "method name contains sprite", evidence);
        score += tokenScore(name, "upload", 35, "method name contains upload", evidence);
        score += tokenScore(name, "decode", 30, "method name contains decode", evidence);
        score += tokenScore(name, "mipmap", 25, "method name contains mipmap", evidence);
        score += tokenScore(name, "atlas", 25, "method name contains atlas", evidence);
        score += tokenScore(name, "bind", 15, "method name contains bind", evidence);
        score += tokenScore(name, "load", 12, "method name contains load", evidence);
        score += tokenScore(name, "read", 8, "method name contains read", evidence);
        score += tokenScore(name, "create", 5, "method name contains create", evidence);
        score += tokenScore(descriptor, "java/awt/image/bufferedimage", 40,
                "descriptor references BufferedImage", evidence);
        score += tokenScore(descriptor, "java/nio/bytebuffer", 25,
                "descriptor references ByteBuffer", evidence);
        score += tokenScore(descriptor, "java/io/inputstream", 12,
                "descriptor references InputStream", evidence);
        score += tokenScore(descriptor, "org/lwjgl/opengl", 25,
                "descriptor references LWJGL OpenGL", evidence);
        score += tokenScore(descriptor, "texture", 15,
                "descriptor references a texture type", evidence);
        score += tokenScore(descriptor, "image", 12,
                "descriptor references an image type", evidence);
        if (descriptor.contains("java/lang/string")) {
            score += 3;
            evidence.add("descriptor accepts or returns String");
        }
        return new MethodScore(method, score, List.copyOf(evidence));
    }

    private static int tokenScore(
            String text,
            String token,
            int points,
            String reason,
            Set<String> evidence) {
        if (!text.contains(token)) {
            return 0;
        }
        evidence.add(reason);
        return points;
    }

    private static String sourceKind(String codeSource) {
        if (codeSource == null || codeSource.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = codeSource.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("fast-render") || normalized.contains("fastrender")
                || normalized.contains("fast_render")) {
            return "FAST_RENDERING";
        }
        if (normalized.contains("/mods/")) {
            return "MOD";
        }
        if (normalized.contains("starsector-core") || normalized.contains("starfarer_obf")
                || normalized.contains("starsector")) {
            return "STARSECTOR_CORE";
        }
        return "OTHER";
    }

    record Score(
            int value,
            String sourceKind,
            List<String> evidence,
            List<RelevantMethod> relevantMethods,
            boolean methodsTruncated) {
        Score {
            evidence = List.copyOf(evidence);
            relevantMethods = List.copyOf(relevantMethods);
        }
    }

    record RelevantMethod(
            String name,
            String descriptor,
            int access,
            int score,
            List<String> evidence) {
        RelevantMethod {
            evidence = List.copyOf(evidence);
        }
    }

    private record MethodScore(ClassSignature.Method method, int score, List<String> evidence) {
    }
}
