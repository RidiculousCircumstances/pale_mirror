package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.LinkedHashMap;
import java.util.List;

/** Canonical admission and reduction for observations from non-leased ambient bodies. */
public final class AmbientActorProcess {
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
            requireUnleased(state, observation.actorId());
        } catch (IllegalArgumentException invalid) {
            return rejected(invalid);
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, observation.actorId()), observation)));
    }

    static CommandPlan plan(FrontierWorldState state, AmbientLeasePrepared prepared) {
        try { AmbientLeaseStateProcess.prepare(state, prepared.lease()); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, prepared.lease().actorId()), prepared)));
    }
    static CommandPlan plan(FrontierWorldState state, AmbientLeaseTransition transition) {
        try { AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status()); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, transition.actorId()), transition)));
    }
    static CommandPlan plan(FrontierWorldState state, AmbientLeaseReleased release) {
        try { AmbientLeaseStateProcess.release(state, release); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, release.actorId()), release)));
    }
    static CommandPlan planLease(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return switch (payload) {
            case AmbientLeasePrepared prepared -> plan(state, prepared);
            case AmbientLeaseTransition transition -> plan(state, transition);
            case AmbientLeaseReleased release -> plan(state, release);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }

    public static AmbientActorLease nextLease(FrontierWorldState state, SubjectId actorId, SimInstant instant) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient lease requires a living canonical actor");
        AmbientActorLease previous = state.ambientLeases().get(actorId);
        long revision = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        AmbientGoal goal = goal(state, actorId);
        return new AmbientActorLease(actorId, actor.position(), instant, revision, AmbientLeaseStatus.PREPARED, goal.kind(), goal.position());
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorDied death) {
        validate(state, death);
        if (!subject.equals(owner(state, death.actorId()))) throw new IllegalArgumentException("ambient actor death lacks its canonical owner");
        AmbientActorLease lease = state.ambientLeases().get(death.actorId());
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) return AmbientLeaseStateProcess.recordDeath(state, death);
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(death.actorId(), state.actorLocations().get(death.actorId()).deadAt(death.position()));
        return new FrontierWorldState(state.bootstrap(), nextActors, state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorObserved observation) {
        validate(state, observation.actorId(), observation.position());
        requireUnleased(state, observation.actorId());
        if (!subject.equals(owner(state, observation.actorId()))) throw new IllegalArgumentException("ambient actor observation lacks its canonical owner");
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(observation.actorId(), new ActorLocation(observation.position(), state.actorLocations().get(observation.actorId()).condition().withHealth(observation.health())));
        return new FrontierWorldState(state.bootstrap(), nextActors, state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, SimInstant instant, AmbientLeasePrepared prepared) {
        if (!subject.equals(owner(state, prepared.lease().actorId())) || !prepared.lease().handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("ambient lease lacks its owner or current handoff instant");
        }
        return AmbientLeaseStateProcess.prepare(state, prepared.lease());
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseTransition transition) {
        if (!subject.equals(owner(state, transition.actorId()))) throw new IllegalArgumentException("ambient lease transition lacks its canonical owner");
        return AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status());
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseReleased release) {
        if (!subject.equals(owner(state, release.actorId()))) throw new IllegalArgumentException("ambient lease release lacks its canonical owner");
        return AmbientLeaseStateProcess.release(state, release);
    }
    static FrontierWorldState reduceLease(FrontierWorldState state, SubjectId subject, SimInstant instant, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return switch (payload) {
            case AmbientLeasePrepared prepared -> reduce(state, subject, instant, prepared);
            case AmbientLeaseTransition transition -> reduce(state, subject, transition);
            case AmbientLeaseReleased release -> reduce(state, subject, release);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }

    private static void validate(FrontierWorldState state, AmbientActorDied death) {
        validate(state, death.actorId(), death.position());
    }
    private static void requireUnleased(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("leased ambient actor observation must use its ambient lease evidence");
        }
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

    private static AmbientGoal goal(FrontierWorldState state, SubjectId actorId) {
        for (Settlement settlement : state.bootstrap().settlements()) {
            for (Resident resident : settlement.residents()) {
                if (!resident.id().equals(actorId)) continue;
                if (resident.role() == ResidentRole.GUARD) return new AmbientGoal(AmbientGoalKind.GUARD, settlement.anchor());
                StructureKind kind = resident.role() == ResidentRole.FARMER ? StructureKind.FARM
                        : resident.role() == ResidentRole.MEDIC ? StructureKind.INFIRMARY : StructureKind.WORKSHOP;
                BlockPosition position = settlement.structures().stream().filter(structure -> structure.kind() == kind).findFirst()
                        .orElseThrow().anchor();
                return new AmbientGoal(AmbientGoalKind.WORK, position);
            }
        }
        Bioform bioform = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(actorId)).findFirst().orElseThrow(() -> new IllegalArgumentException("ambient actor has no canonical role"));
        BlockPosition nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(bioform.nestId())).findFirst().orElseThrow().anchor();
        return new AmbientGoal(bioform.role() == BioformRole.GUARD ? AmbientGoalKind.GUARD : AmbientGoalKind.PATROL, nest);
    }

    private static CommandPlan.Rejected rejected(IllegalArgumentException invalid) {
        return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
    }

    private static SubjectId owner(FrontierWorldState state, SubjectId actorId) {
        return FrontierWorldStateSupport.actorOwner(state, actorId);
    }
    private record AmbientGoal(AmbientGoalKind kind, BlockPosition position) { }
}
