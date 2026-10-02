package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.LinkedHashSet;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PresentationBatchTest {
    @Test void waitingNearestChunkCannotStarveReadyNeighboursOrConsumeAcknowledgements() {
        var pending = new LinkedHashSet<>(List.of(2, 3));
        var selected = FrontierV3PresentationBatch.select(List.of(1), () -> pending.stream().sorted(),
                chunk -> chunk != 1, pending::add, pending::remove);
        assertEquals(List.of(2), selected);
        assertEquals(new LinkedHashSet<>(List.of(3, 1)), pending);
        var allHeld = FrontierV3PresentationBatch.select(List.of(3), () -> pending.stream().sorted(),
                chunk -> false, pending::add, pending::remove);
        assertTrue(allHeld.isEmpty(), "an empty batch creates no sent-count or acknowledgement debt");
        assertTrue(pending.contains(1) && pending.contains(3), "deferred chunks remain pending, never lost");
    }
}
