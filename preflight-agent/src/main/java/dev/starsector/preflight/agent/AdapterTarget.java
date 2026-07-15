package dev.starsector.preflight.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Exact class, method, source, and loader identity required before a transformation may be selected. */
record AdapterTarget(
        String id,
        String internalClassName,
        String sha256,
        String planId,
        List<RequiredMethod> requiredMethods,
        String sourceKind,
        String sourceSuffix,
        String sourceSha256,
        String loaderClass,
        String loaderName) {
    AdapterTarget(
            String id,
            String internalClassName,
            String sha256,
            String planId,
            List<RequiredMethod> requiredMethods) {
        this(id, internalClassName, sha256, planId, requiredMethods, "", "", "", "", "");
    }

    AdapterTarget {
        id = requireText(id, "id");
        internalClassName = requireText(internalClassName, "internalClassName").replace('.', '/');
        sha256 = requireSha256(sha256, "Target class SHA-256");
        planId = requireText(planId, "planId");
        requiredMethods = List.copyOf(requiredMethods);
        if (requiredMethods.isEmpty()) {
            throw new IllegalArgumentException("Adapter target requires at least one method signature");
        }
        sourceKind = optional(sourceKind).toUpperCase(Locale.ROOT);
        sourceSuffix = normalizePath(optional(sourceSuffix));
        sourceSha256 = optional(sourceSha256).isBlank()
                ? ""
                : requireSha256(sourceSha256, "Target source SHA-256");
        loaderClass = normalizeClass(optional(loaderClass));
        loaderName = optional(loaderName);
    }

    Match match(ClassSignature actual) {
        return match(actual, AdapterSourceIdentity.unknown());
    }

    Match match(ClassSignature actual, AdapterSourceIdentity source) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(source, "source");
        List<String> problems = new ArrayList<>();
        if (!internalClassName.equals(actual.internalName())) {
            problems.add("class name differs");
        }
        if (!sha256.equals(actual.sha256())) {
            problems.add("class SHA-256 differs");
        }
        for (RequiredMethod method : requiredMethods) {
            if (!actual.hasMethod(method.name(), method.descriptor())) {
                problems.add("missing method " + method.name() + method.descriptor());
            }
        }
        if (!sourceKind.isBlank() && !sourceKind.equals(source.sourceKind())) {
            problems.add("source kind differs: expected " + sourceKind + ", found " + source.sourceKind());
        }
        if (!sourceSuffix.isBlank() && !source.sourceEndsWith(sourceSuffix)) {
            problems.add("code-source suffix differs: expected " + sourceSuffix);
        }
        if (!sourceSha256.isBlank()) {
            if (!source.sourceHashProblem().isBlank()) {
                problems.add("code-source SHA-256 unavailable: " + source.sourceHashProblem());
            } else if (!sourceSha256.equals(source.sourceSha256())) {
                problems.add("code-source SHA-256 differs");
            }
        }
        if (!loaderClass.isBlank() && !loaderClass.equals(source.loaderClass())) {
            problems.add("classloader class differs: expected " + loaderClass + ", found " + source.loaderClass());
        }
        if (!loaderName.isBlank() && !loaderName.equals(source.loaderName())) {
            problems.add("classloader name differs: expected " + loaderName + ", found " + source.loaderName());
        }
        return new Match(problems.isEmpty(), List.copyOf(problems));
    }

    boolean requiresSourceHash() {
        return !sourceSha256.isBlank();
    }

    boolean hasLiveSourceBinding() {
        return !sourceKind.isBlank() && !sourceSuffix.isBlank() && !loaderClass.isBlank();
    }

    List<String> missingLiveSourceBindings() {
        List<String> missing = new ArrayList<>();
        if (sourceKind.isBlank()) missing.add("source-kind");
        if (sourceSuffix.isBlank()) missing.add("source-suffix");
        if (loaderClass.isBlank()) missing.add("loader-class");
        return List.copyOf(missing);
    }

    private static String requireSha256(String value, String name) {
        String normalized = requireText(value, name).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must contain exactly 64 hex characters");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " may not be blank");
        }
        return trimmed;
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePath(String value) {
        String normalized = optional(value).replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeClass(String value) {
        String normalized = optional(value);
        if (normalized.startsWith("<") && normalized.endsWith(">")) {
            return normalized;
        }
        return normalized.replace('.', '/');
    }

    record RequiredMethod(String name, String descriptor) {
        RequiredMethod {
            name = requireText(name, "method name");
            descriptor = requireText(descriptor, "method descriptor");
        }
    }

    record Match(boolean exact, List<String> problems) {
        Match {
            problems = List.copyOf(problems);
        }
    }
}
