package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class RailCorridorPathfinderTest {
    private static final RoutePlacementRequirement POLICY = new RoutePlacementRequirement(
            "settlement", "mine", true, 4,
            32, 4_000, 24, 2, 250);

    @Test void dryDetourWinsOverShorterWaterCrossing() {
        List<VisualPoint> path = RailCorridorPathfinder.find(new VisualPoint(0, 70, 0),
                new VisualPoint(40, 70, 0), POLICY,
                (x, z) -> z == 0 && x >= 12 && x <= 28);

        assertTrue(path.stream().anyMatch(point -> point.z() != 0));
        assertTrue(path.stream().noneMatch(point -> point.z() == 0 && point.x() >= 12 && point.x() <= 28));
    }

    @Test void shortUnavoidableRiverIsAccepted() {
        List<VisualPoint> path = RailCorridorPathfinder.find(new VisualPoint(0, 70, 0),
                new VisualPoint(40, 70, 0), POLICY,
                (x, z) -> x >= 16 && x <= 24);

        assertTrue(path.stream().anyMatch(point -> point.x() >= 16 && point.x() <= 24));
    }

    @Test void waterBarrierWiderThanBridgeBudgetFailsClosed() {
        assertThrows(DryMineSiteUnavailableException.class, () -> RailCorridorPathfinder.find(
                new VisualPoint(0, 70, 0), new VisualPoint(48, 70, 0), POLICY,
                (x, z) -> x >= 8 && x <= 40));
    }
}
