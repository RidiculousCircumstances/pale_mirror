package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;

/** Registered owner emission validation for exact and disposable preparation. */
final class ScheduledPlanningPreparation {
    private ScheduledPlanningPreparation() { }
    static java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation> prepare(
            DeterministicProcessRegistry registry, FrontierScheduledPlanner planner, FrontierWorldState state, ScheduledAction action,
            io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
        String processId = registry.requireScheduledOwner(action.kind());
        if (planner == null) throw new IllegalStateException("registered scheduled kind has no planner: " + action.kind());
        return planner.preparation(state, action, instant).map(preparation -> () -> {
            var completion = preparation.prepare();
            return () -> {
                var events = completion.get();
                return registry.validateEmissions(processId, events.isEmpty()
                        ? List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id()))) : events);
            };
        });
    }

    static java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAdvisory<FrontierWorldState>> advice(
            DeterministicProcessRegistry registry, FrontierScheduledPlanner planner, FrontierWorldState state, ScheduledAction action,
            io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
        String processId = registry.requireScheduledOwner(action.kind());
        java.util.Objects.requireNonNull(planner, "registered advisory planner");
        return planner.advice(state, action, instant).map(value -> PedestrianPlanningContinuations.advisory(action, value)).map(advice -> () -> {
            var completion = advice.prepare();
            return new ScheduledAdvisory.Completion<FrontierWorldState>() {
                @Override public String rejectionReason() { return completion.rejectionReason(); }
                @Override public java.util.Optional<ScheduledPreparation> revalidate(FrontierWorldState current, SimInstant at) {
                    return completion.revalidate(current, at).map(preparation -> () -> {
                        var exact = preparation.prepare();
                        return () -> {
                            var events = exact.get();
                            return registry.validateEmissions(processId, events.isEmpty()
                                    ? List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id()))) : events);
                        };
                    });
                }
            };
        });
    }

}
