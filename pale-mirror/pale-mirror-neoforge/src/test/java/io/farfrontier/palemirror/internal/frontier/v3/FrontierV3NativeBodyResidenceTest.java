package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.FullChunkStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NativeBodyResidenceTest {
    @Test void inaccessibleTerrainAllowsOnlyNativeUnloadRequest() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.INACCESSIBLE));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.INACCESSIBLE));
    }

    @Test void pendingOrLiveNativeHolderCannotBeReclassifiedFromObserverAbsence() {
        for (var status : FullChunkStatus.values()) if (status != FullChunkStatus.INACCESSIBLE)
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(false, status));
    }

    @Test void loadedNonTickingHaloCanUseTheSameNativeSaveUnloadProtocol() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.FULL, false, false));
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.BLOCK_TICKING, false, false));
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.ENTITY_TICKING, false, false),
                "view-distance tickets do not override vanilla's independent simulation-distance range");
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.INACCESSIBLE, false, false));
    }

    @Test void anObserverOrNativeTickingPreventsColumnEviction() {
        for (var status : FullChunkStatus.values()) {
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, status, false, true));
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, status, true, false));
        }
        assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.FULL, false, false));
    }

    @Test void returnUsesRealTickingRangeWithoutWaitingForANominalHolderTransition() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.ENTITY_TICKING, true));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.ENTITY_TICKING, false));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(false, FullChunkStatus.ENTITY_TICKING, true));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.BLOCK_TICKING, true));
    }
}
