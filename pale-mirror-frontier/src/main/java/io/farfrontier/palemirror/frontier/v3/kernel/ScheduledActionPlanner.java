package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.List;

/** Pure policy for a due action. A no-op must still be represented by an accepted event. */
@FunctionalInterface
public interface ScheduledActionPlanner<S> {
    List<ProposedEvent> plan(S state, ScheduledAction action);
}
