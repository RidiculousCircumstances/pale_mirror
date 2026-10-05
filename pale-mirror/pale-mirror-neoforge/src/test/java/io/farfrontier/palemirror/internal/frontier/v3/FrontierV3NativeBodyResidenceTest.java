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
}
