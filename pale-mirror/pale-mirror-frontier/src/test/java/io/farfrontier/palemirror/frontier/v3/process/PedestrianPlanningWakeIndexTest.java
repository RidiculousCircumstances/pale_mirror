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
    @Test void advancedAdviceCursorRetiresItsPreviousAlternativeWhileKeepingCurrentDelivery() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("cursor-checkpoint", 20);
        var first = request(3); var next = request(20); var cursor = new java.util.concurrent.atomic.AtomicInteger();
        ScheduledAdvisory<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState> advice = () -> {
            if (cursor.get() == 0) {
                assertEquals(PedestrianRouteResult.Status.FOUND, PedestrianRoutePlanning.query(GROUND, first.start(), first.target()).status());
                cursor.incrementAndGet(); PedestrianRoutePlanning.retireCompletedReadPhase();
            }
            var result = PedestrianRoutePlanning.query(GROUND, next.start(), next.target());
            if (result.awaitable()) PedestrianRoutePlanning.await(next);
            return (state, at) -> java.util.Optional.of(() -> () -> List.of(new ProposedEvent(owner.subject(), result.awaitable()
                    ? new ScheduleEffect.Rescheduled(owner.id(), owner) : new ScheduleEffect.Cancelled(owner.id()))));
        };
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            index.replace(owner, List.of(first)); planner.query(GROUND, first.start(), first.target());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges(), planner::readFact);
            assertTrue(index.reads(owner).holdsReadFor(first));
            PedestrianPlanningContinuations.advisory(owner, advice).prepare().revalidate(null, new SimInstant(20)).orElseThrow().prepare().get();
            assertFalse(index.reads(owner).holdsReadFor(first), "publication must propagate the explicit cursor checkpoint");
            assertTrue(index.waiting(owner));
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges(), planner::readFact);
            assertTrue(index.reads(owner).holdsReadFor(next));
            var result = PedestrianPlanningContinuations.advisory(owner, advice).prepare().revalidate(null, new SimInstant(30)).orElseThrow().prepare().get();
            assertInstanceOf(ScheduleEffect.Cancelled.class, result.getFirst().payload());
            assertEquals(1, cursor.get()); assertEquals(0, index.waitingCount());
        }
    }
    @Test void unfinishedAdviceResumesItsCapturedSnapshotInsteadOfAccumulatingNewInputEpochs() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("snapshot-owner", 20);
        var original = new java.util.concurrent.atomic.AtomicInteger(); var changed = new java.util.concurrent.atomic.AtomicInteger();
        var first = snapshotAdvice(owner, request(3), original, () -> true);
        var next = snapshotAdvice(owner, request(20), changed, () -> true);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            PedestrianPlanningContinuations.advisory(owner, first).prepare().revalidate(null, new SimInstant(20)).orElseThrow().prepare().get();
            assertTrue(index.waiting(owner)); assertTrue(index.advice(owner).isPresent());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges(), planner::readFact);
            var terminal = PedestrianPlanningContinuations.advisory(owner, next).prepare()
                    .revalidate(null, new SimInstant(30)).orElseThrow().prepare().get();
            assertInstanceOf(ScheduleEffect.Cancelled.class, terminal.getFirst().payload());
            assertEquals(2, original.get()); assertEquals(0, changed.get(), "a later world view is not the continuation's captured input");
            assertTrue(index.advice(owner).isEmpty()); assertEquals(0, index.waitingCount());
            PedestrianPlanningContinuations.advisory(owner, next).prepare();
            assertEquals(1, changed.get(), "terminal retirement releases the old calculation snapshot");
        }
    }
    @Test void rejectedAdviceReleasesItsSnapshotAndReadsBeforeTheNextPlanningEpoch() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("obsolete-snapshot", 20);
        var permitted = new java.util.concurrent.atomic.AtomicBoolean(true); var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            var old = snapshotAdvice(owner, request(3), calls, permitted::get);
            PedestrianPlanningContinuations.advisory(owner, old).prepare().revalidate(null, new SimInstant(20)).orElseThrow().prepare().get();
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges(), planner::readFact); assertTrue(index.reads(owner).holdsReadFor(request(3)));
            permitted.set(false);
            assertTrue(PedestrianPlanningContinuations.advisory(owner, old).prepare().revalidate(null, new SimInstant(30)).isEmpty());
            assertEquals(0, index.waitingCount()); assertTrue(index.advice(owner).isEmpty());
            assertFalse(index.reads(owner).holdsReadFor(request(3)));
        }
    }
    private static ScheduledAdvisory<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState> snapshotAdvice(
            ScheduledAction owner, PedestrianRouteRequest request, java.util.concurrent.atomic.AtomicInteger calls,
            java.util.function.BooleanSupplier permitted) {
        return () -> {
            calls.incrementAndGet(); var result = PedestrianRoutePlanning.query(GROUND, request.start(), request.target());
            if (result.awaitable()) PedestrianRoutePlanning.await(request);
            return (state, at) -> permitted.getAsBoolean() ? java.util.Optional.of(() -> () -> result.awaitable()
                    ? List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(),
                        new ScheduledAction(owner.id(), new SimInstant(at.ticks() + 1), owner.priority(), owner.subject(), owner.kind(), owner.weight()))))
                    : List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Cancelled(owner.id())))) : java.util.Optional.empty();
        };
    }
    @Test void deliveryDuringDetachedPreparationSurvivesItsOlderCompletionAndSharedEviction() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("in-flight-delivery", 20); var query = request(3);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            planner.query(GROUND, query.start(), query.target()); index.replace(owner, List.of(query));
            var oldCompletion = PedestrianPlanningContinuations.prepare(owner, () -> {
                var result = PedestrianRoutePlanning.query(GROUND, query.start(), query.target());
                assertEquals(PedestrianRouteResult.Status.PLANNING, result.status()); PedestrianRoutePlanning.await(query);
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), action("in-flight-delivery", 21))));
            }).prepare();
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges(), planner::readFact);
            for (int i = 0; i < 140; i++) {
                var point = SurfaceAnchor.at(i % 28 + 1, 63, i / 28 + 3);
                planner.query(GROUND, point, SurfaceAnchor.at(point.x() + 1, 63, point.z()));
                while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            }
            assertTrue(planner.peek(GROUND, query.start(), query.target()).isEmpty());
            oldCompletion.get();
            assertEquals(0, planner.pendingCount(), "older detached advice must acquire the delivered fact, not restart its evicted route");
            assertTrue(index.reads(owner).holdsReadFor(query), "publication must merge owner-side delivery received during preparation");
            assertFalse(index.waiting(owner));
            var terminal = PedestrianPlanningContinuations.prepare(owner, () -> {
                assertEquals(PedestrianRouteResult.Status.FOUND, PedestrianRoutePlanning.query(GROUND, query.start(), query.target()).status());
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Cancelled(owner.id())));
            }).prepare().get();
            assertInstanceOf(ScheduleEffect.Cancelled.class, terminal.getFirst().payload()); assertEquals(0, index.waitingCount());
        }
    }
    @Test void completedDataReleasesSharedCapacityBeforeFutureOwnersExecute() throws Exception {
        var index = new PedestrianPlanningWakeIndex();
        var owners = new java.util.ArrayList<ScheduledAction>();
        var requests = new java.util.ArrayList<PedestrianRouteRequest>();
        try (var planner = new CooperativePedestrianPlanner()) {
            for (int i = 0; i < 140; i++) {
                var point = SurfaceAnchor.at(i % 28 + 1, 63, i / 28 + 3);
                var request = PedestrianRouteRequest.of(GROUND, point, SurfaceAnchor.at(point.x() + 1, 63, point.z()));
                var owner = action("future-mailbox-" + i, 6000);
                owners.add(owner); requests.add(request); index.replace(owner, List.of(request));
                planner.query(GROUND, request.start(), request.target());
            }
            planner.retainDemandKeys(index.retainedDemandKeys(new SimInstant(20)));
            for (int slice = 0; slice < 1024 && planner.pendingCount() > 0; slice++) {
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
                index.changed(planner.drainChanges(), planner::readFact);
                planner.retainDemandKeys(index.retainedDemandKeys(new SimInstant(20)));
            }
            assertEquals(0, planner.pendingCount(), "future business deadlines must not pin the completed-result buffer");
            assertEquals(140, planner.completedCount());
            assertTrue(index.retainedDemandKeys(new SimInstant(20)).isEmpty(), "every subscribed owner acknowledged its own data");
            assertEquals(140, index.waitingCount(), "data delivery neither executes nor retires business work");
            assertTrue(owners.stream().allMatch(owner -> owner.dueAt().ticks() == 6000));
            var first = requests.getFirst();
            assertTrue(planner.peek(GROUND, first.start(), first.target()).isEmpty(), "real shared capacity evicted the oldest result");
            var intermediate = PedestrianRoutePlanning.prepare(planner.readView(), index.reads(owners.getFirst()), () ->
                    PedestrianRoutePlanning.query(GROUND, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(20, 63, 20)));
            assertEquals(PedestrianRouteResult.Status.PLANNING, intermediate.observation().result().status());
            var resumed = PedestrianRoutePlanning.prepare(planner.readView(), intermediate.reads(), () ->
                    PedestrianRoutePlanning.query(GROUND, first.start(), first.target()));
            assertEquals(PedestrianRouteResult.Status.FOUND, resumed.observation().result().status());
            assertEquals(0, planner.pendingCount(), "the waiting owner already has the evicted data");
            assertTrue(resumed.observation().requests().isEmpty());
            index.remove(owners.getFirst().id());
            assertEquals(PedestrianRouteResult.Status.PLANNING, PedestrianRoutePlanning.prepare(planner.readView(),
                    index.reads(owners.getFirst()), () -> PedestrianRoutePlanning.query(GROUND, first.start(), first.target()))
                    .observation().result().status(), "retiring the owner releases its private delivery");
        }
    }
    @Test void unfinishedPreparationKeepsItsFirstLegWhenSharedResultsAreEvictedBeforeTheSecondLeg() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("two-leg-preparation", 20);
        var first = request(3); var end = SurfaceAnchor.at(6, 63, 1);
        var prefix = java.util.stream.IntStream.range(0, 40).mapToObj(i ->
                SurfaceAnchor.at(i % 20 + 2, 63, i / 20 + 3)).toList();
        java.util.function.Supplier<List<ProposedEvent>> prepare = () -> {
            try {
                for (var point : prefix) KnownPedestrianNavigation.plannedBetween(GROUND, first.start(), point);
                KnownPedestrianNavigation.plannedBetween(GROUND, first.start(), first.target());
                KnownPedestrianNavigation.plannedBetween(GROUND, first.target(), end);
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Cancelled(owner.id())));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                assertEquals(PedestrianRouteResult.Status.PLANNING, unavailable.status());
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), action("two-leg-preparation", 21))));
            }
        };
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index)) {
            for (var point : prefix) {
                planner.query(GROUND, first.start(), point);
                while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            }
            planner.query(GROUND, first.start(), first.target());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            PedestrianPlanningContinuations.prepare(owner, prepare).prepare().get();
            assertTrue(index.waiting(owner));
            // Exercise real shared-result capacity, not a test-only invalidation.
            for (int i = 0; i < 140; i++) {
                var point = SurfaceAnchor.at(i % 28 + 1, 63, i / 28 + 3);
                planner.query(GROUND, point, SurfaceAnchor.at(point.x() + 1, 63, point.z()));
                while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            }
            assertTrue(planner.peek(GROUND, first.start(), first.target()).isEmpty());
            assertTrue(planner.peek(GROUND, first.target(), end).isEmpty());
            index.changed(planner.drainChanges()); index.ready(view(20, owner));
            PedestrianPlanningContinuations.prepare(owner, prepare).prepare().get();
            assertEquals(1, planner.pendingCount());
            assertEquals(first.target(), planner.activeDiagnostics().getFirst().start(),
                    "resume must request only the second leg, rather than restarting its consumed first proof");
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges()); index.ready(view(20, owner));
            var terminal = PedestrianPlanningContinuations.prepare(owner, prepare).prepare().get();
            assertInstanceOf(ScheduleEffect.Cancelled.class, terminal.getFirst().payload());
            assertEquals(0, index.waitingCount(), "terminal completion releases the calculation's private reads");
            PedestrianPlanningContinuations.prepare(owner, prepare).prepare().get();
            assertEquals(first.start(), planner.activeDiagnostics().getFirst().start(),
                    "a later calculation cannot inherit the completed continuation's private memory");
        }
    }
    @Test void recoveryCannotPreviewFutureBusinessOrAdvanceItsExecutionInstant() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var action = action("future-recovery", 6000);
        var planner = ScheduledPlanningRecovery.withPlanningRecovery(new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state, ScheduledAction due) {
                fail("recovery must retain the explicit execution instant"); return List.of();
            }
            @Override public List<ProposedEvent> plan(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state, ScheduledAction due, SimInstant at) {
                assertSame(action, due); assertEquals(new SimInstant(6000), at); calls.incrementAndGet(); return List.of();
            }
        });
        assertTrue(planner.recoveryPlan(null, action, new SimInstant(20)).isEmpty());
        assertEquals(0, calls.get(), "future economic/route work must not be executed during recovery");
        planner.recoveryPlan(null, action, action.dueAt()).orElseThrow().get();
        assertEquals(1, calls.get(), "the same due action retains ordinary recovery");
    }

    @Test void calculationOnlyWaitKeepsItsDueGenerationButBusinessTransitionRetainsItsDeadline() throws Exception {
        var index = new PedestrianPlanningWakeIndex(); var owner = action("calculation-only", 20);
        var future = action("calculation-only", 220); var query = request(3);
        try (var binding = PedestrianPlanningContinuations.bind(index)) {
            var result = PedestrianPlanningContinuations.plan(owner, () -> {
                PedestrianRoutePlanning.await(query);
                return List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), future)));
            });
            assertEquals(List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), owner))), result);
            assertTrue(index.waiting(owner));
            PedestrianPlanningContinuations.markParked(owner);
            index.changed(List.of(new PedestrianPlanningChange(query, PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertEquals(List.of(owner), index.ready(view(20, owner)), "ready result needs no future deadline acceleration");
            index.resumed(owner, owner); assertFalse(index.waiting(owner));
            var business = List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Consumed(new ScheduleId("other:owned"))),
                    new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), future)));
            assertEquals(business, PedestrianPlanningContinuations.plan(owner, () -> {
                PedestrianRoutePlanning.await(query); return business;
            }), "an actual transition keeps its own timing even when it consulted a pending route");
            index.remove(owner.id());
            var timed = List.of(new ProposedEvent(owner.subject(), new ScheduleEffect.Rescheduled(owner.id(), future)));
            assertEquals(timed, PedestrianPlanningContinuations.plan(owner, () -> timed), "a timer without a route wait stays a timer");
        }
    }
    @Test void onlyCurrentReadyConsumersRetainResultsAndARefusingOwnerCannotBlockOtherLanes() {
        var index = new PedestrianPlanningWakeIndex(); var first = action("first-retention", 20);
        var shared = action("shared-retention", 20); var future = action("future-retention", 6000);
        var query = request(3); var later = request(20);
        index.replace(first, List.of(query)); index.replace(shared, List.of(query)); index.restore(future, List.of(later));
        assertEquals(List.of(query), index.retainedRequests(new SimInstant(20)));
        var retry = action("live-retry", 40); index.replace(retry, List.of(later));
        assertEquals(java.util.Set.of(query, later), java.util.Set.copyOf(index.retainedRequests(new SimInstant(20))));
        index.remove(retry.id());
        index.ownerHeld(first, true);
        assertEquals(List.of(query), index.retainedRequests(new SimInstant(20)), "another current consumer still owns delivery");
        index.ownerHeld(shared, true); assertTrue(index.retainedRequests(new SimInstant(20)).isEmpty());
        index.ownerHeld(first, false); assertEquals(List.of(query), index.retainedRequests(new SimInstant(20)));
        assertEquals(java.util.Set.of(query, later), java.util.Set.copyOf(index.retainedRequests(new SimInstant(6000))));
    }
    @Test void detachedRecoveryPublishesOnlyRetainedUnregisteredWaits() throws Exception {
        var index = new PedestrianPlanningWakeIndex();
        var retained = action("retained", 6000); var replaced = action("replaced", 6000);
        var cancelled = action("cancelled", 6000);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner);
                var waits = PedestrianPlanningContinuations.bind(index);
                var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var preparation = PedestrianPlanningContinuations.prepareRestore(() -> java.util.stream.IntStream.range(0, 3)
                    .mapToObj(i -> new PedestrianPlanningContinuations.RecoveryCandidate(List.of(retained, replaced, cancelled).get(i), () -> {
                        var query = request(3 + i);
                        PedestrianRoutePlanning.query(GROUND, query.start(), query.target());
                        PedestrianRoutePlanning.await(query);
                        return List.of(new ProposedEvent(retained.subject(), new ScheduleEffect.Cancelled(retained.id())));
                    })).toList());
            var recovery = worker.submit(preparation::get).get();
            assertEquals(0, planner.pendingCount(), "worker cannot acquire live navigation work");
            assertEquals(0, index.waitingCount(), "worker cannot publish volatile wake ownership");
            index.replace(replaced, List.of(request(10)));
            recovery.publish(action -> !action.equals(cancelled));
            assertEquals(1, planner.pendingCount(), "stale or already refreshed advice cannot acquire work");
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges());
            assertEquals(List.of(retained), index.ready(view(20, retained, replaced)));
            index.changed(List.of(new PedestrianPlanningChange(request(10), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertEquals(List.of(retained, replaced), index.ready(view(20, retained, replaced)), "newer wait survives recovery");
        }
    }
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
    @Test void computedSuccessorRetainsItsReadyResultUntilItsExactCanonicalPublication() {
        var index = new PedestrianPlanningWakeIndex(); var before = action("publishing", 20); var next = action("publishing", 40);
        var query = request(3); index.stage(before, next, List.of(query));
        index.changed(List.of(new PedestrianPlanningChange(query, PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        assertTrue(index.ready(view(20, before)).isEmpty(), "an unpublished successor has no execution authority");
        assertEquals(1, index.waitingCount()); assertEquals(1, index.readyCount());
        assertEquals(List.of(query), index.retainedRequests(new SimInstant(20)), "durability delay cannot evict the successor's route");
        index.releaseUnused(ignored -> fail("pending publication cannot cancel the calculation"));
        assertEquals(List.of(next), index.ready(view(20, next)));
        index.resumed(next, next); assertFalse(index.waiting(next));
        index.stage(next, action("publishing", 60), List.of(query));
        assertTrue(index.ready(view(20, action("publishing", 80))).isEmpty());
        assertEquals(0, index.waitingCount(), "a genuinely superseded or cancelled producer releases its staged result");
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
    @Test void compatibleGeometryRefreshKeepsTheWaitAndTerminalDeliveryAddressesItsOriginalConsumer() {
        var revision = PedestrianGeometryRevision.initial();
        java.util.function.Supplier<PedestrianRouteGeometry> geometry = () -> new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 512, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(480, 63, 1);
        var initial = geometry.get(); var index = new PedestrianPlanningWakeIndex(); var owner = action("compatible", 6000);
        var first = PedestrianRouteRequest.of(initial, start, target);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(initial, start, target); index.replace(owner, List.of(first));
            for (int view = 0; view < 200; view++) {
                planner.query(geometry.get(), start, target);
                index.changed(planner.drainChanges());
                assertTrue(index.ready(view(20, owner)).isEmpty(), "unchanged facts are not calculation readiness");
                assertTrue(index.waiting(owner));
            }
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges());
            assertEquals(List.of(owner), index.ready(view(20, owner)), "completion survives the immutable address refresh");
            long work = planner.workUnits();
            var refreshed = geometry.get();
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.query(refreshed, start, target).status());
            assertTrue(planner.drainChanges().isEmpty()); assertEquals(work, planner.workUnits());
            // Advice may see both old and current immutable addresses in one continuation.
            // One consumer still releases one normalized demand, without orphaning aliases.
            index.replace(owner, List.of(first, PedestrianRouteRequest.of(refreshed, start, target)));
            index.remove(owner.id()); var released = new java.util.ArrayList<PedestrianRouteRequest>();
            index.releaseUnused(released::add); assertEquals(1, released.size());
        }
    }
    @Test void regionalRebaseKeepsUnresolvedWorkAndDeliversChangedTerrainOutcomeToItsConsumer() {
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
            index.changed(planner.drainChanges()); assertTrue(index.ready(view(20, owner, other)).isEmpty(),
                    "an unresolved search can inspect changed terrain without waking the domain early");
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
            assertTrue(index.waiting(owner));
            PedestrianPlanningContinuations.markParked(owner);
            var read = PedestrianRoutePlanning.observe(() -> PedestrianRoutePlanning.peek(GROUND, request(3).start(), request(3).target()));
            assertTrue(read.requests().isEmpty());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            index.changed(planner.drainChanges());
            assertEquals(List.of(owner), index.ready(view(20, owner)));
            index.resumed(owner, owner);
            // A later successful owner review retires the old wait instead of repeatedly consuming receipts.
            PedestrianPlanningContinuations.plan(owner, () -> {
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
