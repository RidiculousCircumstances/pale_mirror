package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3GameTestSliceTest {
    @Test
    void sceneSliceIncludesOnlyTheFastHotColdBatches() {
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-handoff"));
        assertTrue(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-scene-restart-reclaim"));
        assertFalse(FrontierV3GameTestSlice.includes("scene", "pm-frontier-v3-resource-harvest"));
        assertFalse(FrontierV3GameTestSlice.includes("scene", "core-integration"));
    }

    @Test
    void economySliceIncludesExactItemAndStoreProofsButNotCombatScenes() {
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-exact-consumption"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-object-boards"));
        assertTrue(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-equipment-issue"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-container-recovery"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-scene-explosion"));
        assertFalse(FrontierV3GameTestSlice.includes("economy", "pm-frontier-v3-resource-harvest"));
    }

    @Test
    void emptySliceCannotAccidentallyFilterTheFullGateAndUnknownSlicesFailClosed() {
        assertTrue(FrontierV3GameTestSlice.includes("", "core-integration"));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3GameTestSlice.includes("all", "pm-frontier-v3-scene-handoff"));
    }
}
