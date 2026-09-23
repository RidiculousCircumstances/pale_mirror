package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.List;

/**
 * Pure policy for a due action. A no-op must still be represented by an accepted event. The
 * kernel normally appends {@link ScheduleEffect.Consumed}; a planner that has discovered its
 * own action obsolete may instead emit one matching {@link ScheduleEffect.Cancelled},
 * {@link ScheduleEffect.Consumed}, or {@link ScheduleEffect.Rescheduled} event.
 */
@FunctionalInterface
public interface ScheduledActionPlanner<S> {
    List<ProposedEvent> plan(S state, ScheduledAction action);

    /** Canonical owner-declared hold; excludes work before budget admission without changing its deadline. */
    default boolean held(S state, ScheduledAction action) { return false; }

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
