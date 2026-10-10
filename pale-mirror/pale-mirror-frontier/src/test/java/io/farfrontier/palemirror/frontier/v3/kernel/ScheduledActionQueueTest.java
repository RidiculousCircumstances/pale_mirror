package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledActionQueueTest {
    @Test void explicitHintsCoalesceByOwnerAndKindWithoutLosingASuccessorOrExactFacts() {
        var queue = new ScheduledActionQueue();
        var first = action("schedule:hint-first", 20, 0, "settlement:a", 1);
        var mutation = queue.beginMutation();
        for (int i = 0; i < 100; i++) mutation.requestReconsideration(
                action("schedule:hint-" + i, 20 + i, 0, "settlement:a", 1));
        assertEquals(1, mutation.projectedSize());
        assertEquals(0, queue.size(), "uncommitted hints cannot alter the canonical queue");
        mutation.commit();
        var retained = queue.snapshot().getFirst();
        var earlier = queue.beginMutation(); earlier.requestReconsideration(first);
        earlier.requestReconsideration(action("schedule:earlier", 10, 0, "settlement:a", 1));
        earlier.requestReconsideration(action("schedule:another-owner", 10, 0, "settlement:b", 1));
        earlier.schedule(new ScheduledAction(new ScheduleId("schedule:receipt"), new SimInstant(10), 0,
                first.subject(), "effect.confirmation", 1));
        earlier.commit();
        assertEquals(3, queue.size());
        assertFalse(queue.containsExact(retained));
        assertTrue(queue.snapshot().stream().anyMatch(value -> value.kind().equals("effect.confirmation")));
        var next = queue.beginMutation();
        next.cancel(new ScheduleId("schedule:earlier"));
        var successor = action("schedule:successor", 11, 0, "settlement:a", 1);
        next.requestReconsideration(successor); next.commit();
        assertTrue(queue.containsExact(successor), "a change after consumption must get its own pending review");
        var invalid = queue.beginMutation();
        assertThrows(IllegalArgumentException.class, () -> invalid.requestReconsideration(
                action("schedule:conflicting-cost", 12, 0, "settlement:a", 2)));
        assertThrows(IllegalArgumentException.class, () -> invalid.requestReconsideration(
                action("schedule:successor", 11, 0, "settlement:foreign", 1)));
    }

    @Test void pressureSeparatesReadyHeldFutureAndExcludesParkedTimeAfterWake() {
        var queue = new ScheduledActionQueue();
        var held = action("schedule:held-pressure", 1, 0, "resident:a", 1);
        var ready = action("schedule:ready-pressure", 2, 0, "resident:b", 1);
        var future = action("schedule:future-pressure", 200, 0, "resident:c", 1);
        queue.schedule(held); queue.schedule(ready); queue.schedule(future);
        queue.selectDue(new SimInstant(10), new WorkBudget(1, 1), value -> !value.equals(held), value -> Set.of(held.subject()));
        var before = queue.pressure(new SimInstant(100), value -> !value.equals(held));
        assertEquals(1, before.ready()); assertEquals(1, before.held()); assertEquals(1, before.future());
        queue.cancel(ready.id()); queue.wake(Set.of(held.subject()), 100);
        var after = queue.pressure(new SimInstant(103), value -> true);
        assertEquals(3, after.oldestReadyLagTicks());
        assertEquals(102, after.oldestDeadlineLagTicks(), "held time is semantic deadline lateness, not ready service delay");
        assertEquals(0, after.held());
    }

    @Test void registeredAdmissionCostBoundsTheSameStableDueOrder() {
        var queue = new ScheduledActionQueue();
        for (int i = 0; i < 20; i++) queue.schedule(action("schedule:policy-" + i, 1, 0, "settlement:a", 1));
        var work = queue.selectDue(new SimInstant(1), new WorkBudget(64, 128), value -> true,
                value -> Set.of(), value -> 8);
        assertEquals(16, work.admitted().size());
        assertEquals(queue.snapshot().subList(0, 16), work.admitted());
        assertEquals(queue.snapshot().get(16), work.blockedAction());
    }
    @Test
    void parkedWaitersAreNotReevaluatedEachTickAndWakeInTheirOriginalDueOrder() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction waiting = action("schedule:waiting", 1L, 0, "resident:a", 1);
        ScheduledAction ready = action("schedule:ready", 2L, 0, "resident:b", 1);
        SubjectId depot = new SubjectId("container:depot-a");
        queue.schedule(waiting);
        queue.schedule(ready);
        AtomicInteger checks = new AtomicInteger();
        java.util.function.Predicate<ScheduledAction> eligible = action -> {
            if (action.equals(waiting)) { checks.incrementAndGet(); return false; }
            return true;
        };
        var keys = (java.util.function.Function<ScheduledAction, Set<SubjectId>>) action ->
                action.equals(waiting) ? Set.of(waiting.subject(), depot) : Set.of();
        assertEquals(List.of(ready), queue.selectDue(new SimInstant(2), new WorkBudget(2, 2), eligible, keys).admitted());
        assertEquals(List.of(ready), queue.selectDue(new SimInstant(20), new WorkBudget(2, 2), eligible, keys).admitted());
        assertEquals(1, checks.get(), "unchanged held resident must not be re-planned each tick");
        assertEquals(List.of(waiting, ready), queue.snapshot(), "parking does not change canonical membership");

        queue.wake(Set.of(depot));
        assertEquals(List.of(ready), queue.selectDue(new SimInstant(20), new WorkBudget(2, 2), eligible, keys).admitted());
        assertEquals(2, checks.get(), "one exact depot change rechecks its waiter");
        assertEquals(List.of(ready), queue.selectDue(new SimInstant(1_220), new WorkBudget(2, 2), eligible, keys).admitted());
        assertEquals(3, checks.get(), "bounded audit rechecks a missed wake without a WAL retry");
        assertEquals(0L, queue.auditReadyWithoutWake(), "still-blocked audit is not a missed wake");
        queue.cancel(waiting.id());
        queue.wake(Set.of(depot));
        assertEquals(List.of(ready), queue.snapshot());
    }

    @Test
    void auditReportsAnEligibleWaiterWhoseOwnerSignalWasMissed() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction waiter = action("schedule:audit-ready", 1L, 0, "resident:a", 1);
        SubjectId source = new SubjectId("container:audit-source");
        java.util.concurrent.atomic.AtomicBoolean available = new java.util.concurrent.atomic.AtomicBoolean();
        queue.schedule(waiter);
        var eligible = (java.util.function.Predicate<ScheduledAction>) action -> available.get();
        var keys = (java.util.function.Function<ScheduledAction, Set<SubjectId>>) action -> Set.of(source);
        assertTrue(queue.selectDue(new SimInstant(1L), new WorkBudget(1, 1), eligible, keys).admitted().isEmpty());
        available.set(true);
        assertTrue(queue.selectDue(new SimInstant(1_200L), new WorkBudget(1, 1), eligible, keys).admitted().isEmpty());
        assertEquals(List.of(waiter), queue.selectDue(new SimInstant(1_201L),
                new WorkBudget(1, 1), eligible, keys).admitted());
        assertEquals(1L, queue.auditReadyWithoutWake());
    }

    @Test
    void addressedWakeReadmitsAnEarlierDueActionAheadOfAnAlreadySelectedLaterOne() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction first = action("schedule:first", 1L, 0, "resident:a", 1);
        ScheduledAction later = action("schedule:later", 2L, 0, "resident:b", 1);
        queue.schedule(first); queue.schedule(later);
        SubjectId source = new SubjectId("container:depot-a");
        java.util.concurrent.atomic.AtomicBoolean available = new java.util.concurrent.atomic.AtomicBoolean();
        var eligible = (java.util.function.Predicate<ScheduledAction>) action -> !action.equals(first) || available.get();
        var keys = (java.util.function.Function<ScheduledAction, Set<SubjectId>>) action ->
                action.equals(first) ? Set.of(source) : Set.of();
        assertEquals(List.of(later), queue.selectDue(new SimInstant(2), new WorkBudget(2, 2), eligible, keys).admitted());
        available.set(true);
        queue.wake(Set.of(source));
        assertFalse(queue.isEligibleHead(later, new SimInstant(2), eligible, keys));
        assertEquals(List.of(first, later), queue.selectDue(new SimInstant(2), new WorkBudget(2, 2), eligible, keys).admitted());
    }
    @Test
    void equalDueActionsUseStablePrioritySubjectAndIdOrder() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        queue.schedule(action("schedule:c", 10L, 1, "settlement:b", 1));
        queue.schedule(action("schedule:b", 10L, 2, "settlement:c", 1));
        queue.schedule(action("schedule:a", 10L, 2, "settlement:a", 1));

        ScheduledWork work = queue.selectDue(new SimInstant(10L), new WorkBudget(3, 3));

        assertEquals(List.of("schedule:a", "schedule:b", "schedule:c"),
                work.admitted().stream().map(value -> value.id().value()).toList());
        assertFalse(work.dueWorkDeferred());
    }

    @Test
    void budgetDefersTheFirstUnadmittedActionWithoutReorderingOrLoss() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        queue.schedule(action("schedule:a", 10L, 0, "settlement:a", 2));
        queue.schedule(action("schedule:b", 10L, 0, "settlement:b", 2));

        ScheduledWork first = queue.selectDue(new SimInstant(10L), new WorkBudget(2, 3));
        queue.acknowledge(first.admitted().getFirst());
        ScheduledWork second = queue.selectDue(new SimInstant(10L), new WorkBudget(2, 3));

        assertEquals(List.of("schedule:a"), first.admitted().stream().map(value -> value.id().value()).toList());
        assertTrue(first.dueWorkDeferred());
        assertEquals(List.of("schedule:b"), second.admitted().stream().map(value -> value.id().value()).toList());
        assertFalse(second.dueWorkDeferred());
    }

    @Test
    void duplicateAndCancelledActionsAreExplicit() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction action = action("schedule:a", 10L, 0, "settlement:a", 1);
        queue.schedule(action);
        assertThrows(IllegalArgumentException.class, () -> queue.schedule(action));
        assertTrue(queue.cancel(action.id()));
        assertFalse(queue.cancel(action.id()));
        assertEquals(0, queue.size());
    }

    @Test
    void eligibleHeadStillFencesEarlierWorkCreatedAfterBatchAdmission() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction held = action("schedule:held", 1L, 0, "settlement:a", 1);
        ScheduledAction later = action("schedule:later", 10L, 0, "settlement:b", 1);
        ScheduledAction inserted = action("schedule:inserted", 5L, 0, "settlement:c", 1);
        java.util.function.Predicate<ScheduledAction> eligible = action -> !action.equals(held);
        queue.schedule(held); queue.schedule(later);
        assertEquals(List.of(later), queue.selectDue(new SimInstant(10L), new WorkBudget(1, 1), eligible).admitted());
        queue.schedule(inserted);
        assertFalse(queue.isEligibleHead(later, eligible), "snapshot membership is not execution authority");
        assertTrue(queue.isEligibleHead(inserted, eligible));
        queue.cancel(inserted.id());
        queue.cancel(later.id());
        assertFalse(queue.isEligibleHead(later, eligible), "cancelled snapshot entries cannot run");
        assertEquals(List.of(held), queue.snapshot());
    }

    @Test
    void transactionOverlayLeavesFutureWorkUntouchedUntilItsCanonicalCommit() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction first = action("schedule:first", 10L, 0, "settlement:a", 1);
        ScheduledAction second = action("schedule:second", 20L, 0, "settlement:b", 1);
        ScheduledAction replacement = action("schedule:replacement", 15L, 0, "settlement:c", 1);
        queue.schedule(first); queue.schedule(second);

        ScheduledActionQueue.Mutation mutation = queue.beginMutation();
        assertTrue(mutation.cancel(first.id()));
        mutation.schedule(replacement);

        assertEquals(List.of(first, second), queue.snapshot(), "a failed WAL append must leave canonical future work unchanged");
        assertEquals(replacement, mutation.head(), "the pending transaction still validates its own effective queue");

        mutation.commit();
        assertEquals(List.of(replacement, second), queue.snapshot());
        assertThrows(IllegalStateException.class, () -> mutation.schedule(first));
    }

    private static ScheduledAction action(String id, long dueAt, int priority, String subject, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(dueAt), priority,
                new SubjectId(subject), "process.tick", weight);
    }

    @Test
    void referenceDeltaContainsOnlyFinalRetainedCreationsAndReplacements() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        ScheduledAction original = action("schedule:original", 10L, 0, "settlement:a", 1);
        ScheduledAction transientAction = action("schedule:temporary", 20L, 0, "settlement:b", 1);
        ScheduledAction replacement = action("schedule:original", 30L, 0, "settlement:c", 1);
        queue.schedule(original);
        var mutation = queue.beginMutation();
        mutation.schedule(transientAction);
        mutation.cancel(transientAction.id());
        mutation.cancel(original.id());
        mutation.schedule(replacement);
        assertEquals(List.of(replacement), mutation.retainedChanges());
        assertEquals(List.of(original), queue.snapshot());
        mutation.commit();
        assertEquals(List.of(replacement), queue.snapshot());
    }
}
