package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected;
import io.farfrontier.palemirror.frontier.v3.model.ActorDeathConsequences;
import java.util.List;

/** Activity-independent physical-incarnation owner; no family/job/scene inspection. */
final class FrontierActorBodyProcessModule implements FrontierWorldProcessModule {
    private final ActorDeathConsequences deaths;
    private final ActorDeathFollowUpPlanner followUps;
    FrontierActorBodyProcessModule(ActorDeathConsequences deaths, ActorDeathFollowUpPlanner followUps) {
        this.deaths = java.util.Objects.requireNonNull(deaths); this.followUps = java.util.Objects.requireNonNull(followUps);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ActorBodyInspected inspected) {
            try {
                ActorBodyAuthority.inspected(state, inspected);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(inspected.body().actorId(), inspected)));
            } catch (IllegalArgumentException invalid) {
                return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
            }
        }
        if (command.payload() instanceof ActorBodyDied died) {
            try {
                var afterDeath = ActorBodyAuthority.died(state, died, deaths, command.submittedAt().ticks());
                var events = new java.util.ArrayList<ProposedEvent>();
                events.add(new ProposedEvent(died.body().actorId(), died));
                events.addAll(followUps.plan(state, afterDeath, died.body().actorId()));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) {
                return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
            }
        }
        if (command.payload() instanceof ActorBodyPresent present) {
            try {
                ActorBodyAuthority.present(state, present);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(present.body().actorId(), present)));
            } catch (IllegalArgumentException invalid) {
                return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
            }
        }
        if (command.payload() instanceof ActorBodyUnloaded unloaded) {
            try {
                ActorBodyAuthority.unloaded(state, unloaded);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(unloaded.body().actorId(), unloaded)));
            } catch (IllegalArgumentException invalid) {
                return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
            }
        }
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
        if (event.payload() instanceof ActorBodyInspected inspected) {
            if (!event.subject().equals(inspected.body().actorId()))
                throw new IllegalArgumentException("body inspection has a foreign actor subject");
            return ActorBodyAuthority.inspected(state, inspected);
        }
        if (event.payload() instanceof ActorBodyDied died) {
            if (!event.subject().equals(died.body().actorId()))
                throw new IllegalArgumentException("body death has a foreign actor subject");
            return ActorBodyAuthority.died(state, died, deaths, event.instant().ticks());
        }
        if (event.payload() instanceof ActorBodyPresent present) {
            if (!event.subject().equals(present.body().actorId()))
                throw new IllegalArgumentException("body presence has a foreign actor subject");
            return ActorBodyAuthority.present(state, present);
        }
        if (event.payload() instanceof ActorBodyUnloaded unloaded) {
            if (!event.subject().equals(unloaded.body().actorId()))
                throw new IllegalArgumentException("body unload has a foreign actor subject");
            return ActorBodyAuthority.unloaded(state, unloaded);
        }
        if (!(event.payload() instanceof ActorBodyReleased released) || !event.subject().equals(released.body().actorId()))
            throw new IllegalArgumentException("actor-body owner requires its exact actor subject");
        return ActorBodyAuthority.released(state, released.body());
    }
}
