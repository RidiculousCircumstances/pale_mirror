package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;

/**
 * Optional purpose yields only at the shared movement/effect checkpoint. No suspended work or
 * resources.
 */
final class RoutineActivityCapability implements ActorActivityCapability {
  public ActorActivityKind kind() {
    return ActorActivityKind.LEISURE;
  }

  public Interruption interruption() {
    return Interruption.RELEASE;
  }

  public void validateReference(FrontierWorldState s, ActorExecutionId e) {
    ResidentRoutines.require(s, e);
  }

  public void validateAmbientRelease(FrontierWorldState s, ActorExecutionId e) {
    validateReference(s, e);
  }

  public ActorActivityBodyCheckpoint bodyCheckpoint() {
    return new ActorMovementBodyCheckpoint();
  }

  public ActorActivityCheckpoint checkpoint(FrontierWorldState s, ActorExecutionId e) {
    validateReference(s, e);
    return new ActorActivityCheckpoint(
        s,
        e,
        s.actorMovements().containsKey(e.actorId())
            ? Optional.of(
                new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, e.activityOwnerId()))
            : Optional.empty());
  }

  public FrontierWorldState release(FrontierWorldState s, ActorExecutionId e) {
    if (!checkpoint(s, e).ready())
      throw new IllegalArgumentException("routine retains its common movement");
    var row = ResidentRoutines.require(s, e);
    // The common lifecycle publishes this owner's release and successor authority together.
    return FrontierWorldState.duringUnpublishedTransition(
        () ->
            s.withChanges(
                FrontierWorldStateUpdate.begin().routines(s.routines().with(row.closed()))));
  }

  public FrontierWorldState pause(FrontierWorldState s, ActorExecutionId e, long tick) {
    throw new IllegalArgumentException("optional routine has no suspended continuation");
  }

  public FrontierWorldState resume(FrontierWorldState s, ActorExecutionId e, long tick) {
    throw new IllegalArgumentException("optional routine has no suspended continuation");
  }

  public Optional<ActorActivityDeath> deathAcknowledgement() {
    return Optional.of(
        (s, e, tick) -> {
          validateReference(s, e);
          var movement = s.actorMovements().get(e.actorId());
          var changes =
              FrontierWorldStateUpdate.begin().routines(s.routines().without(e.actorId()));
          if (movement != null && movement.executionId().equals(e))
            changes.actorMovements(
                ImmutableLookupMap.withoutEntry(s.actorMovements(), e.actorId()));
          return new ActorActivityDeath.Acknowledgement(
              s, e, changes, ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
        });
  }

  public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace> navigationPace(
      FrontierWorldState s, ActorExecutionId e) {
    validateReference(s, e);
    return Optional.of(
        ResidentWalkingPace.read(
            s,
            e.actorId(),
            io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK));
  }

  public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder>
      navigationOrder(FrontierWorldState s, ActorExecutionId e) {
    validateReference(s, e);
    return Optional.ofNullable(s.actorMovements().get(e.actorId()))
        .filter(m -> m.executionId().equals(e))
        .map(m -> m.order());
  }

  public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(
      FrontierWorldState s, ActorExecutionId e) {
    var row = ResidentRoutines.require(s, e);
    return Optional.of(
        new ActorActivityAmbientTarget(
            AmbientGoalKind.PATROL,
            row.phase() == ResidentRoutine.Phase.PAUSING
                ? row.goal()
                : s.actorLocations().get(e.actorId()).supportingSurface()));
  }

  public boolean permitsAmbientMotion(
      FrontierWorldState s, ActorExecutionId e, AmbientActorLease lease) {
    var row = ResidentRoutines.require(s, e);
    return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT
            && navigationOrder(s, e)
                .filter(o -> o.legalStations().contains(lease.goalBody().supportingSurface()))
                .isPresent()
        || row.phase() == ResidentRoutine.Phase.PAUSING
            && lease.goal() == AmbientGoalKind.PATROL
            && lease.goalBody().supportingSurface().equals(row.goal());
  }
}
