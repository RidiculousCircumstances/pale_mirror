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

        ScheduledWork work = queue.takeDue(new SimInstant(10L), new WorkBudget(3, 3));

        assertEquals(List.of("schedule:a", "schedule:b", "schedule:c"),
                work.executed().stream().map(value -> value.id().value()).toList());
        assertFalse(work.dueWorkDeferred());
    }

    @Test
    void budgetDefersTheFirstUnadmittedActionWithoutReorderingOrLoss() {
        ScheduledActionQueue queue = new ScheduledActionQueue();
        queue.schedule(action("schedule:a", 10L, 0, "settlement:a", 2));
        queue.schedule(action("schedule:b", 10L, 0, "settlement:b", 2));

        ScheduledWork first = queue.takeDue(new SimInstant(10L), new WorkBudget(2, 3));
        ScheduledWork second = queue.takeDue(new SimInstant(10L), new WorkBudget(2, 3));

        assertEquals(List.of("schedule:a"), first.executed().stream().map(value -> value.id().value()).toList());
        assertTrue(first.dueWorkDeferred());
        assertEquals(List.of("schedule:b"), second.executed().stream().map(value -> value.id().value()).toList());
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

    private static ScheduledAction action(String id, long dueAt, int priority, String subject, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(dueAt), priority,
                new SubjectId(subject), "process.tick", weight);
    }
}
