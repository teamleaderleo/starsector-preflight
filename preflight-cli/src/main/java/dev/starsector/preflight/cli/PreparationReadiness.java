package dev.starsector.preflight.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Current machine-readable runtime readiness emitted by the preparation report. */
final class PreparationReadiness {
    private PreparationReadiness() {
    }

    static Map<String, Object> toMap(boolean allEnabledStagesSuccessful) {
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("cacheArtifactsPrepared", allEnabledStagesSuccessful);
        readiness.put("liveAdapterIntegrated", true);
        readiness.put("liveAdapterEnabledByPreparation", false);
        readiness.put("vanillaAdapter", "compatibility-v2-behaviorally-accepted");
        readiness.put("compatibilityBehavioralAcceptance", "accepted-2026-07-19-starsector-0.98a-rc8");
        readiness.put("repeatTimingCampaignRequired", true);
        readiness.put("preparedPixelsAdapter", "pot-bypass-enabled-npot-coherent-direct-gameplay-smoke-diagnostic");
        readiness.put("preparedPixelsBehavioralAcceptance", "launcher-accepted-2026-07-23-gameplay-smoke-required");
        readiness.put("textureAdapterModes", List.of("compatibility", "prepared-pixels"));
        readiness.put("realInstallPilotRequired", true);
        readiness.put("preparedPixelsNextOperatorAction", "single-coherent-direct-gameplay-smoke");
        readiness.put("fastRenderingAdapter", "requires-exact-compatible-method-evidence");
        readiness.put("launchAccelerationClaimed", false);
        return Collections.unmodifiableMap(readiness);
    }
}
