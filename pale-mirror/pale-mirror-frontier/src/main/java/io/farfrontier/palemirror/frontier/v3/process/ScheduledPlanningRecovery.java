package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Recovery capability decorator; no family-specific execution policy. */
final class ScheduledPlanningRecovery {
    private ScheduledPlanningRecovery() { }
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
            @Override public boolean held(FrontierWorldState state, ScheduledAction action) { return delegate.held(state, action); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledHold<FrontierWorldState>>
                    holdProof(FrontierWorldState state, ScheduledAction action) { return delegate.holdProof(state, action); }
            @Override public Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) { return delegate.wakeDependencies(state, action); }
            @Override public Optional<java.util.function.Supplier<List<ProposedEvent>>> recoveryPlan(FrontierWorldState state, ScheduledAction action,
                    io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
                // This is a read-only owner preview; its events never enter the engine.
                return Optional.of(() -> delegate.plan(state, action,
                        new io.farfrontier.palemirror.frontier.v3.api.SimInstant(Math.max(instant.ticks(), action.dueAt().ticks()))));
            }
        };
    }

}
