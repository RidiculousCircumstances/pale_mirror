package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** The hive owns survivor/homeward semantics; UAE owns only exact participant authority. */
public final class HiveReturnExecutionAuthority {
    private HiveReturnExecutionAuthority() { }
    public static ActorExecutionId current(FrontierWorldState state, SubjectId mobilization, SubjectId actor) {
        var id = state.actorExecutions().current(ActorActivityKind.HIVE_TASK_RETURN).get(actor);
        if (id == null || !id.activityOwnerId().equals(mobilization))
            throw new IllegalArgumentException("hive return lost its exact participant execution");
        state.actorExecutions().requireCurrent(id);
        return id;
    }
    public static Optional<HiveMobilization> owner(FrontierWorldState state, SubjectId actor) {
        var id = state.actorExecutions().current(ActorActivityKind.HIVE_TASK_RETURN).get(actor);
        if (id == null) return Optional.empty();
        state.actorExecutions().requireCurrent(id);
        return Optional.of(require(state.hiveColony().mobilizations(), id));
    }
    public static ActorExecutionGroup currentGroup(FrontierWorldState state, HiveMobilization mobilization) {
        return new ActorExecutionGroup(mobilization.returnAssembly().orElseThrow().members().keySet().stream().sorted()
                .map(actor -> current(state, mobilization.id(), actor)).toList());
    }
    public static void requireDeclaration(ActorExecutionId id, SubjectId mobilization, SubjectId actor) {
        if (id.activityKind() != ActorActivityKind.HIVE_TASK_RETURN
                || !id.activityOwnerId().equals(mobilization) || !id.actorId().equals(actor))
            throw new IllegalArgumentException("hive return has a foreign kind, owner or participant");
    }
    /** Discovery occurs only at the new return admission, never on a later movement callback. */
    public static HiveReturnAdmission planResolution(FrontierWorldState state, SettlementAssault assault) {
        var parents = state.hiveColony().mobilizations().values().stream()
                .filter(parent -> parent.taskId().equals(assault.taskId())).toList();
        if (parents.isEmpty()) return new HiveReturnAdmission.Independent();
        if (parents.size() != 1) throw new IllegalArgumentException("assault has multiple hive parents");
        var parent = requireParent(parents.getFirst(), assault);
        if (parent.memberIds().stream().noneMatch(actor -> alive(state, actor))) return new HiveReturnAdmission.Completed(parent.id());
        var returning = HiveAssemblyCorridor.compileReturn(state, parent);
        if (returning.complete()) return new HiveReturnAdmission.Completed(parent.id());
        var group = new ActorExecutionGroup(returning.members().keySet().stream().sorted().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.HIVE_TASK_RETURN, parent.id())).toList());
        return new HiveReturnAdmission.Returning(parent.id(), returning, group);
    }
    public static FrontierWorldState resolve(FrontierWorldState state, SettlementAssault assault,
                                            StrategicPlanState plans, HiveReturnAdmission admission) {
        return switch (admission) {
            case HiveReturnAdmission.Independent ignored -> {
                if (state.hiveColony().mobilizations().values().stream().anyMatch(parent -> parent.taskId().equals(assault.taskId())))
                    throw new IllegalArgumentException("independent resolution cannot discard its declared hive parent");
                yield state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(plans).actorExecutions(
                        ActorExecutionComposition.LIFECYCLE.retireCurrentGroup(state.actorExecutions(), SettlementAssaultExecutionAuthority.current(state, assault))));
            }
            case HiveReturnAdmission.Completed completed -> {
                var parent = requireParent(state.hiveColony().mobilizations().get(completed.mobilizationId()), assault);
                if (parent.memberIds().stream().anyMatch(actor -> alive(state, actor))
                        && !HiveAssemblyCorridor.compileReturn(state, parent).complete())
                    throw new IllegalArgumentException("hive survivors still require their exact return");
                yield state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(plans)
                        .hiveColony(state.hiveColony().completeMobilization(parent.id())).actorExecutions(
                                ActorExecutionComposition.LIFECYCLE.retireCurrentGroup(state.actorExecutions(), SettlementAssaultExecutionAuthority.current(state, assault))));
            }
            case HiveReturnAdmission.Returning returning -> {
                var parent = requireParent(state.hiveColony().mobilizations().get(returning.mobilizationId()), assault);
                if (!returning.assembly().equals(HiveAssemblyCorridor.compileReturn(state, parent)))
                    throw new IllegalArgumentException("return admission substituted survivor routes or predecessors");
                yield ActorExecutionComposition.LIFECYCLE.prepareAcknowledgedGroupHandoff(state,
                        SettlementAssaultExecutionAuthority.current(state, assault), returning.executions()).commit(state,
                        FrontierWorldStateUpdate.begin().strategicPlans(plans)
                                .hiveColony(state.hiveColony().beginMobilizationReturn(parent.id(), returning.assembly())));
            }
        };
    }
    private static HiveMobilization requireParent(HiveMobilization parent, SettlementAssault assault) {
        if (parent == null || parent.status() != HiveMobilizationStatus.DEPARTED
                || !parent.taskId().equals(assault.taskId()) || !parent.hiveId().equals(assault.hiveId())
                || !parent.expeditionId().equals(assault.expeditionId()) || !parent.sighting().equals(assault.sighting())
                || !parent.memberIds().equals(assault.attackerIds()))
            throw new IllegalArgumentException("return admission has a foreign or terminal parent");
        return parent;
    }
    private static boolean alive(FrontierWorldState state, SubjectId actor) {
        var location = state.actorLocations().get(actor);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE;
    }
    static void validateReferences(Map<SubjectId, HiveMobilization> parents, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.HIVE_TASK_RETURN).values()) require(parents, id);
        for (var parent : parents.values()) if (parent.status() == HiveMobilizationStatus.RETURNING)
            for (var actor : parent.returnAssembly().orElseThrow().members().keySet()) {
                var id = executions.current(ActorActivityKind.HIVE_TASK_RETURN).get(actor);
                if (id == null || !id.activityOwnerId().equals(parent.id()))
                    throw new IllegalArgumentException("returning survivor lacks its execution");
            }
    }
    private static HiveMobilization require(Map<SubjectId, HiveMobilization> parents, ActorExecutionId id) {
        var parent = parents.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.HIVE_TASK_RETURN || parent == null
                || parent.status() != HiveMobilizationStatus.RETURNING
                || !parent.returnAssembly().orElseThrow().members().containsKey(id.actorId()))
            throw new IllegalArgumentException("hive return execution has a foreign or completed owner");
        return parent;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
            return Optional.of((state, execution, tick) -> {
                var parent = require(state.hiveColony().mobilizations(), execution);
                var colony = state.hiveColony().acknowledgeReturnCasualty(parent.id(), execution.actorId());
                var update = FrontierWorldStateUpdate.begin().hiveColony(colony);
                if (colony.mobilizations().get(parent.id()).status() == HiveMobilizationStatus.COMPLETED)
                    return new ActorActivityDeath.Acknowledgement(state, execution, update,
                            ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP, Optional.of(currentGroup(state, parent)));
                return new ActorActivityDeath.Acknowledgement(state, execution, update,
                        ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
            });
        }
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            if (lease.goal() != AmbientGoalKind.HIVE_TASK_RETURN) return false;
            var owner = require(state.hiveColony().mobilizations(), id);
            var member = owner.returnAssembly().orElseThrow().members().get(id.actorId());
            return lease.goalBody().supportingSurface().equals(member.arrived() ? member.currentSurface() : member.nextSurface());
        }
        @Override public ActorActivityKind kind() { return ActorActivityKind.HIVE_TASK_RETURN; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
            return request -> {
                var owner = require(request.expectedState().hiveColony().mobilizations(), request.execution());
                if (!owner.returnAssembly().orElseThrow().members().get(request.execution().actorId()).currentSurface()
                        .equals(request.observedPosition().supportingSurface()))
                    throw new IllegalArgumentException("hive return requires its observed rejoin before COLD");
                return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
            };
        }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { require(state.hiveColony().mobilizations(), id); }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var parent = require(state.hiveColony().mobilizations(), id);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, parent.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) { throw new IllegalArgumentException("hive owns the coordinated survivor return"); }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) { throw new IllegalArgumentException("hive return has no suspended continuation"); }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { throw new IllegalArgumentException("hive parent retires the returned group"); }
    }; }
}
