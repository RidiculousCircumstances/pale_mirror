package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningReady;
import java.util.List;
import java.util.Set;

/** Owns only the exact bound schedule wake. The original registered process still owns its decision. */
final class PedestrianPlanningProcessModule implements FrontierWorldProcessModule {
    static final String TYPE = "frontier.pedestrian_planning_ready";
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("pedestrian-planning",
            Set.of(TYPE), Set.of(), Set.of(), Set.of("kernel.schedule_rescheduled"), Set.of(TYPE));
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (!(command.payload() instanceof PedestrianPlanningReady value))
            throw new IllegalArgumentException("planning owner rejects a foreign wake");
        var expected = value.expected();
        if (command.scheduleBinding().isEmpty() || !command.scheduleBinding().orElseThrow().action().equals(expected))
            return FrontierWorldCommandPlanner.rejected("calculation wake lacks its exact engine schedule binding");
        if (expected.dueAt().ticks() <= command.submittedAt().ticks())
            return FrontierWorldCommandPlanner.rejected("calculation owner is already due");
        // Match ordinary owner wake semantics: run on the next canonical tick,
        // never manufacture a tick-zero review or execute inside notification admission.
        var replacement = new ScheduledAction(expected.id(), new SimInstant(Math.addExact(command.submittedAt().ticks(), 1L)), expected.priority(),
                expected.subject(), expected.kind(), expected.weight());
        return new CommandPlan.Accepted(List.of(new ProposedEvent(expected.subject(),
                new ScheduleEffect.Rescheduled(expected.id(), replacement))));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        throw new IllegalArgumentException("planning notifications have no world reducer");
    }
}
