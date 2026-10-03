package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyReleased;
import java.util.List;

/** Activity-independent physical-incarnation owner; no family/job/scene inspection. */
final class FrontierActorBodyProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (!(command.payload() instanceof ActorBodyReleased released))
            return FrontierWorldCommandPlanner.rejected("actor-body owner does not admit " + command.payload().type());
        try {
            ActorBodyAuthority.released(state, released.body());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(released.body().actorId(), released)));
        } catch (IllegalArgumentException invalid) {
            return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
        }
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (!(event.payload() instanceof ActorBodyReleased released) || !event.subject().equals(released.body().actorId()))
            throw new IllegalArgumentException("actor-body owner requires its exact actor subject");
        return ActorBodyAuthority.released(state, released.body());
    }
}
