package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BuiltInPreparedImageTargetTest {
    @AfterEach
    void resetBridge() {
        PreparedImageBridge.resetForTests();
    }

    @Test
    void builtInTargetMatchesReviewedProbeIdentity() {
        AdapterTargetRegistry registry = AdapterTargetRegistry.builtInPreparedImage();
        assertEquals(1, registry.targets().size());
        AdapterTarget target = registry.targets().get(0);

        assertEquals("com/fs/graphics/TextureLoader", target.internalClassName());
        assertEquals(
                "d8fcb4cb90d457fc3075e711b6293940774dcf990ea66a7584c231bd96898b50",
                target.sha256());
        assertEquals(PreparedImageTransformation.PLAN_ID, target.planId());
        assertEquals("STARSECTOR_CORE", target.sourceKind());
        assertEquals("contents/resources/java/fs.common_obf.jar", target.sourceSuffix());
        assertEquals("jdk/internal/loader/ClassLoaders$AppClassLoader", target.loaderClass());
        assertEquals("app", target.loaderName());
        assertEquals(1, target.requiredMethods().size());
        assertEquals(PreparedImageTransformation.METHOD_NAME, target.requiredMethods().get(0).name());
        assertEquals(PreparedImageTransformation.METHOD_DESCRIPTOR, target.requiredMethods().get(0).descriptor());
        assertTrue(target.hasLiveSourceBinding());
    }

    @Test
    void compiledPlanRemainsUnavailableWithoutValidatedCacheContext() {
        PreparedImageBridge.resetForTests();

        assertFalse(AdapterTransformationRegistry.hasPlan(PreparedImageTransformation.PLAN_ID));
        assertFalse(AdapterTransformationRegistry.hasLivePlans());
    }
}
