package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;

import java.util.List;
import java.util.Set;

/**
 * Pure policy for a due action. A no-op must still be represented by an accepted event. The
 * kernel normally appends {@link ScheduleEffect.Consumed}; a planner that has discovered its
 * own action obsolete may instead emit one matching {@link ScheduleEffect.Cancelled},
 * {@link ScheduleEffect.Consumed}, or {@link ScheduleEffect.Rescheduled} event.
 */
@FunctionalInterface
public interface ScheduledActionPlanner<S> {
    List<ProposedEvent> plan(S state, ScheduledAction action);

    /** A held action may become runnable after its original due instant. */
    default List<ProposedEvent> plan(S state, ScheduledAction action, SimInstant currentInstant) {
        return plan(state, action);
    }

    /** Canonical owner-declared hold; excludes work before budget admission without changing its deadline. */
    default boolean held(S state, ScheduledAction action) { return false; }

    /** Nonempty keys allow a held action to leave the runnable index until one owner changes. */
    default Set<SubjectId> holdWakeKeys(S state, ScheduledAction action) { return Set.of(); }

    /** Derived wake signals from an accepted domain transition; they are not new canonical events. */
    default Set<SubjectId> wakeKeys(S previous, S next, FrontierEvent event) { return Set.of(); }

    /**
     * Exact pending actions retired by the owning domain transition. The kernel records
     * ordinary cancellation events in the same WAL transaction, before reference validation.
     * This is not permission to discard arbitrary dangling references or repair recovery.
     */
    default List<ScheduledAction> retiredBy(S previous, S next,
            io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event,
            java.util.function.Supplier<List<ScheduledAction>> pending) {
        return List.of();
    }
}
