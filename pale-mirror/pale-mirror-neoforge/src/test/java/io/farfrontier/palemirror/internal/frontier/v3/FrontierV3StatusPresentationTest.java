package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3StatusPresentationTest {
    @Test
    void activeStatusNamesV3AndReportsTheBoundedCanonicalAuditSurface() {
        FrontierWorldProjection projection = new FrontierWorldProjection(
                new WorldId("frontier:graybox"), new Revision(42L), new SimInstant(9_001L), "bootstrap",
                12, 274, 48, 17, 63, 2, 1, 3, 4, 5, 6, 7, 8, 9);

        assertEquals("Frontier v3 ACTIVE | world=frontier:graybox rev=42 tick=9001"
                        + " | settlements=12 residents=274 bioforms=48 infectedCells=17"
                        + " | exactStacks=63 production=2 routes=1 | effects prepared=3 unknown=4"
                        + " | HOT scenes=5/unknown=6 ambient=7/unknown=8 | inventoryConflicts=9",
                FrontierV3ServerLifecycle.formatStatus(projection));
    }
}
