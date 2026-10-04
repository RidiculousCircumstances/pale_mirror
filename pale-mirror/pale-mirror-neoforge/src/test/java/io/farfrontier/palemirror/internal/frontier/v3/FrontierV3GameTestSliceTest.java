package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3GameTestSliceTest {
    @Test void localNavigationSliceRetainsTheActualMovementBoundary() {
        assertTrue(FrontierV3GameTestSlice.includes("route-construction", "pm-frontier-v3-scene-route-construction"));
        assertFalse(FrontierV3GameTestSlice.includes("route-construction", "pm-frontier-v3-scene-local-navigation"));
        assertTrue(FrontierV3GameTestSlice.includes("local-navigation", "pm-frontier-v3-scene-local-navigation"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-local-navigation"));
        assertFalse(FrontierV3GameTestSlice.includes("local-navigation", "pm-frontier-v3-scene-cargo"));
    }
    @Test void pendingProjectionRemainsInEconomyReferenceAndFullGates() {
        String batch = "pm-frontier-v3-reference-projection";
        assertTrue(FrontierV3GameTestSlice.includes("reference-projection", batch));
        assertTrue(FrontierV3GameTestSlice.includes("reference-all", batch));
        assertTrue(FrontierV3GameTestSlice.includes("economy", batch));
        assertTrue(FrontierV3GameTestSlice.includes("", batch));
        assertFalse(FrontierV3GameTestSlice.includes("reference-projection", "pm-frontier-v3-production"));
    }
    @Test void productionWorkSliceRetainsTheRegisteredWorkerBatchOnly() {
        assertTrue(FrontierV3GameTestSlice.includes("production-effect", "pm-frontier-v3-production"));
        assertTrue(FrontierV3GameTestSlice.includes("resource-prefix", "pm-frontier-v3-resource-site-prefix"));
        assertFalse(FrontierV3GameTestSlice.includes("resource-prefix", "pm-frontier-v3-production"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-production"));
        assertFalse(FrontierV3GameTestSlice.includes("production-effect", "pm-frontier-v3-scene-production-work"));
        assertTrue(FrontierV3GameTestSlice.includes("production-work", "pm-frontier-v3-scene-production-work"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-production-work"));
        assertFalse(FrontierV3GameTestSlice.includes("production-work", "pm-frontier-v3-scene-cargo"));
    }
    @Test
    void cargoSliceRunsTheActualCarrierBoundaryWithoutOtherSceneFamilies() {
        assertTrue(FrontierV3GameTestSlice.includes("cargo", "pm-frontier-v3-scene-cargo"));
        assertFalse(FrontierV3GameTestSlice.includes("cargo", "pm-frontier-v3-scene-handoff"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-cargo"));
        assertTrue(FrontierV3GameTestSlice.includes("cargo", "pm-frontier-v3-scene-cargo-authority"));
        assertTrue(FrontierV3GameTestSlice.includes("cargo-authority", "pm-frontier-v3-scene-cargo-authority"));
        assertFalse(FrontierV3GameTestSlice.includes("cargo-authority", "pm-frontier-v3-scene-cargo"));
        assertTrue(FrontierV3GameTestSlice.includes("cargo", "pm-frontier-v3-scene-cargo-interaction"));
        assertTrue(FrontierV3GameTestSlice.includes("cargo-interaction", "pm-frontier-v3-scene-cargo-interaction"));
        assertFalse(FrontierV3GameTestSlice.includes("cargo-interaction", "pm-frontier-v3-scene-cargo-authority"));
        assertTrue(FrontierV3GameTestSlice.includes("scene-departure", "pm-frontier-v3-scene-departure"));
        assertTrue(FrontierV3GameTestSlice.includes("scene-departure", "pm-frontier-v3-scene-deaths"));
        assertTrue(FrontierV3GameTestSlice.includes("body-lifetime", "pm-frontier-v3-scene-body-lifetime"));
        assertTrue(FrontierV3GameTestSlice.includes("scene-departure", "pm-frontier-v3-scene-body-lifetime"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-body-lifetime"));
        assertTrue(FrontierV3GameTestSlice.includes("", "pm-frontier-v3-scene-body-lifetime"));
        assertFalse(FrontierV3GameTestSlice.includes("body-lifetime", "pm-frontier-v3-scene-deaths"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-departure"));
        assertFalse(FrontierV3GameTestSlice.includes("scene-departure", "pm-frontier-v3-scene-cargo"));
    }

    @Test
    void sceneSliceIncludesOnlyTheFastHotColdBatches() {
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-handoff"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-restart-reclaim"));
        assertTrue(FrontierV3GameTestSlice.includes("scene-restart-reclaim", "pm-frontier-v3-scene-restart-reclaim"));
        assertFalse(FrontierV3GameTestSlice.includes("scene-restart-reclaim", "pm-frontier-v3-scene-strikes"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-assembly-grade"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-route-maintenance"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-graybox"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-resource-observation"));
        assertFalse(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-resource-harvest"));
        assertFalse(FrontierV3GameTestSlice.includes("scene", "core-integration"));
    }

    @Test
    void economySliceIncludesExactItemAndStoreProofsButNotCombatScenes() {
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-exact-consumption"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-object-boards"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-equipment-issue"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-equipment-return"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-equipment-death"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-container-recovery"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-scene-explosion"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-resource-harvest"));
    }

    @Test
    void ambientPhysicsSliceContainsOnlyTheExactNativeCollisionBoundary() {
        assertTrue(FrontierV3GameTestSlice.includes("ambient-physics", "pm-frontier-v3-ambient-physics"));
        assertFalse(FrontierV3GameTestSlice.includes("ambient-physics", "pm-frontier-v3-ambient-local-brain"));
        assertFalse(FrontierV3GameTestSlice.includes("ambient-physics", "pm-frontier-v3-scene-handoff"));
    }

    @Test
    void emptySliceCannotAccidentallyFilterTheFullGateAndUnknownSlicesFailClosed() {
        assertTrue(FrontierV3GameTestSlice.includes("", "core-integration"));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3GameTestSlice.includes("all", "pm-frontier-v3-scene-handoff"));
    }

}
