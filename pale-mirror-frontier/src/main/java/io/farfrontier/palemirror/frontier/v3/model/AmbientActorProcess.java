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

    static CommandPlan plan(FrontierWorldState state, AmbientActorObserved observation) {
        try {
            validate(state, observation.actorId(), observation.position());
        } catch (IllegalArgumentException invalid) {
            return rejected(invalid);
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, observation.actorId()), observation)));
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

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorObserved observation) {
        validate(state, observation.actorId(), observation.position());
        if (!subject.equals(owner(state, observation.actorId()))) throw new IllegalArgumentException("ambient actor observation lacks its canonical owner");
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(observation.actorId(), new ActorLocation(observation.position(), state.actorLocations().get(observation.actorId()).condition().withHealth(observation.health())));
        return new FrontierWorldState(state.bootstrap(), nextActors, state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas());
    }

    private static void validate(FrontierWorldState state, AmbientActorDied death) {
        validate(state, death.actorId(), death.position());
    }

    private static void validate(FrontierWorldState state, SubjectId actorId, BlockPosition position) {
        ActorLocation current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("ambient actor observation is not evidence for a living canonical actor");
        }
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))) {
            throw new IllegalArgumentException("leased actor observation must use its scene lease evidence");
        }
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position);
        if (owner(state, actorId) == null) throw new IllegalArgumentException("ambient actor has no canonical owner");
    }

    private static CommandPlan.Rejected rejected(IllegalArgumentException invalid) {
        return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
    }

    private static SubjectId owner(FrontierWorldState state, SubjectId actorId) {
        return FrontierWorldStateSupport.actorOwner(state, actorId);
    }
}
