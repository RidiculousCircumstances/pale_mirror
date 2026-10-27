package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianPlanningDemandTest {
    @Test void calculationFactBudgetRejectsOverflowInsteadOfSilentlyDroppingAcknowledgedData() {
        var surfaces = java.util.stream.IntStream.range(0, 65_535).mapToObj(i ->
                SurfaceAnchor.at(i / 2, 63, (i + 1) / 2)).toList();
        var geometry = ground(PedestrianGeometryRevision.initial());
        var request = PedestrianRouteRequest.of(geometry, surfaces.getFirst(), surfaces.getLast());
        var result = new PedestrianRouteResult(PedestrianRouteResult.Status.FOUND, surfaces, 0, 0, "PACKED_ZIGZAG");
        var read = PedestrianCalculationMemory.Read.conservative(request, result);
        assertThrows(IllegalStateException.class, () -> PedestrianCalculationMemory.empty().including(read),
                "resource exhaustion is explicit, rather than an invisible infinite re-preparation loop");
    }
    @Test void deliveredNegativeEvidenceIsRetainedButReopensWhenItsTerrainChanges() {
        var revision = PedestrianGeometryRevision.initial();
        java.util.function.Function<PedestrianGeometryRevision, PedestrianRouteGeometry> geometry = current -> new PedestrianRouteGeometry() {
            public Object version() { return current; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(current); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 16, 4); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return surface.x() == 2; }
        };
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(3, 63, 1);
        var original = geometry.apply(revision);
        PedestrianCalculationMemory memory;
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(original, start, goal);
            for (int n = 0; n < 100 && planner.pendingCount() > 0; n++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(0, planner.pendingCount());
            var request = PedestrianRouteRequest.of(original, start, goal);
            var delivered = planner.readFact(request).orElseThrow();
            assertEquals(PedestrianRouteResult.Status.NO_PATH, delivered.result().status());
            memory = PedestrianCalculationMemory.empty().including(delivered);
        }
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        PedestrianRoutePlanner unavailable = (g, from, to) -> {
            calls.incrementAndGet(); return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "NOT_COMPUTED");
        };
        var remote = geometry.apply(revision.changed(Set.of(new PedestrianGeometryRevision.Region(7, 0))));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, PedestrianRoutePlanning.prepare(unavailable, memory, () ->
                PedestrianRoutePlanning.query(remote, start, goal)).observation().result().status());
        assertEquals(0, calls.get());
        var changed = geometry.apply(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))));
        assertEquals(PedestrianRouteResult.Status.PLANNING, PedestrianRoutePlanning.prepare(unavailable, memory, () ->
                PedestrianRoutePlanning.query(changed, start, goal)).observation().result().status());
        assertEquals(1, calls.get(), "negative data cannot hide a later relevant terrain change");
    }
    @Test void privateSuccessfulReadsValidateRegionalChangesAndCannotSurviveAForeignBaseline() throws Exception {
        var revision = PedestrianGeometryRevision.initial(); var geometry = ground(revision);
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(5, 63, 1);
        PedestrianCalculationMemory memory;
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            planner.query(geometry, start, goal);
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            memory = PedestrianRoutePlanning.prepare(planner.readView(), () ->
                    PedestrianRoutePlanning.query(geometry, start, goal)).reads();
        }
        var freshReads = new java.util.concurrent.atomic.AtomicInteger();
        PedestrianRoutePlanner emptyView = (g, from, to) -> {
            freshReads.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "EMPTY_READ_VIEW");
        };
        var distant = ground(revision.changed(Set.of(new PedestrianGeometryRevision.Region(7, 0))));
        assertEquals(PedestrianRouteResult.Status.FOUND, PedestrianRoutePlanning.prepare(emptyView, memory, () ->
                PedestrianRoutePlanning.query(distant, start, goal)).observation().result().status());
        assertEquals(0, freshReads.get(), "a remote edit does not discard this caller's consumed positive proof");
        for (var changed : List.of(ground(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)))),
                ground(PedestrianGeometryRevision.initial()), ground(PedestrianGeometryRevision.initial(revision.scope())))) {
            assertEquals(PedestrianRouteResult.Status.PLANNING, PedestrianRoutePlanning.prepare(emptyView, memory, () ->
                    PedestrianRoutePlanning.query(changed, start, goal)).observation().result().status(),
                    "local facts, foreign scope and a new baseline all require fresh calculation");
        }
        assertEquals(3, freshReads.get());
    }
    @Test void retainedUnknownGeometryDoesNotRepeatedlyCompleteAnAcceptedAdviceWait() throws Exception {
        var geometry = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 128, 4); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public boolean clearanceKnown(SurfaceAnchor surface) { return false; }
        };
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(3, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            planner.query(geometry, start, goal);
            for (int n = 0; n < 100 && planner.pendingCount() > 0; n++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            var result = planner.peek(geometry, start, goal).orElseThrow();
            assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, result.status());
            assertFalse(result.missingGeometry().isEmpty()); planner.drainChanges();
            var prepared = PedestrianRoutePlanning.prepare(PedestrianRoutePlanning.readView(), () -> {
                var current = PedestrianRoutePlanning.query(geometry, start, goal);
                PedestrianRoutePlanning.await(PedestrianRouteRequest.of(geometry, start, goal)); return current;
            });
            for (int n = 0; n < 20; n++) assertTrue(prepared.acquire().isEmpty(), "stable missing facts are not a new calculation result");
            assertTrue(planner.drainChanges().isEmpty());
            planner.geometryObserved(result.missingGeometry());
            assertTrue(planner.drainChanges().stream().anyMatch(change -> change.kind() == PedestrianPlanningChange.Kind.INVALIDATED),
                    "actual physical knowledge ingress still reopens the exact calculation");
        }
    }
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
    @Test void detachedAdviceNeverEnqueuesAndAcceptedAdviceObservesAlreadyCompletedRoute() throws Exception {
        var geometry = ground(PedestrianGeometryRevision.initial());
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(5, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            var snapshot = PedestrianRoutePlanning.readView();
            var advice = PedestrianRoutePlanning.prepare(snapshot, () -> {
                var result = PedestrianRoutePlanning.query(geometry, start, goal);
                PedestrianRoutePlanning.await(PedestrianRouteRequest.of(geometry, start, goal));
                return result;
            });
            assertEquals(0, planner.pendingCount(), "abandoning this advice must leave no live work");
            planner.query(geometry, start, goal);
            for (int n = 0; n < 100 && planner.pendingCount() > 0; n++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(geometry, start, goal).orElseThrow().status());
            assertEquals(PedestrianRouteResult.Status.PLANNING, snapshot.query(geometry, start, goal).status(), "view is frozen");
            assertEquals(List.of(new PedestrianPlanningChange(PedestrianRouteRequest.of(geometry, start, goal),
                    PedestrianPlanningChange.Kind.RESULT_AVAILABLE)), advice.acquire(), "completion before publication must not lose wake");
            var current = PedestrianRoutePlanning.readView();
            assertEquals(PedestrianRouteResult.Status.FOUND, current.query(geometry, start, goal).status());
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
