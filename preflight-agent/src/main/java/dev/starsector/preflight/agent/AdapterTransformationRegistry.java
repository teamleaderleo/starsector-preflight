package dev.starsector.preflight.agent;

/**
 * Registry for target-specific bytecode rewrites.
 *
 * <p>The safety-gate release intentionally contains no live Starsector transformation plans. A plan
 * added later must still pass the exact target signature checks before this registry is called.</p>
 */
final class AdapterTransformationRegistry {
    private AdapterTransformationRegistry() {
    }

    static byte[] transform(AdapterTarget target, ClassSignature signature, byte[] originalBytes) {
        // No live plans are registered until a real supported Starsector build has been probed.
        return null;
    }

    static boolean hasPlan(String planId) {
        return false;
    }
}