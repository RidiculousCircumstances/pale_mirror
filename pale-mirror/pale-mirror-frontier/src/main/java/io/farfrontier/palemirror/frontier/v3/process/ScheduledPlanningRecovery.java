package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Recovery capability decorator; no family-specific execution policy. */
final class ScheduledPlanningRecovery {
    private ScheduledPlanningRecovery() { }
    /** Reconstruct only admitted due work. Future commands retain their ordinary admission. */
    static java.util.function.Supplier<PedestrianPlanningContinuations.Recovery> prepareWaits(
            DeterministicProcessRegistry registry, FrontierWorldState state, FrontierScheduleView view,
            Map<String, FrontierScheduledPlanner> planners) {
        return PedestrianPlanningContinuations.prepareRestore(() -> {
            var candidates = new java.util.ArrayList<PedestrianPlanningContinuations.RecoveryCandidate>();
            for (var action : view.schedules()) {
                registry.requireScheduledOwner(action.kind());
                // Future commands have not reached admission. Previewing them
                // invents future route demand and can solve every town's next
                // economic review before recovery lets ordinary time advance.
                if (action.dueAt().compareTo(view.instant()) > 0) continue;
                var planner = java.util.Objects.requireNonNull(planners.get(action.kind()), "recovery planner");
                if (!planner.held(state, action)) planner.recoveryPlan(state, action, view.instant())
                        .ifPresent(plan -> candidates.add(new PedestrianPlanningContinuations.RecoveryCandidate(action, plan)));
            }
            return java.util.List.copyOf(candidates);
        });
    }
    /** Explicit producer capability; recovery does not preview unrelated growth/birth/recipe clocks. */
    static FrontierScheduledPlanner withPlanningRecovery(FrontierScheduledPlanner delegate) {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) { return delegate.plan(state, action); }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                    io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) { return delegate.plan(state, action, instant); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation> preparation(
                    FrontierWorldState state, ScheduledAction action, io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
                return delegate.preparation(state, action, instant);
            }
            @Override public Optional<ScheduledAdvisory<FrontierWorldState>> advice(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return delegate.advice(state, action, instant);
            }
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) { return delegate.held(state, action); }
            @Override public boolean acceptsReconsideration() { return delegate.acceptsReconsideration(); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledHold<FrontierWorldState>>
                    holdProof(FrontierWorldState state, ScheduledAction action) { return delegate.holdProof(state, action); }
            @Override public Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) { return delegate.wakeDependencies(state, action); }
            @Override public Optional<java.util.function.Supplier<List<ProposedEvent>>> recoveryPlan(FrontierWorldState state, ScheduledAction action,
                    io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
                // Recovery rebuilds overdue calculation dependencies, not future
                // business forecasts. Normal admission owns a future deadline.
                if (action.dueAt().compareTo(instant) > 0) return Optional.empty();
                return Optional.of(() -> delegate.plan(state, action, instant));
            }
        };
    }

}
