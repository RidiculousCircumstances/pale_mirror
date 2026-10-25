package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockInteractionReachTest {
    @Test void observedOffCenterArrivalMustNotTurnAnAdmittedStationIntoAnUnreachableInteraction() {
        var target = new BlockPosition(385, 64, -35);
        // Actual native stall: support388/63/-32, feet388.5976307/64/-31.4279835.
        // Its center is in reach, but the real eye is about4.504blocks from the log.
        double dx = 388.5976307172259 - 385.5, dz = -31.427983472370435 + 34.5;
        assertTrue(dx * dx + dz * dz + 1.12 * 1.12 > 4.5 * 4.5);
        var reach = BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN;
        assertFalse(reach.contains(SurfaceAnchor.at(388, 63, -32), target));
        assertTrue(reach.contains(SurfaceAnchor.at(387, 63, -33), target));
        assertEquals(4500, reach.radiusMilli(), "planning must not extend native interaction reach");
    }
}
