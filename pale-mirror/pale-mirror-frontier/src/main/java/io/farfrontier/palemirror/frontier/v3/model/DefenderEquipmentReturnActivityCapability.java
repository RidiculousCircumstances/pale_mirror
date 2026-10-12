package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Bounded service chore may yield outside the access footprint, retaining its exact item and goal. */
final class DefenderEquipmentReturnActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.EQUIPMENT_SERVICE; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        DefenderEquipmentReturnAuthority.require(state, execution);
    }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        var visit = DefenderEquipmentReturnAuthority.require(state, execution);
        if (DefenderEquipmentReturnAuthority.pendingEffect(state, visit))
            throw new IllegalArgumentException("equipment departure retains its unsettled exact physical transfer");
    }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
        return request -> {
            validateAmbientRelease(request.expectedState(), request.execution());
            return new ActorMovementBodyCheckpoint().acknowledge(request);
        };
    }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> {
            var state = request.expectedState();
            var visit = DefenderEquipmentReturnAuthority.require(state, request.suspended());
            var parent = state.strategicPlans().settlementAssaults().get(visit.ownerId());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin().strategicPlans(
                    state.strategicPlans().replaceSettlementAssault(parent.withEquipmentReturnVisit(Optional.of(visit.withExecution(request.successor()))))));
        };
    }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin(), ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
        });
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var visit = DefenderEquipmentReturnAuthority.require(state, execution);
        Optional<ActorActivityCheckpoint.Wait> wait = DefenderEquipmentReturnAuthority.pendingEffect(state, visit)
                ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, visit.ownerId()))
                : visit.access().accessBoundary().occupied(state.actorLocations().get(visit.actorId()).body())
                    ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, visit.ownerId())) : Optional.empty();
        return new ActorActivityCheckpoint(state, execution, wait);
    }
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var visit = DefenderEquipmentReturnAuthority.require(state, execution);
        return new ActorActivityCheckpoint(state, execution, DefenderEquipmentReturnAuthority.pendingEffect(state, visit)
                ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, visit.ownerId())) : Optional.empty());
    }
    @Override public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return Optional.of(new ActorActivityAmbientTarget(AmbientGoalKind.PATROL, state.actorLocations().get(execution.actorId()).supportingSurface()));
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        var visit = DefenderEquipmentReturnAuthority.require(state, execution);
        var movement = state.actorMovements().get(execution.actorId());
        return movement != null ? lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface())
                : lease.goal() == AmbientGoalKind.PATROL && lease.goalBody().supportingSurface().equals(state.actorLocations().get(visit.actorId()).supportingSurface());
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long tick) {
        if (!checkpoint(state, execution).ready() || state.actorMovements().containsKey(execution.actorId()))
            throw new IllegalArgumentException("equipment pause requires its safe common movement checkpoint");
        return state;
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long tick) { validateReference(state, execution); return state; }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(FrontierWorldState state, ActorExecutionId execution, long tick) {
        var visit = DefenderEquipmentReturnAuthority.require(state, execution);
        return java.util.List.of(DefenderEquipmentReturnContinuation.wake(state.strategicPlans().settlementAssaults().get(visit.ownerId()), tick));
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("equipment return retires its exact visit through its owner, not a generic work release");
    }
}
