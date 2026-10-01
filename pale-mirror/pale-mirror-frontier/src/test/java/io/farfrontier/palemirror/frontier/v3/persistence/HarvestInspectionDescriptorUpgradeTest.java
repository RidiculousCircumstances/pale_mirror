package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HarvestInspectionDescriptorUpgradeTest {
    @Test void onlyExactAdditiveRegistrationIsAcceptedAndReencodedWithCurrentInventory() {
        var codec = new FrontierWorldStateCodec();
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:descriptor-upgrade"), 125L));
        byte[] current = codec.encode(state);
        assertEquals(HarvestInspectionDescriptorUpgrade.AFTER, FrontierDurationProcessDriverRegistry.inventoryFingerprint());
        byte[] before = current.clone();
        replaceFingerprint(before, HarvestInspectionDescriptorUpgrade.BEFORE);
        assertEquals(FrontierWorldSnapshotHeader.read(current), FrontierWorldSnapshotHeader.read(before));
        var upgraded = codec.decode(before);
        assertEquals(state, upgraded);
        assertArrayEquals(current, codec.encode(upgraded));
        byte[] unknown = current.clone(); replaceFingerprint(unknown, "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldSnapshotHeader.read(unknown));
        byte[] incompatibleLifecycle = before.clone(); incompatibleLifecycle[73] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(incompatibleLifecycle));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldSnapshotHeader.read(incompatibleLifecycle));
        assertFalse(HarvestInspectionDescriptorUpgrade.accepts("future-registry", HarvestInspectionDescriptorUpgrade.BEFORE));
        assertFalse(HarvestInspectionDescriptorUpgrade.accepts(HarvestInspectionDescriptorUpgrade.BEFORE, HarvestInspectionDescriptorUpgrade.AFTER));
    }
    private static void replaceFingerprint(byte[] bytes, String fingerprint) {
        // Header: int magic, byte schema, unsigned-short UTF length, 64 ASCII hash characters.
        assertEquals(64, (bytes[5] & 255) * 256 + (bytes[6] & 255));
        System.arraycopy(fingerprint.getBytes(StandardCharsets.US_ASCII), 0, bytes, 7, 64);
    }
}
