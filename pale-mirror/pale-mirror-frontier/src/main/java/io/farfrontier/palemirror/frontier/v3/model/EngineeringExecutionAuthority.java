package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** Engineering owns rendezvous, tools and cell settlement; UAE owns exact crew execution. */
public final class EngineeringExecutionAuthority {
    private EngineeringExecutionAuthority() { }
    public static Optional<ActorExecutionGroup> admission(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().map(team -> next(state, owner, ActorActivityKind.ENGINEERING_ASSEMBLY));
    }
    private static ActorExecutionGroup next(FrontierWorldState state, EngineeringWorkOrder owner, ActorActivityKind kind) {
        return new ActorExecutionGroup(owner.engineeringTeam().orElseThrow().memberIds().stream().sorted()
                .map(actor -> state.actorExecutions().next(actor, kind, owner.id())).toList());
    }
    public static ActorExecutionGroup current(FrontierWorldState state, EngineeringWorkOrder owner) {
        return new ActorExecutionGroup(owner.engineeringTeam().orElseThrow().memberIds().stream().sorted().map(actor -> {
            var retained = state.actorExecutions().actors().get(actor);
            var id = retained == null ? null : retained.current().orElse(null);
            if (id == null || !engineering(id.activityKind()) || !id.activityOwnerId().equals(owner.id()))
                throw new IllegalArgumentException("engineering crew lost its exact current owner");
            state.actorExecutions().requireCurrent(id);
            return id;
        }).toList());
    }
    public static Optional<EngineeringWorkOrder> owner(FrontierWorldState state, SubjectId actor) {
        var retained = state.actorExecutions().actors().get(actor);
        var id = retained == null ? null : retained.current().orElse(null);
        if (id == null || !engineering(id.activityKind())) return Optional.empty();
        state.actorExecutions().requireCurrent(id);
        return Optional.of(require(EngineeringWorkOrderSupport.registry(state.routeConstructions(), state.routeMaintenances()), id));
    }
    public static void requireWork(FrontierWorldState state, EngineeringWorkOrder owner) {
        if (owner.engineeringTeam().isPresent()) current(state, owner).requireDeclaration(
                ActorActivityKind.ENGINEERING_WORK, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
    }
    public static boolean crewOwnsWork(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().isPresent() && owner.engineeringTeam().orElseThrow().memberIds().stream()
                .allMatch(actor -> state.actorExecutions().owns(actor, ActorActivityKind.ENGINEERING_WORK, owner.id()));
    }
    public static ActorExecutionGroup assemblyCurrent(FrontierWorldState state, EngineeringWorkOrder owner) {
        var group = current(state, owner);
        group.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        return group;
    }
    public static Optional<ActorExecutionGroup> terminalDeclaration(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().map(team -> current(state, owner));
    }
    public static void requireOwner(ActorExecutionGroup group, SubjectId owner, ActorActivityKind kind) {
        for (var id : group.members()) if (id.activityKind() != kind || !id.activityOwnerId().equals(owner))
            throw new IllegalArgumentException("engineering payload has a foreign kind or owner");
    }
    public static void requireTerminal(FrontierWorldState state, EngineeringWorkOrder owner, Optional<ActorExecutionGroup> declared) {
        if (!terminalDeclaration(state, owner).equals(declared))
            throw new IllegalArgumentException("engineering terminal outcome lost its exact execution declaration");
    }
    public static Optional<ActorExecutionGroup> workAdmission(FrontierWorldState state, EngineeringWorkOrder owner,
                                                            EngineeringWorkAssembly assembly) {
        return startsWork(owner, assembly) ? Optional.of(next(state, owner, ActorActivityKind.ENGINEERING_WORK)) : Optional.empty();
    }
    private static boolean startsWork(EngineeringWorkOrder owner, EngineeringWorkAssembly assembly) {
        return owner.building() && assembly.complete() && assembly.purpose() == EngineeringJourneyPurpose.WORKSITE;
    }
    public static FrontierWorldState admit(FrontierWorldState state, EngineeringWorkOrder owner,
                                           Optional<ActorExecutionGroup> declaration, FrontierWorldStateUpdate update) {
        if (owner.engineeringTeam().isPresent() != declaration.isPresent())
            throw new IllegalArgumentException("engineering admission must declare exactly its actual crew");
        if (declaration.isEmpty()) return state.withChanges(update);
        var group = declaration.orElseThrow();
        group.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        return ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state, group).commit(state, update);
    }
    public static FrontierWorldState assembled(FrontierWorldState state, EngineeringWorkOrder owner,
                                               EngineeringWorkAssembly assembly, ActorExecutionGroup current,
                                               Optional<ActorExecutionGroup> work, FrontierWorldStateUpdate update) {
        current.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        current.requireCurrent(state.actorExecutions());
        if (startsWork(owner, assembly) != work.isPresent())
            throw new IllegalArgumentException("engineering assembly must explicitly acknowledge its work successor");
        if (work.isEmpty()) return state.withChanges(update);
        var successor = work.orElseThrow();
        successor.requireDeclaration(ActorActivityKind.ENGINEERING_WORK, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        return ActorExecutionComposition.LIFECYCLE.prepareAcknowledgedGroupHandoff(state, current, successor).commit(state, update);
    }
    /** An exact confirmed work receipt closes this cell and prepares the next owned journey. */
    public static FrontierWorldState workSettled(FrontierWorldState state, EngineeringWorkOrder owner, FrontierWorldStateUpdate update) {
        if (owner.engineeringTeam().isEmpty()) return state.withChanges(update);
        var current = current(state, owner);
        current.requireDeclaration(ActorActivityKind.ENGINEERING_WORK, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        // A confirmed resource/block effect still settles after a casualty. Do not mint a
        // walking successor for a dead actor or discard the family's retained death obligation.
        if (!crewLiving(state, owner)) return state.withChanges(update);
        return ActorExecutionComposition.LIFECYCLE.prepareAcknowledgedGroupHandoff(state, current,
                next(state, owner, ActorActivityKind.ENGINEERING_ASSEMBLY)).commit(state, update);
    }
    public static boolean crewLiving(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().isEmpty() || owner.engineeringTeam().orElseThrow().memberIds().stream()
                .allMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE);
    }
    public static ActorExecutionState retired(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().isEmpty() ? state.actorExecutions()
                : ActorExecutionComposition.LIFECYCLE.retireCurrentGroup(state.actorExecutions(), current(state, owner));
    }
    static void validateReferences(Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                    ActorExecutionState executions) {
        var owners = EngineeringWorkOrderSupport.registry(constructions, maintenances);
        for (var kind : java.util.List.of(ActorActivityKind.ENGINEERING_ASSEMBLY, ActorActivityKind.ENGINEERING_WORK))
            for (var id : executions.current(kind).values()) require(owners, id);
        for (var owner : owners.values()) owner.engineeringTeam().ifPresent(team -> {
            ActorActivityKind declaredKind = null;
            for (var actor : team.memberIds()) {
                var retained = executions.actors().get(actor);
                var id = retained == null ? null : retained.current().orElse(null);
                if (id == null) throw new IllegalArgumentException("retained engineering team lacks its execution");
                require(owners, id);
                if (!id.activityOwnerId().equals(owner.id()) || declaredKind != null && declaredKind != id.activityKind())
                    throw new IllegalArgumentException("engineering cohort has a foreign owner or mixed purposes");
                declaredKind = id.activityKind();
            }
        });
    }
    private static boolean engineering(ActorActivityKind kind) {
        return kind == ActorActivityKind.ENGINEERING_ASSEMBLY || kind == ActorActivityKind.ENGINEERING_WORK;
    }
    private static EngineeringWorkOrder require(Map<SubjectId, EngineeringWorkOrder> owners, ActorExecutionId id) {
        var owner = owners.get(id.activityOwnerId());
        if (!engineering(id.activityKind()) || owner == null || owner.engineeringTeam().isEmpty()
                || !owner.engineeringTeam().orElseThrow().memberIds().contains(id.actorId()))
            throw new IllegalArgumentException("engineering execution lost its declared work order or participant");
        return owner;
    }
    static ActorActivityCapability capability(ActorActivityKind kind) {
        if (!engineering(kind)) throw new IllegalArgumentException("unsupported engineering activity kind");
        return new ActorActivityCapability() {
            @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
                return request -> {
                    var owner = require(EngineeringWorkOrderSupport.registry(request.expectedState().routeConstructions(),
                            request.expectedState().routeMaintenances()), request.execution());
                    return EngineeringBodyCheckpoint.acknowledge(request, owner);
                };
            }
            @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
                if (kind != ActorActivityKind.ENGINEERING_ASSEMBLY || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY) return false;
                var owner = require(EngineeringWorkOrderSupport.registry(state.routeConstructions(), state.routeMaintenances()), id);
                if (owner.assembly().isEmpty()) return lease.goalBody().equals(state.actorLocations().get(id.actorId()).body());
                var member = owner.assembly().orElseThrow().members().get(id.actorId());
                var target = member.arrived() ? member.currentSurface() : member.nextSurface();
                return lease.goalBody().supportingSurface().equals(target);
            }
            @Override public ActorActivityKind kind() { return kind; }
            @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
            @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
            @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
                if (id.activityKind() != kind) throw new IllegalArgumentException("foreign engineering capability kind");
                require(EngineeringWorkOrderSupport.registry(state.routeConstructions(), state.routeMaintenances()), id);
            }
            @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
                validateReference(state, id);
                return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                        ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, id.activityOwnerId())));
            }
            @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("engineering owner must settle its tools, cell and journey"); }
            @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("engineering has no suspended continuation"); }
            @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { throw new IllegalArgumentException("engineering owner retires the exact crew"); }
        };
    }
}
