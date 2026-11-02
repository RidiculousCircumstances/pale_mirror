package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Duty owns interruption semantics; shared combat and movement retain their own mechanical state. */
final class GuardActivityCapability implements ActorActivityCapability {
    public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace> navigationPace(
            FrontierWorldState state, ActorExecutionId execution) {
        var duty=GuardAuthority.require(state,execution);
        var mode=io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK;
        if(duty.mission().filter(m->m.stage()==io.farfrontier.palemirror.frontier.v3.model.security.GuardMission.Stage.INTERCEPT).isPresent()){
            var point=state.combat().positions().get(duty.id());var arm=state.combat().armaments().get(duty.id());
            mode=point!=null && point.sector().role()!=io.farfrontier.palemirror.frontier.v3.model.combat.MeleeSector.Role.PRESSURE
                    ?io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.MANEUVER
                    :point!=null && arm!=null && io.farfrontier.palemirror.frontier.v3.model.combat.EvasionDestinations.distance(
                        state.actorLocations().get(execution.actorId()).supportingSurface(),point.targetPosition())<=arm.profile().reach()
                        ?io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.CAREFUL:io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.RUN;
        }
        var strike=state.combat().pending().get(duty.id());
        if(strike!=null && strike.attack()==io.farfrontier.palemirror.frontier.v3.model.combat.MeleeAttack.LUNGE)
            mode=io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.RUN;
        return java.util.Optional.of(ResidentWalkingPace.read(state,execution.actorId(),mode));
    }

    public ActorActivityKind kind() { return ActorActivityKind.GUARD; }
    public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    public ActorActivityBodyCheckpoint bodyCheckpoint() {
        return request -> { validateAmbientRelease(request.expectedState(), request.execution());
            return new ActorMovementBodyCheckpoint().acknowledge(request); };
    }
    public void validateReference(FrontierWorldState state, ActorExecutionId execution) { GuardAuthority.require(state, execution); }
    public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        if (GuardAuthority.pending(state, GuardAuthority.require(state, execution)))
            throw new IllegalArgumentException("guard retains an unsettled physical operation");
    }
    public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var duty = GuardAuthority.require(state, execution);
        return new ActorActivityCheckpoint(state, execution, GuardAuthority.pending(state, duty)
                ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, duty.id()))
                : state.actorMovements().containsKey(execution.actorId()) || duty.mission().flatMap(io.farfrontier.palemirror.frontier.v3.model.security.GuardMission::response).isPresent()
                    ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, duty.id())) : Optional.empty());
    }
    public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var duty = GuardAuthority.require(state, execution);
        return new ActorActivityCheckpoint(state, execution, GuardAuthority.pending(state, duty)
                ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, duty.id())) : Optional.empty());
    }
    public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return Optional.of(new ActorActivityAmbientTarget(AmbientGoalKind.GUARD,
                state.actorLocations().get(execution.actorId()).supportingSurface()));
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder> navigationOrder(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution); var move = state.actorMovements().get(execution.actorId());
        return move != null && move.executionId().equals(execution) ? Optional.of(move.order()) : Optional.empty();
    }
    public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution);
        return lease.goal() == AmbientGoalKind.GUARD
                    && lease.goalBody().supportingSurface().equals(state.actorLocations().get(execution.actorId()).supportingSurface())
                || lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT
                && navigationOrder(state, execution).filter(order -> order.legalStations().contains(lease.goalBody().supportingSurface())).isPresent();
    }
    public ActorActivityResumption resumptionReference() {
        return request -> {
            var state = request.expectedState(); var duty = GuardAuthority.require(state, request.suspended());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin().strategicPlans(
                    state.strategicPlans().withGuard(duty.resumed(request.successor()))));
        };
    }
    public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            var changes = FrontierWorldStateUpdate.begin();
            var movement = state.actorMovements().get(execution.actorId());
            if (movement != null && movement.executionId().equals(execution)) {
                var movements = new LinkedHashMap<>(state.actorMovements());
                movements.remove(execution.actorId());
                changes.actorMovements(movements);
            }
            return new ActorActivityDeath.Acknowledgement(state, execution, changes,
                    ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
        });
    }
    public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(FrontierWorldState state, ActorExecutionId execution, long tick) {
        validateReference(state, execution); return List.of(GuardContinuation.wake(execution.activityOwnerId(), tick));
    }
    public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long tick) {
        if (!checkpoint(state, execution).ready()) throw new IllegalArgumentException("guard cannot pause a prepared physical operation");
        return state;
    }
    public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long tick) { validateReference(state, execution); return state; }
    public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("guard appointment requires its explicit terminal transition");
    }
}
