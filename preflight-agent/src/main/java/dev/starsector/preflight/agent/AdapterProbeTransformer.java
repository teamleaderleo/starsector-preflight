package dev.starsector.preflight.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Objects;

/** Observes candidate classes and only delegates exact source-bound targets to a registered plan. */
final class AdapterProbeTransformer implements ClassFileTransformer {
    private final AdapterMode mode;
    private final AdapterTargetRegistry registry;
    private final List<String> candidatePrefixes;
    private final AdapterReport report;
    private final CodeLoaderSignatureReport codeLoaderReport;
    private final AudioDecoderSignatureReport audioDecoderReport;

    AdapterProbeTransformer(
            AdapterMode mode,
            AdapterTargetRegistry registry,
            List<String> candidatePrefixes,
            AdapterReport report) {
        this(mode, registry, candidatePrefixes, report, null, null);
    }

    AdapterProbeTransformer(
            AdapterMode mode,
            AdapterTargetRegistry registry,
            List<String> candidatePrefixes,
            AdapterReport report,
            CodeLoaderSignatureReport codeLoaderReport) {
        this(mode, registry, candidatePrefixes, report, codeLoaderReport, null);
    }

    AdapterProbeTransformer(
            AdapterMode mode,
            AdapterTargetRegistry registry,
            List<String> candidatePrefixes,
            AdapterReport report,
            CodeLoaderSignatureReport codeLoaderReport,
            AudioDecoderSignatureReport audioDecoderReport) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.candidatePrefixes = List.copyOf(candidatePrefixes);
        this.report = Objects.requireNonNull(report, "report");
        this.codeLoaderReport = codeLoaderReport;
        this.audioDecoderReport = audioDecoderReport;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        boolean adapterCandidate = adapterCandidate(className);
        boolean codeCandidate = codeLoaderReport != null && codeLoaderReport.interested(className);
        boolean audioCandidate = audioDecoderReport != null && audioDecoderReport.interested(className);
        if (mode == AdapterMode.OFF
                || className == null
                || classfileBuffer == null
                || (!adapterCandidate && !codeCandidate && !audioCandidate)) {
            return null;
        }
        try {
            ClassSignature signature = ClassSignature.parse(classfileBuffer);
            List<AdapterTarget> targets = registry.forClass(signature.internalName());
            boolean hashSource = codeCandidate
                    || audioCandidate
                    || targets.stream().anyMatch(AdapterTarget::requiresSourceHash);
            AdapterSourceIdentity source = AdapterSourceIdentity.capture(loader, protectionDomain, hashSource);
            if (adapterCandidate) {
                report.observed(signature, source);
            }
            if (codeCandidate) {
                codeLoaderReport.observed(signature, source);
            }
            if (audioCandidate) {
                audioDecoderReport.observed(signature, source);
            }
            for (AdapterTarget target : targets) {
                AdapterTarget.Match match = target.match(signature, source);
                report.evaluation(target, match);
                if (!match.exact() || mode != AdapterMode.ENABLED) {
                    continue;
                }
                if (!target.hasLiveSourceBinding()) {
                    report.unbound(target);
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
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            if (adapterCandidate) {
                report.malformed(className, error);
            }
            if (codeCandidate) {
                codeLoaderReport.contained("Could not retain code-loader identity " + className, error);
            }
            if (audioCandidate) {
                audioDecoderReport.contained("Could not retain audio-decoder identity " + className, error);
            }
        }
        return null;
    }

    private boolean adapterCandidate(String className) {
        if (className == null) return false;
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
