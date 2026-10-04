package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** Cocoon release admits purpose; the hive owns staging, conflict and exact group departure. */
public final class HiveAssemblyExecutionAuthority {
    private HiveAssemblyExecutionAuthority() { }
    public static ActorExecutionId admission(FrontierWorldState state, SubjectId mobilization, SubjectId actor) {
        return state.actorExecutions().next(actor, ActorActivityKind.HIVE_TASK_ASSEMBLY, mobilization);
    }
    public static ActorExecutionId current(FrontierWorldState state, SubjectId mobilization, SubjectId actor) {
        var id = state.actorExecutions().current(ActorActivityKind.HIVE_TASK_ASSEMBLY).get(actor);
        if (id == null || !id.activityOwnerId().equals(mobilization))
            throw new IllegalArgumentException("hive assembly lost its exact participant execution");
        state.actorExecutions().requireCurrent(id);
        return id;
    }
    /** Read the producer-declared owner; roster membership validates it, never discovers it. */
    public static Optional<HiveMobilization> owner(FrontierWorldState state, SubjectId actor) {
        var id = state.actorExecutions().current(ActorActivityKind.HIVE_TASK_ASSEMBLY).get(actor);
        if (id == null) return Optional.empty();
        state.actorExecutions().requireCurrent(id);
        return Optional.of(require(state.hiveColony().mobilizations(), id));
    }
    public static ActorExecutionGroup currentGroup(FrontierWorldState state, HiveMobilization mobilization) {
        return new ActorExecutionGroup(mobilization.memberIds().stream()
                .map(actor -> current(state, mobilization.id(), actor)).toList());
    }
    public static void requireDeclaration(ActorExecutionId id, SubjectId mobilization, SubjectId actor) {
        if (id.activityKind() != ActorActivityKind.HIVE_TASK_ASSEMBLY
                || !id.activityOwnerId().equals(mobilization) || !id.actorId().equals(actor))
            throw new IllegalArgumentException("hive assembly has a foreign kind, owner or participant");
    }
    static void validateReferences(Map<SubjectId, HiveMobilization> mobilizations, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.HIVE_TASK_ASSEMBLY).values()) require(mobilizations, id);
        for (var mobilization : mobilizations.values()) if (ownsAssembly(mobilization))
            for (var actor : mobilization.releasedMemberIds()) {
                var id = executions.current(ActorActivityKind.HIVE_TASK_ASSEMBLY).get(actor);
                if (id == null || !id.activityOwnerId().equals(mobilization.id()))
                    throw new IllegalArgumentException("released hive participant lacks its assembly execution");
            }
    }
    private static boolean ownsAssembly(HiveMobilization mobilization) {
        return switch (mobilization.status()) {
            case WAKING, RELEASING, ASSEMBLING, CONFLICT -> true;
            case DEPARTED, RETURNING, COMPLETED -> false;
        };
    }
    private static HiveMobilization require(Map<SubjectId, HiveMobilization> mobilizations, ActorExecutionId id) {
        var mobilization = mobilizations.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.HIVE_TASK_ASSEMBLY || mobilization == null
                || !ownsAssembly(mobilization) || !mobilization.releasedMemberIds().contains(id.actorId()))
            throw new IllegalArgumentException("hive assembly execution has a foreign or departed owner");
        return mobilization;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            if (lease.goal() != AmbientGoalKind.HIVE_TASK_ASSEMBLY) return false;
            var owner = require(state.hiveColony().mobilizations(), id);
            if (owner.status() != HiveMobilizationStatus.ASSEMBLING)
                return lease.goalBody().equals(state.actorLocations().get(id.actorId()).body());
            var member = owner.assembly().orElseThrow().members().get(id.actorId());
            return lease.goalBody().supportingSurface().equals(member.arrived() ? member.currentSurface() : member.nextSurface());
        }
        @Override public ActorActivityKind kind() { return ActorActivityKind.HIVE_TASK_ASSEMBLY; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
            return request -> {
                var owner = require(request.expectedState().hiveColony().mobilizations(), request.execution());
                return HiveBodyCheckpoint.acknowledge(request, owner);
            };
        }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
            require(state.hiveColony().mobilizations(), id);
        }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var mobilization = require(state.hiveColony().mobilizations(), id);
            boolean staged = mobilization.status() == HiveMobilizationStatus.ASSEMBLING
                    && mobilization.assembly().orElseThrow().complete();
            return new ActorActivityCheckpoint(state, id, staged ? Optional.empty()
                    : Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY,
                    mobilization.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) {
            throw new IllegalArgumentException("hive owns the bounded coordinated assembly");
        }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) {
            throw new IllegalArgumentException("hive assembly has no suspended continuation");
        }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) {
            throw new IllegalArgumentException("the exact hive departure retires assembly authority");
        }
    }; }
}
