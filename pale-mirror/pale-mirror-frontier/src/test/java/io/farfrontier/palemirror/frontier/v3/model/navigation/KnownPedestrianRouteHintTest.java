package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class KnownPedestrianRouteHintTest {
    private static final List<SurfaceAnchor> HINT = IntStream.range(0, 100)
            .mapToObj(x -> new SurfaceAnchor(new BlockPosition(x, x / 20, 0))).toList();

    @Test void supportedNonflatSuffixAndAdjacentJoinDoNotRestartRegionalSearch() throws Exception {
        var queries = new AtomicInteger();
        try (var binding = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            queries.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "pending");
        })) {
            var suffix = KnownPedestrianNavigation.plannedRoute(geometry(-1), HINT.get(30), order(), HINT);
            assertEquals(HINT.subList(30, 100), suffix);
            var adjacent = new SurfaceAnchor(new BlockPosition(30, 1, 1));
            var joined = KnownPedestrianNavigation.plannedRoute(geometry(-1), adjacent, order(), HINT);
            assertEquals(adjacent, joined.getFirst());
            assertEquals(HINT.getLast(), joined.getLast());
            assertEquals(HINT.subList(30, 100), joined.subList(1, joined.size()));
            assertEquals(0, queries.get(), "accepted route continuity must not become repeated calculation delay");
        }
    }

    @Test void changedGeometryFallsBackToTheSharedPlannerWithoutAdmittingTheStaleHint() throws Exception {
        var queries = new AtomicInteger();
        try (var binding = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            queries.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "changed terrain pending");
        })) {
            var error = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                    () -> KnownPedestrianNavigation.plannedRoute(geometry(50), HINT.get(30), order(), HINT));
            assertEquals(PedestrianRouteResult.Status.PLANNING, error.status());
            assertEquals(1, queries.get());
        }
    }

    private static MovementOrder order() {
        return new MovementOrder(new SubjectId("job:hint"), new SubjectId("resident:hint"), 1, 1,
                List.of(HINT.getLast()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    private static PedestrianRouteGeometry geometry(int blockedX) {
        return new PedestrianRouteGeometry() {
            @Override public WorldBounds bounds() { return new WorldBounds(-10, -10, 210, 30); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return new SurfaceAnchor(new BlockPosition(x, x / 20, z)); }
            @Override public boolean blocked(SurfaceAnchor surface) { return surface.x() == blockedX; }
        };
    }
}
