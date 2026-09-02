package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.LinkedHashMap;
import java.util.List;

/** Canonical admission and reduction for observations from non-leased ambient bodies. */
public final class AmbientActorProcess {
    private AmbientActorProcess() { }

    public static CommandPlan plan(FrontierWorldState state, AmbientActorDied death) {
        try {
            validate(state, death);
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        java.util.ArrayList<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(owner(state, death.actorId()), death));
        CompanyFoundationProcess.terminationForDeath(state, death.actorId()).ifPresent(events::add);
        events.addAll(ProductionProcess.failPreEffectWorkForDeath(state, death.actorId()));
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    public static CommandPlan plan(FrontierWorldState state, AmbientActorObserved observation) {
        try {
            validate(state, observation.actorId(), observation.body());
            requireUnleased(state, observation.actorId());
        } catch (IllegalArgumentException invalid) {
            return rejected(invalid);
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, observation.actorId()), observation)));
    }

    public static CommandPlan plan(FrontierWorldState state, AmbientLeasePrepared prepared) {
        try { AmbientLeaseStateProcess.prepare(state, prepared.lease()); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, prepared.lease().actorId()), prepared)));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseTransition transition) {
        try { AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status()); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, transition.actorId()), transition)));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseReleased release) {
        try { AmbientLeaseStateProcess.release(state, release); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, release.actorId()), release)));
    }
    public static CommandPlan plan(FrontierWorldState state, AmbientLeaseRestartAbsenceObserved absence) {
        try { AmbientLeaseStateProcess.resolveRestartAbsence(state, absence); } catch (IllegalArgumentException invalid) { return rejected(invalid); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner(state, absence.actorId()), absence)));
    }
    public static CommandPlan planLease(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return switch (payload) {
            case AmbientLeasePrepared prepared -> plan(state, prepared);
            case AmbientLeaseTransition transition -> plan(state, transition);
            case AmbientLeaseReleased release -> plan(state, release);
            case AmbientLeaseRestartAbsenceObserved absence -> plan(state, absence);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }

    public static AmbientActorLease nextLease(FrontierWorldState state, SubjectId actorId, SimInstant instant) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("ambient lease requires a living canonical actor");
        AmbientActorLease previous = state.ambientLeases().get(actorId);
        long revision = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        AmbientGoal goal = goalFor(state, actorId);
        return new AmbientActorLease(actorId, actor.body(), instant, revision, AmbientLeaseStatus.PREPARED, goal.kind(), BodyPosition.above(new SurfaceAnchor(goal.position())));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorDied death) {
        validate(state, death);
        if (!subject.equals(owner(state, death.actorId()))) throw new IllegalArgumentException("ambient actor death lacks its canonical owner");
        AmbientActorLease lease = state.ambientLeases().get(death.actorId());
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) return AmbientLeaseStateProcess.recordDeath(state, death);
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(death.actorId(), state.actorLocations().get(death.actorId()).deadAt(death.body()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(nextActors));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientActorObserved observation) {
        validate(state, observation.actorId(), observation.body());
        requireUnleased(state, observation.actorId());
        if (!subject.equals(owner(state, observation.actorId()))) throw new IllegalArgumentException("ambient actor observation lacks its canonical owner");
        var nextActors = new LinkedHashMap<>(state.actorLocations());
        nextActors.put(observation.actorId(), new ActorLocation(observation.body(), state.actorLocations().get(observation.actorId()).condition().withHealth(observation.health())));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(nextActors));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, SimInstant instant, AmbientLeasePrepared prepared) {
        if (!subject.equals(owner(state, prepared.lease().actorId())) || !prepared.lease().handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("ambient lease lacks its owner or current handoff instant");
        }
        return AmbientLeaseStateProcess.prepare(state, prepared.lease());
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseTransition transition) {
        if (!subject.equals(owner(state, transition.actorId()))) throw new IllegalArgumentException("ambient lease transition lacks its canonical owner");
        return AmbientLeaseStateProcess.transition(state, transition.actorId(), transition.status());
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseReleased release) {
        if (!subject.equals(owner(state, release.actorId()))) throw new IllegalArgumentException("ambient lease release lacks its canonical owner");
        return AmbientLeaseStateProcess.release(state, release);
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, AmbientLeaseRestartAbsenceObserved absence) {
        if (!subject.equals(owner(state, absence.actorId()))) throw new IllegalArgumentException("ambient restart absence lacks its canonical owner");
        return AmbientLeaseStateProcess.resolveRestartAbsence(state, absence);
    }
    public static FrontierWorldState reduceLease(FrontierWorldState state, SubjectId subject, SimInstant instant, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return switch (payload) {
            case AmbientLeasePrepared prepared -> reduce(state, subject, instant, prepared);
            case AmbientLeaseTransition transition -> reduce(state, subject, transition);
            case AmbientLeaseReleased release -> reduce(state, subject, release);
            case AmbientLeaseRestartAbsenceObserved absence -> reduce(state, subject, absence);
            default -> throw new IllegalArgumentException("payload is not an ambient lease transition");
        };
    }

    private static void validate(FrontierWorldState state, AmbientActorDied death) {
        validate(state, death.actorId(), death.body());
    }
    private static void requireUnleased(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("leased ambient actor observation must use its ambient lease evidence");
        }
    }

    private static void validate(FrontierWorldState state, SubjectId actorId, BodyPosition body) {
        ActorLocation current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("ambient actor observation is not evidence for a living canonical actor");
        }
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))) {
            throw new IllegalArgumentException("leased actor observation must use its scene lease evidence");
        }
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), body.supportingSurface().support());
        if (owner(state, actorId) == null) throw new IllegalArgumentException("ambient actor has no canonical owner");
    }

    public static AmbientGoal goalFor(FrontierWorldState state, SubjectId actorId) {
        EngineeringWorkOrder engineering = java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .filter(project -> project.building() || project.readyForToolReturn())
                .filter(project -> project.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .findFirst().orElse(null);
        if (engineering != null) {
            EngineeringWorkAssembly.Member member = engineering.assembly().orElseThrow().members().get(actorId);
            BlockPosition target = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
            return new AmbientGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, target);
        }
        RouteOperation assembling = state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false)).findFirst().orElse(null);
        if (assembling != null) {
            OperationAssembly.Member member = assembling.activeAssembly().orElseThrow().members().get(actorId);
            SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
            return new AmbientGoal(AmbientGoalKind.OPERATION_ASSEMBLY, target.support());
        }
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey != null) {
            BlockPosition target = journey.status() == ResidentMigrationStatus.EN_ROUTE && !journey.arriving()
                    ? journey.nextColdPosition() : journey.currentPosition();
            return new AmbientGoal(AmbientGoalKind.TRANSIT, target);
        }
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) {
            Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
            if (resident.profession() == ResidentProfession.SECURITY_WORKER) return new AmbientGoal(AmbientGoalKind.GUARD, settlement.anchor());
            StructureKind kind = resident.profession() == ResidentProfession.AGRICULTURAL_WORKER ? StructureKind.FARM
                    : resident.profession() == ResidentProfession.MEDICAL_WORKER ? StructureKind.INFIRMARY : StructureKind.WORKSHOP;
            BlockPosition position = settlement.structures().stream().filter(structure -> structure.kind() == kind).findFirst()
                    .orElseThrow().anchor();
            return new AmbientGoal(AmbientGoalKind.WORK, position);
        }
        Bioform bioform = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(actorId)).findFirst().orElseThrow(() -> new IllegalArgumentException("ambient actor has no canonical role"));
        BlockPosition nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(bioform.nestId())).findFirst().orElseThrow().anchor();
        if (bioform.role() == BioformRole.SCOUT) return new AmbientGoal(AmbientGoalKind.SCOUT_PATROL,
                HiveScoutPatrolProcess.nextPosition(state, bioform));
        return new AmbientGoal(bioform.role() == BioformRole.GUARD ? AmbientGoalKind.GUARD : AmbientGoalKind.PATROL, nest);
    }

    private static CommandPlan.Rejected rejected(IllegalArgumentException invalid) {
        return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
    }

    private static SubjectId owner(FrontierWorldState state, SubjectId actorId) {
        return FrontierWorldStateSupport.actorOwner(state, actorId);
    }
    public record AmbientGoal(AmbientGoalKind kind, BlockPosition position) { }
}
