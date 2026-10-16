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
    @Test void wakeRetainsUnfinishedSiblingUntilOwnerReplacesOrCancelsItsDemand() {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("mixed", 6000);
        var fast = request(3); var slow = request(20);
        var released = new java.util.ArrayList<PedestrianRouteRequest>();
        index.replace(owner, List.of(fast, slow));
        index.changed(List.of(new PedestrianPlanningChange(fast, PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        var continuation = new PedestrianPlanningReady(owner).continuationAt(new SimInstant(20));
        index.resumed(owner, continuation);
        index.releaseUnused(released::add);
        assertTrue(released.isEmpty(), "notification admission must not cancel unfinished sibling searches");
        assertFalse(index.waiting(continuation));
        assertTrue(index.ready(view(21, continuation)).isEmpty());
        index.changed(List.of(new PedestrianPlanningChange(slow, PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertTrue(index.ready(view(21, continuation)).isEmpty(), "already runnable owner needs no second wake");
        index.replace(action("mixed", 8000), List.of(slow));
        index.releaseUnused(released::add);
        assertEquals(List.of(fast), released);
        index.ready(view(22)); index.releaseUnused(released::add);
        assertEquals(List.of(fast, slow), released, "cancelled successor must release remaining work");
        assertThrows(IllegalArgumentException.class, () -> index.resumed(owner, continuation));
    }
    @Test void naturallyDueOwnerKeepsItsDemandUntilItsReviewRuns() {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("due", 20);
        index.replace(owner, List.of(request(3), request(20)));
        index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertTrue(index.ready(view(20, owner)).isEmpty());
        assertFalse(index.waiting(owner));
        index.releaseUnused(request -> fail("runnable is not retired: " + request));
    }
    @Test void onlyLastRetiredConsumerReleasesCalculationAndReplacementDoesNotCancelIt() {
        var index = new PedestrianPlanningWakeIndex();
        var a = action("owner-a", 20); var b = action("owner-b", 20); var query = request(3);
        var released = new java.util.ArrayList<PedestrianRouteRequest>();
        index.replace(a, List.of(query)); index.replace(b, List.of(query));
        index.replace(a, List.of(query)); index.releaseUnused(released::add);
        assertTrue(released.isEmpty());
        index.remove(a.id()); index.releaseUnused(released::add); assertTrue(released.isEmpty());
        index.remove(b.id()); index.releaseUnused(released::add); assertEquals(List.of(query), released);
        index.releaseUnused(released::add); assertEquals(1, released.size());
    }
    @Test void regionalRebaseWakesOnlyTheExactOldAddressThenTheSelectedSuccessorWithoutLosingWork() {
        var revision = PedestrianGeometryRevision.initial(); var next = revision.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(12, 0)));
        java.util.function.Function<PedestrianGeometryRevision, PedestrianRouteGeometry> geometry = current -> new PedestrianRouteGeometry() {
            public Object version() { return current; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(current); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 512, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return current == next && surface.x() == 192; }
        };
        var old = geometry.apply(revision); var current = geometry.apply(next);
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(480, 63, 1);
        var index = new PedestrianPlanningWakeIndex(); var owner = action("regional", 6_000); var other = action("unrelated", 6_000);
        var oldAddress = PedestrianRouteRequest.of(old, start, target); var newAddress = PedestrianRouteRequest.of(current, start, target);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(old, start, target); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.replace(owner, List.of(oldAddress)); index.replace(other, List.of(request(3)));
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(current, start, target).status());
            index.changed(planner.drainChanges()); assertEquals(List.of(owner), index.ready(view(20, owner, other)));
            index.replace(owner, List.of(newAddress)); assertTrue(index.ready(view(20, owner, other)).isEmpty());
            for (int turn = 0; turn < 1_024 && planner.pendingCount() > 0; turn++)
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(0, planner.pendingCount()); assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.query(current, start, target).status());
            index.changed(planner.drainChanges()); assertEquals(List.of(owner), index.ready(view(20, owner, other)));
        }
    }
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
    @Test void cancelledChangedAndAlreadyRunnableDueGenerationsDoNotProduceCommands() {
        for (var retained : List.of(List.<ScheduledAction>of(), List.of(action("a", 7000)), List.of(action("a", 6000)))) {
            var index = new PedestrianPlanningWakeIndex(); var owner = action("a", 6000);
            index.replace(owner, List.of(request(3)));
            index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertTrue(index.ready(view(retained.equals(List.of(owner)) ? 6000 : 20,
                    retained.toArray(ScheduledAction[]::new))).isEmpty());
        }
    }
    @Test void dueCalculationWaitParksWithoutPollingAndNeedsAnAddressedWake() {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("parked", 20);
        index.replace(owner, List.of(request(3)));
        var queue = new ScheduledActionQueue(); queue.schedule(owner);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        for (long tick = 20; tick < 200; tick++) assertTrue(queue.selectDue(new SimInstant(tick), new WorkBudget(1, 1),
                action -> { checks.incrementAndGet(); return !index.waiting(action); },
                action -> { index.markParked(action); return java.util.Set.of(action.subject()); }).admitted().isEmpty());
        assertEquals(1, checks.get()); assertEquals(List.of(owner), queue.snapshot());
        index.changed(List.of(new PedestrianPlanningChange(request(3), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertFalse(index.waiting(owner));
        assertEquals(List.of(owner), index.ready(view(200, owner)), "due time must not discard a parked owner's wake");
        assertEquals(List.of(owner), index.ready(view(201, owner)), "notification capacity cannot lose the wake");
        queue.wake(java.util.Set.of(owner.subject()));
        assertEquals(List.of(owner), queue.selectDue(new SimInstant(201), new WorkBudget(1, 1),
                action -> !index.waiting(action)).admitted());
        index.remove(owner.id()); assertFalse(index.waiting(owner));
    }
    @Test void boundNotificationCanWakeADueParkedOwnerButCannotForgeItsGeneration() {
        var state = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState.initial(
                io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(new WorldId("frontier:due-planning-wake"), 421L));
        var owner = action("parked", 20); var module = new PedestrianPlanningProcessModule();
        var commandId = new CommandId("test:due-planning-wake");
        var bound = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                Revision.ZERO, new SimInstant(200), owner.subject(), CauseChain.root(commandId), new PedestrianPlanningReady(owner),
                java.util.Optional.of(new EngineScheduleBinding(Revision.ZERO, owner)));
        var accepted = assertInstanceOf(CommandPlan.Accepted.class, module.planCommand(state, bound));
        var replacement = assertInstanceOf(ScheduleEffect.Rescheduled.class, accepted.events().getFirst().payload()).replacement();
        assertEquals(owner.id(), replacement.id()); assertEquals(new SimInstant(201), replacement.dueAt());
        var foreign = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                Revision.ZERO, new SimInstant(200), owner.subject(), CauseChain.root(commandId), new PedestrianPlanningReady(owner),
                java.util.Optional.of(new EngineScheduleBinding(Revision.ZERO, action("parked", 21))));
        assertInstanceOf(CommandPlan.Rejected.class, module.planCommand(state, foreign));
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
            assertEquals(0, planner.pendingCount(), "discarded newly acquired alternatives must not consume calculation capacity");
            assertTrue(observation.requests().isEmpty(), "a usable route must not create a wake subscription for discarded alternatives");
        }
    }
}
