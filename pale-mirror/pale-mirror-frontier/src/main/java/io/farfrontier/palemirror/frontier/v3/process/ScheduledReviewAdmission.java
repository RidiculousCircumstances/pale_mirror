package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntBiFunction;

/** Decorates an explicitly registered review capability; the kernel knows no business families. */
final class ScheduledReviewAdmission {
    private ScheduledReviewAdmission() { }
    static FrontierScheduledPlanner policyReview(FrontierScheduledPlanner delegate) {
        return policyReview(delegate, (state, action) -> 1);
    }
    static FrontierScheduledPlanner coalescedReview(FrontierScheduledPlanner delegate) {
        return coalescedReview(delegate, (state, action) -> 1);
    }
    static FrontierScheduledPlanner policyReview(FrontierScheduledPlanner delegate,
            ToIntBiFunction<FrontierWorldState, ScheduledAction> workUnits) {
        return decorate(delegate, false, workUnits);
    }
    static FrontierScheduledPlanner coalescedReview(FrontierScheduledPlanner delegate,
            ToIntBiFunction<FrontierWorldState, ScheduledAction> workUnits) {
        return decorate(delegate, true, workUnits);
    }
    private static FrontierScheduledPlanner decorate(FrontierScheduledPlanner delegate, boolean coalescible,
            ToIntBiFunction<FrontierWorldState, ScheduledAction> workUnits) {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) { return delegate.plan(state, action); }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) { return delegate.plan(state, action, instant); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation> preparation(
                    FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return delegate.preparation(state, action, instant);
            }
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) { return delegate.held(state, action); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledHold<FrontierWorldState>>
                    holdProof(FrontierWorldState state, ScheduledAction action) { return delegate.holdProof(state, action); }
            @Override public int admissionWeight(FrontierWorldState state, ScheduledAction action) {
                int units = workUnits.applyAsInt(state, action);
                if (units < 1) throw new IllegalArgumentException("review owner declared nonpositive work");
                var policy = state.bootstrap().ruleset().execution();
                // Saturate just above half a turn: two largest reviews cannot
                // batch together, while cheap independent actions retain headroom.
                // Admission never drops alternatives or changes consent.
                int largestReview = policy.maxWeight() / 2 + 1;
                int cost = (int) Math.min(largestReview, (long) units * policy.policyReviewWeight());
                return Math.max(Math.max(action.weight(), delegate.admissionWeight(state, action)),
                        Math.max(policy.policyReviewWeight(), cost));
            }
            @Override public boolean acceptsReconsideration() { return coalescible; }
            @Override public Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) { return delegate.wakeDependencies(state, action); }
            @Override public void restorePlanningWait(FrontierWorldState state, ScheduledAction action, SimInstant instant) { delegate.restorePlanningWait(state, action, instant); }
        };
    }
}
