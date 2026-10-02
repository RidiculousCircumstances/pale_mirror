package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentDescriptorInventoryTest {
    @Test void freshSchemaRequiresExactDescriptorInventoryBeforeHydration() {
        var codec = new FrontierWorldStateCodec();
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:current-descriptors"), 125L));
        byte[] current = codec.encode(state);
        assertEquals(state, codec.decode(current));
        FrontierWorldSnapshotHeader.read(current);
        byte[] unknown = current.clone(); unknown[7] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldSnapshotHeader.read(unknown));
    }
}
