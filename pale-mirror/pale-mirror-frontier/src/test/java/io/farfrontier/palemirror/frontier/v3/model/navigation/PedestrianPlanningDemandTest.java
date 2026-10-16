package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianPlanningDemandTest {
    @Test void retiringLastConsumerCancelsItsRebasedWorkerWithoutPublishingAnArrival() {
        var tasks = new ArrayList<Runnable>(); var revision = PedestrianGeometryRevision.initial();
        var old = ground(revision); var next = ground(revision.changed(Set.of(new PedestrianGeometryRevision.Region(7, 0))));
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(90, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(tasks::add)) {
            planner.query(old, start, goal); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK);
            planner.query(next, start, goal);
            planner.discardUnobserved(PedestrianRouteRequest.of(old, start, goal));
            tasks.forEach(Runnable::run); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK);
            assertEquals(0, planner.pendingCount()); assertEquals(0, planner.inFlightCount());
            assertEquals(0, planner.completedCount()); assertTrue(planner.peek(next, start, goal).isEmpty());
        }
    }
    private static PedestrianRouteGeometry ground(PedestrianGeometryRevision revision) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 128, 4); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
    }
    @Test void oneDestinationWithSeveralStationsStartsOneSearchAndRetainsItsExactWake() throws Exception {
        var geometry = ground(PedestrianGeometryRevision.initial()); var start = SurfaceAnchor.at(1, 63, 1);
        var order = new MovementOrder(new SubjectId("owner:a"), new SubjectId("actor:a"), 1, 1,
                List.of(SurfaceAnchor.at(90, 63, 1), SurfaceAnchor.at(91, 63, 1), SurfaceAnchor.at(92, 63, 1)),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            var observation = PedestrianRoutePlanning.observe(() -> assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                    () -> KnownPedestrianNavigation.plannedRoute(geometry, start, order)));
            assertEquals(PedestrianRouteResult.Status.PLANNING, observation.result().status());
            assertEquals(1, planner.pendingCount()); assertEquals(1, observation.requests().size());
            var access = new PedestrianAccessSelection(geometry, start);
            for (var target : order.legalStations()) assertFalse(access.reachable(target));
            assertEquals(1, planner.pendingCount(), "access selection must share the same bounded alternatives policy");
            for (int i = 0; i < 1000 && planner.pendingCount() > 0; i++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(geometry, start, order.legalStations().getFirst()).orElseThrow().status());
            assertTrue(planner.drainChanges().stream().anyMatch(change -> observation.requests().contains(change.request())
                    && change.kind() == PedestrianPlanningChange.Kind.RESULT_AVAILABLE));
            assertEquals(order.legalStations().getFirst(), KnownPedestrianNavigation.plannedRoute(geometry, start, order).getLast());
        }
    }

    @Test void abandoningAnInspectionCannotCancelAnExistingSharedInFlightRequestAfterRebase() throws Exception {
        var tasks = new ArrayList<Runnable>(); var revision = PedestrianGeometryRevision.initial();
        var old = ground(revision); var current = ground(revision.changed(Set.of(new PedestrianGeometryRevision.Region(7, 0))));
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(3, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(tasks::add); var binding = PedestrianRoutePlanning.bind(planner)) {
            planner.query(old, start, goal); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            PedestrianRoutePlanning.observe(() -> PedestrianRoutePlanning.query(current, start, goal));
            assertEquals(1, planner.pendingCount(), "another consumer's acquired search survives unused selection");
            tasks.removeFirst().run(); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(current, start, goal).orElseThrow().status());
        }
    }

    @Test void serviceScopePromotesASharedCalculationWithoutDuplicatingItAndRestoresOnFailure() throws Exception {
        var geometry = ground(PedestrianGeometryRevision.initial()); var start = SurfaceAnchor.at(1, 63, 1);
        var goal = SurfaceAnchor.at(3, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            PedestrianRoutePlanning.query(geometry, start, goal);
            assertThrows(IllegalArgumentException.class, () -> PedestrianRoutePlanning.withService(PedestrianPlanningService.PASSAGE_CLEARANCE, () -> {
                PedestrianRoutePlanning.query(geometry, start, goal); throw new IllegalArgumentException("selection aborted");
            }));
            PedestrianRoutePlanning.query(geometry, start, SurfaceAnchor.at(4, 63, 1));
            assertEquals(2, planner.pendingCount());
            assertEquals(1, planner.serviceDiagnostics().stream().filter(value -> value.service().equals("CLEARANCE")).findFirst().orElseThrow().active());
            assertEquals(1, planner.serviceDiagnostics().stream().filter(value -> value.service().equals("LOCAL")).findFirst().orElseThrow().active());
        }
    }
}
