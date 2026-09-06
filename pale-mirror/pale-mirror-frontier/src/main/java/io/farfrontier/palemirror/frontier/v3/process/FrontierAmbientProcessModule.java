package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact owner for ambient body observations and leases. */
final class FrontierAmbientProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof AmbientActorDied death) return AmbientActorProcess.plan(state, death);
        if (command.payload() instanceof AmbientActorObserved observation) return AmbientActorProcess.plan(state, observation);
        if (command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition
                || command.payload() instanceof AmbientLeaseReleased || command.payload() instanceof AmbientLeaseRestartAbsenceObserved) return AmbientActorProcess.planLease(state, command.payload());
        return FrontierWorldCommandPlanner.rejected("ambient process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition
                || event.payload() instanceof AmbientLeaseReleased || event.payload() instanceof AmbientLeaseRestartAbsenceObserved) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        return switch (event.payload()) {
            case AmbientActorDied death -> AmbientActorProcess.reduce(state, event.subject(), death);
            case AmbientActorObserved observation -> AmbientActorProcess.reduce(state, event.subject(), observation);
            default -> throw new IllegalArgumentException("ambient process does not own event: " + event.payload().type());
        };
    }
}
