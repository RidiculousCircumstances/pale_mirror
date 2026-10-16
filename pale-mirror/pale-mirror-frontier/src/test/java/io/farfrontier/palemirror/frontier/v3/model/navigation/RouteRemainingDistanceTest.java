package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteRemainingDistanceTest {
    @Test void followsActualThreeDimensionalRouteWithoutExpandingLongRunsOrInventingShortcuts() {
        var path = new PedestrianPathProof(SurfaceAnchor.at(0, 60, 0), List.of(
                new PedestrianPathProof.Run(1, 0, 0, 10000), new PedestrianPathProof.Run(0, 1, 1, 10)));
        assertEquals(5010, RouteRemainingDistance.edges(path, SurfaceAnchor.at(5000, 60, 0)).orElseThrow());
        assertEquals(5, RouteRemainingDistance.edges(path, SurfaceAnchor.at(10000, 65, 5)).orElseThrow());
        assertTrue(RouteRemainingDistance.edges(path, SurfaceAnchor.at(5000, 59, 0)).isEmpty());
        assertEquals(0, RouteRemainingDistance.edges(path, path.destination()).orElseThrow());
    }
}
