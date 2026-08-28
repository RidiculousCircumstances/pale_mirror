package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.LinkedHashMap;
import java.util.List;

/** Canonical admission and reduction for observations from non-leased ambient bodies. */
final class AmbientActorProcess {
    private AmbientActorProcess() { }

    static CommandPlan plan(FrontierWorldState state, AmbientActorDied death) {
        try {
            validate(state, death);
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, death.actorId()), death)));
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorDied death) {
        validate(state, death);
        if (!subject.equals(owner(state, death.actorId()))) throw new IllegalArgumentException("ambient actor death lacks its canonical owner");
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(death.actorId(), state.actorLocations().get(death.actorId()).deadAt(death.position()));
        return new FrontierWorldState(state.bootstrap(), nextActors, state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas());
    }

    private static void validate(FrontierWorldState state, AmbientActorDied death) {
        ActorLocation current = state.actorLocations().get(death.actorId());
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("ambient actor death is not evidence for a living canonical actor");
        }
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(death.actorId())))) {
            throw new IllegalArgumentException("leased actor death must use its scene lease evidence");
        }
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), death.position());
        if (owner(state, death.actorId()) == null) throw new IllegalArgumentException("ambient actor has no canonical owner");
    }

    private static SubjectId owner(FrontierWorldState state, SubjectId actorId) {
        return FrontierWorldStateSupport.actorOwner(state, actorId);
    }
}
