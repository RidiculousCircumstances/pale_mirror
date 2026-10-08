package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianPlanningWakeIndexTest {
    @Test void emptyWaitsDoNotReadTheQueueAndIndexedLookupFencesTheExactGeneration() {
        var index = new PedestrianPlanningWakeIndex();
        var owner = action("indexed", 6000);
        var view = new FrontierScheduleView() {
            public WorldId worldId() { return new WorldId("frontier:empty-waits"); }
            public Revision revision() { return Revision.ZERO; }
            public SimInstant instant() { return new SimInstant(20); }
            public List<ScheduledAction> schedules() { fail("empty waits must not enumerate future work"); return List.of(); }
        };
        assertTrue(index.ready(view).isEmpty());
        index.replace(owner, List.of(request(3)));
        index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(List.of(owner), index.ready(new SimInstant(20), action -> {
            calls.incrementAndGet(); return action.equals(owner);
        }));
        assertEquals(1, calls.get());
        assertTrue(index.ready(new SimInstant(20), action -> action.equals(action("indexed", 7000))).isEmpty());
        assertEquals(0, index.waitingCount());
    }
    private static final PedestrianRouteGeometry GROUND = new PedestrianRouteGeometry() {
        public WorldBounds bounds() { return new WorldBounds(0, 0, 32, 32); }
        public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
        public boolean blocked(SurfaceAnchor surface) { return false; }
    };
    private static ScheduledAction action(String id, long due) {
        return new ScheduledAction(new ScheduleId("schedule:" + id), new SimInstant(due), 1,
                new SubjectId("owner:" + id), "frontier.transport_mission.progress", 1);
    }
    private static FrontierExecutionView view(long tick, ScheduledAction... actions) {
        return new FrontierExecutionView(new WorldId("frontier:planning-waits"), Revision.ZERO, new SimInstant(tick), List.of(actions));
    }
    private static PedestrianRouteRequest request(int target) {
        return PedestrianRouteRequest.of(GROUND, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(target, 63, 1));
    }
    @Test void unrelatedCompletionDoesNotWakeAndRepeatedDependenciesCoalesceToOneContinuation() {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("a", 6000); var other = action("b", 6000);
        index.replace(owner, List.of(request(3), request(4))); index.replace(other, List.of(request(5)));
        index.changed(List.of(new PedestrianPlanningChange(request(6), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertTrue(index.ready(view(20, owner, other)).isEmpty());
        for (int i = 0; i < 5000; i++) index.changed(List.of(
                new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE),
                new PedestrianPlanningChange(request(4), PedestrianPlanningChange.Kind.INVALIDATED)));
        assertEquals(List.of(owner), index.ready(view(20, owner, other)));
        index.remove(owner.id()); assertTrue(index.ready(view(20, owner, other)).isEmpty());
    }
    @Test void cancelledChangedAndAlreadyDueGenerationsDoNotProduceCommands() {
        for (var retained : List.of(List.<ScheduledAction>of(), List.of(action("a", 7000)), List.of(action("a", 6000)))) {
            var index = new PedestrianPlanningWakeIndex(); var owner = action("a", 6000);
            index.replace(owner, List.of(request(3)));
            index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertTrue(index.ready(view(retained.equals(List.of(owner)) ? 6000 : 20,
                    retained.toArray(ScheduledAction[]::new))).isEmpty());
        }
    }
    @Test void deferredReadyWorkRemainsFairUntilAcknowledged() {
        var index = new PedestrianPlanningWakeIndex(); var a = action("a", 6000); var b = action("b", 6000);
        index.replace(a, List.of(request(3))); index.replace(b, List.of(request(4)));
        index.changed(List.of(new PedestrianPlanningChange(request(4), PedestrianPlanningChange.Kind.RESULT_AVAILABLE),
                new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertEquals(List.of(b, a), index.ready(view(20, a, b)));
        assertEquals(List.of(b, a), index.ready(view(21, a, b)), "a budget pause cannot discard unacknowledged work");
        index.remove(b.id()); index.replace(b, List.of(request(4)));
        index.changed(List.of(new PedestrianPlanningChange(request(4), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertEquals(List.of(a, b), index.ready(view(22, a, b)), "a rearmed owner joins the tail");
    }
    @Test void actualPendingQueryRegistersItsProducedSuccessorButDiagnosticReadDoesNot() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("a", 20); var successor = action("a", 6000);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            PedestrianPlanningContinuations.plan(owner, () -> {
                PedestrianRoutePlanning.query(GROUND, request(3).start(), request(3).target());
                PedestrianRoutePlanning.await(request(3));
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), successor)));
            });
            var read = PedestrianRoutePlanning.observe(() -> PedestrianRoutePlanning.peek(GROUND, request(3).start(), request(3).target()));
            assertTrue(read.requests().isEmpty());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges());
            assertEquals(List.of(successor), index.ready(view(20, successor)));
            // A later successful owner review retires the old wait instead of repeatedly consuming receipts.
            PedestrianPlanningContinuations.plan(successor, () -> {
                assertEquals(PedestrianRouteResult.Status.FOUND,
                        PedestrianRoutePlanning.query(GROUND, request(3).start(), request(3).target()).status());
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), action("a", 8000))));
            });
            index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertTrue(index.ready(view(6000, action("a", 8000))).isEmpty());
        }
    }
    @Test void usableAlternativeDoesNotSubscribeToAnUnneededPendingRoute() throws Exception {
        var near = request(3); var pending = request(20);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            planner.query(GROUND, near.start(), near.target());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            var order = new MovementOrder(new SubjectId("owner:a"), new SubjectId("actor:a"), 1, 1,
                    List.of(pending.target(), near.target()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
            var observation = PedestrianRoutePlanning.observe(() -> KnownPedestrianNavigation.plannedRoute(GROUND, near.start(), order));
            assertEquals(near.target(), observation.result().getLast());
            assertEquals(1, planner.pendingCount());
            assertTrue(observation.requests().isEmpty(), "a usable route must not create a wake subscription for discarded alternatives");
        }
    }
}
