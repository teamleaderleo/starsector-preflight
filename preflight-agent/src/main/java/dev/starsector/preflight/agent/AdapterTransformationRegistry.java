package dev.starsector.preflight.agent;

/** Registry for manually reviewed target-specific bytecode rewrites. */
final class AdapterTransformationRegistry {
    private AdapterTransformationRegistry() {
    }

    static byte[] transform(AdapterTarget target, ClassSignature signature, byte[] originalBytes) {
        if (!TextureCompatibilityRuntime.PLAN_ID.equals(target.planId())
                || !TextureCompatibilityRuntime.ready()) {
            return null;
        }
        return TextureCompatibilityPlan.transform(signature, originalBytes);
    }

    static boolean hasPlan(String planId) {
        return TextureCompatibilityRuntime.PLAN_ID.equals(planId)
                && TextureCompatibilityRuntime.ready();
    }

    static boolean anyPlanCompiled() {
        return true;
    }
}
