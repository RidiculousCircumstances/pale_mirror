package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementHotObserved;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted;

/** Single canonical reducer for purpose-independent actor goal travel. */
final class FrontierActorMovementProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (!(command.payload() instanceof ActorMovementHotObserved observed))
            return FrontierWorldCommandPlanner.rejected("actor movement does not admit command: " + command.payload().type());
        try {
            return new CommandPlan.Accepted(ActorMovementProcess.planHotObserved(state, observed,
                    command.submittedAt().ticks()));
        } catch (IllegalArgumentException invalid) {
            return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
        }
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ActorMovementColdAdvanced advanced -> ActorMovementProcess.reduceColdAdvanced(state, event.subject(), advanced);
            case ActorMovementHotObserved observed -> ActorMovementProcess.reduceHotObserved(state, event.subject(), observed,
                    event.instant().ticks());
            case ActorMovementInterrupted interrupted -> ResidentActivityProcess.retargetHotResident(
                    ActorMovementInterruptionPlanner.reduce(state, event.subject(), interrupted),
                    event.subject(), event.instant().ticks());
            default -> throw new IllegalArgumentException("actor movement does not own event: " + event.payload().type());
        };
    }
}
