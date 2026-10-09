package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FrontierSnapshotDescriptorInventoryTest {
    @Test void currentCompositionPreservesHeaderAndFullState() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-recovery-upgrade"), 91L));
        var codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(state);
        assertTrue(FrontierSnapshotDescriptorInventory.accepts(FrontierDurationProcessDriverRegistry.inventoryFingerprint()));
        assertEquals(state.bootstrap().worldId(), FrontierWorldSnapshotHeader.read(encoded).worldId());
        assertEquals(state, codec.decode(encoded));
        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
    }

    @Test void previousInventoryCannotAuthorizeAnUnrelatedFutureComposition() {
        assertFalse(FrontierSnapshotDescriptorInventory.accepts("5f2691f35f4efbb5b339eaa50745e945a54149126d8fd0e0b18962f33d595003"));
        assertFalse(FrontierSnapshotDescriptorInventory.accepts("0".repeat(64)));
    }

    @Test void freshWorldDoesNotRelaxThePhysicalLifecycleOrUnknownInventoryFence() {
        var codec = new FrontierWorldStateCodec();
        var encoded = codec.encode(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-recovery-negative"), 91L)));
        var unknown = replace(encoded, FrontierDurationProcessDriverRegistry.inventoryFingerprint(), "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldSnapshotHeader.read(unknown));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown));
        var changedPhysical = replace(encoded, FrontierWorldProcessCatalog.physicalLifecycleFingerprint(), "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldSnapshotHeader.read(changedPhysical));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(changedPhysical));
    }

    private static byte[] replace(byte[] original, String expected, String replacement) {
        assertEquals(expected.length(), replacement.length());
        byte[] result = original.clone();
        byte[] needle = expected.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= result.length - needle.length; offset++) {
            if (java.util.Arrays.equals(result, offset, offset + needle.length, needle, 0, needle.length)) {
                System.arraycopy(replacement.getBytes(StandardCharsets.UTF_8), 0, result, offset, needle.length);
                return result;
            }
        }
        throw new AssertionError("snapshot lacks the exact descriptor fingerprint");
    }
}
