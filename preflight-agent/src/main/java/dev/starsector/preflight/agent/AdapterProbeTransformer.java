package dev.starsector.preflight.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Objects;

/** Observes candidate classes and only delegates exact allowlisted targets to a registered plan. */
final class AdapterProbeTransformer implements ClassFileTransformer {
    private final AdapterMode mode;
    private final AdapterTargetRegistry registry;
    private final List<String> candidatePrefixes;
    private final AdapterReport report;

    AdapterProbeTransformer(
            AdapterMode mode,
            AdapterTargetRegistry registry,
            List<String> candidatePrefixes,
            AdapterReport report) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.candidatePrefixes = List.copyOf(candidatePrefixes);
        this.report = Objects.requireNonNull(report, "report");
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (mode == AdapterMode.OFF || className == null || classfileBuffer == null || !candidate(className)) {
            return null;
        }
        try {
            ClassSignature signature = ClassSignature.parse(classfileBuffer);
            report.observed(signature, protectionDomain);
            for (AdapterTarget target : registry.forClass(signature.internalName())) {
                AdapterTarget.Match match = target.match(signature);
                report.evaluation(target, match);
                if (!match.exact() || mode != AdapterMode.ENABLED) {
                    continue;
                }
                if (!AdapterTransformationRegistry.hasPlan(target.planId())) {
                    report.eligible(target);
                    continue;
                }
                byte[] transformed = AdapterTransformationRegistry.transform(target, signature, classfileBuffer);
                if (transformed != null) {
                    report.transformed(target);
                    return transformed;
                }
            }
        } catch (Throwable error) {
            report.malformed(className, error);
        }
        return null;
    }

    private boolean candidate(String className) {
        if (!registry.forClass(className).isEmpty()) {
            return true;
        }
        for (String prefix : candidatePrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}