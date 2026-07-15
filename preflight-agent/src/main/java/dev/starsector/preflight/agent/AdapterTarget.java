package dev.starsector.preflight.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact class and method signature required before a runtime transformation may be selected. */
record AdapterTarget(
        String id,
        String internalClassName,
        String sha256,
        String planId,
        List<RequiredMethod> requiredMethods) {
    AdapterTarget {
        id = requireText(id, "id");
        internalClassName = requireText(internalClassName, "internalClassName").replace('.', '/');
        sha256 = requireText(sha256, "sha256").toLowerCase();
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Target SHA-256 must contain exactly 64 lowercase hex characters");
        }
        planId = requireText(planId, "planId");
        requiredMethods = List.copyOf(requiredMethods);
        if (requiredMethods.isEmpty()) {
            throw new IllegalArgumentException("Adapter target requires at least one method signature");
        }
    }

    Match match(ClassSignature actual) {
        Objects.requireNonNull(actual, "actual");
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
        return new Match(problems.isEmpty(), List.copyOf(problems));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " may not be blank");
        }
        return trimmed;
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