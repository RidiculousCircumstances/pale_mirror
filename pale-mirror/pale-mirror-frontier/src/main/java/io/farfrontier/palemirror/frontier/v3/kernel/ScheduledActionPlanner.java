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
}
