package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledActionQueueTest {
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
