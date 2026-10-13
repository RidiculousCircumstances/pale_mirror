package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownRouteBarrierIndexTest {
    @Test void deltaReplacementInvalidatesPositiveAndNegativeResultsWithoutChangingBarrierRules() {
        var index = new KnownRouteBarrierIndex();
        var route = List.of(SurfaceAnchor.at(0, 63, 0), SurfaceAnchor.at(1, 63, 0), SurfaceAnchor.at(2, 63, 0));
        var unrelated = Map.of(new BlockPosition(10, 63, 10), delta(new BlockPosition(10, 63, 10)));
        assertEquals(-1, index.first(unrelated, route));
        assertEquals(-1, index.first(unrelated, route));
        assertEquals(1, index.retainedRoutes());
        for (int y : List.of(63, 64, 65)) {
            var obstacle = new BlockPosition(1, y, 0);
            var changed = Map.of(obstacle, delta(obstacle));
            assertEquals(1, index.first(changed, route));
            assertEquals(1, index.first(changed, route));
        }
        assertEquals(-1, index.first(unrelated, route), "removed obstacle must not survive in cache");
        assertEquals(-1, index.first(Map.of(route.getFirst().support(), delta(route.getFirst().support())), route),
                "the retained departure is not a future barrier");
        assertEquals(-1, index.first(Map.of(), route));
        assertEquals(0, index.retainedRoutes(), "empty geometry releases the prior input cache");
    }

    @Test void retainedRouteAndSurfaceBudgetsRemainBoundedAndEvictionCannotChangeResults() {
        var index = new KnownRouteBarrierIndex();
        var position = new BlockPosition(1, 63, 0);
        var deltas = Map.of(position, delta(position));
        var first = List.of(SurfaceAnchor.at(0, 63, 0), SurfaceAnchor.at(1, 63, 0));
        assertEquals(1, index.first(deltas, first));
        for (int n = 0; n < KnownRouteBarrierIndex.MAX_ROUTES + 1; n++) {
            var route = java.util.stream.IntStream.range(0, 1024)
                    .mapToObj(x -> SurfaceAnchor.at(x, 63, 2)).toList();
            assertEquals(-1, index.first(deltas, route));
            assertTrue(index.retainedRoutes() <= KnownRouteBarrierIndex.MAX_ROUTES);
            assertTrue(index.retainedSurfaces() <= KnownRouteBarrierIndex.MAX_RETAINED_SURFACES);
        }
        assertEquals(1, index.first(deltas, first), "a discarded route is recalculated, not treated as clear");
    }

    private static PhysicalDelta delta(BlockPosition position) {
        return new PhysicalDelta(position, PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:route-barrier");
    }
}
