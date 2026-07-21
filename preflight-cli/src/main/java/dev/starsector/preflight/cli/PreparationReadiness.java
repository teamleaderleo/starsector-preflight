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
        readiness.put("preparedPixelsAdapter", "fail-closed-pending-color-transfer-repair");
        readiness.put("textureAdapterModes", List.of("compatibility", "prepared-pixels"));
        readiness.put("realInstallPilotRequired", false);
        readiness.put("fastRenderingAdapter", "requires-exact-compatible-method-evidence");
        readiness.put("launchAccelerationClaimed", false);
        return Collections.unmodifiableMap(readiness);
    }
}
