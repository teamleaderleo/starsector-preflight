package dev.starsector.preflight.agent;

/** Registry for exact target-specific bytecode rewrites. */
final class AdapterTransformationRegistry {
    private AdapterTransformationRegistry() {
    }

    static byte[] transform(AdapterTarget target, ClassSignature signature, byte[] originalBytes) {
        if (PreparedImageTransformation.PLAN_ID.equals(target.planId())) {
            return PreparedImageTransformation.transform(target, signature, originalBytes);
        }
        return null;
    }

    static boolean hasPlan(String planId) {
        return PreparedImageTransformation.PLAN_ID.equals(planId);
    }

    static boolean hasLivePlans() {
        return true;
    }
}
