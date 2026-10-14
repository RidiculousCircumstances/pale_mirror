package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;
import java.util.Set;

/** Decorates an explicitly registered review capability; the kernel knows no business families. */
final class ScheduledReviewAdmission {
    private ScheduledReviewAdmission() { }
    static FrontierScheduledPlanner policyReview(FrontierScheduledPlanner delegate) {
        return decorate(delegate, false);
    }
    static FrontierScheduledPlanner coalescedReview(FrontierScheduledPlanner delegate) {
        return decorate(delegate, true);
    }
    private static FrontierScheduledPlanner decorate(FrontierScheduledPlanner delegate, boolean coalescible) {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) { return delegate.plan(state, action); }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) { return delegate.plan(state, action, instant); }
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) { return delegate.held(state, action); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledHold<FrontierWorldState>>
                    holdProof(FrontierWorldState state, ScheduledAction action) { return delegate.holdProof(state, action); }
            @Override public int admissionWeight(FrontierWorldState state, ScheduledAction action) {
                return Math.max(action.weight(), state.bootstrap().ruleset().execution().policyReviewWeight());
            }
            @Override public boolean acceptsReconsideration() { return coalescible; }
            @Override public Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) { return delegate.wakeDependencies(state, action); }
            @Override public void restorePlanningWait(FrontierWorldState state, ScheduledAction action, SimInstant instant) { delegate.restorePlanningWait(state, action, instant); }
        };
    }
}
