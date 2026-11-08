package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;

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
    var use = s.humanPopulation().seats().uses().get(e.actorId());
    if (use != null && use.execution().equals(e) && use.pendingBody().isPresent())
      throw new IllegalArgumentException("routine retains an unresolved native seat effect");
  }

  public ActorActivityBodyCheckpoint bodyCheckpoint() {
    return request -> {
      validateAmbientRelease(request.expectedState(), request.execution());
      return new ActorMovementBodyCheckpoint().acknowledge(request);
    };
  }

  public ActorActivityCheckpoint checkpoint(FrontierWorldState s, ActorExecutionId e) {
    validateReference(s, e);
    var use = s.humanPopulation().seats().uses().get(e.actorId());
    return new ActorActivityCheckpoint(
        s,
        e,
        s.actorMovements().containsKey(e.actorId())
                || ActorInventoryInteractionFences.pending(s, e.actorId())
                || use != null && use.execution().equals(e) && (use.pendingBody().isPresent() || use.phase() == SeatUse.Phase.SEATED)
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
                FrontierWorldStateUpdate.begin().routines(s.routines().with(row.closed()))
                    .humanPopulation(row.seat().isPresent() ? s.humanPopulation().withSeats(s.humanPopulation().seats().without(row.actor())) : s.humanPopulation())));
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
              FrontierWorldStateUpdate.begin().routines(s.routines().without(e.actorId()))
                  .humanPopulation(s.humanPopulation().withSeats(SeatContacts.atFatality(s.humanPopulation().seats(), e)));
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

  public void validateSeatReference(FrontierWorldState s, ActorExecutionId e, SeatUse use) {
    if (s.actorLocations().get(e.actorId()).condition().status() != ActorLifeStatus.ALIVE) {
      if (e.activityKind() != kind() || !e.activityOwnerId().equals(e.actorId()) || !use.execution().equals(e)
          || !s.humanPopulation().resident(e.actorId()).settlementId().equals(use.place().home())
          || !use.pendingMode().equals(Optional.of(SeatUse.ExecutionMode.LOCAL)))
        throw new IllegalArgumentException("dead routine seat lost its declared local owner");
      ActorBodyAuthority.requireRetiredDeath(s, use.pendingBody().orElseThrow().body());
      return;
    }
    var row = ResidentRoutines.require(s, e);
    if (row.kind() != ResidentRoutine.Kind.SIT || !use.execution().equals(e)
        || !row.seat().equals(Optional.of(use.place().id())) || !row.home().equals(use.place().home())
        || !SeatContacts.present(s, use.place()))
      throw new IllegalArgumentException("routine seat lost its exact declared purpose/place");
    var building = FrontierWorldStateSupport.settlement(s.bootstrap(), row.home()).structures().stream()
        .filter(b -> b.id().equals(use.place().id().buildingId())).findFirst().orElseThrow();
    if (building.definition().zones().get(building.definition().seats().get(use.place().id().localId()).zoneId()).use()
        != io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.ZoneUse.REST)
      throw new IllegalArgumentException("routine cannot occupy an undeclared rest zone");
  }
  public boolean permitsSeat(FrontierWorldState s, ActorExecutionId e, SeatUse use) {
    validateSeatReference(s, e, use);
    return !s.combat().evasions().containsKey(e.actorId());
  }
  public boolean permitsSeatRelease(FrontierWorldState s, ActorExecutionId e, SeatUse use) {
    validateSeatReference(s, e, use);
    return use.phase() == SeatUse.Phase.RESERVED && use.pendingBody().isEmpty();
  }
  public Optional<SeatUse.Action> seatAction(FrontierWorldState s, ActorExecutionId e, SeatUse use, long tick) {
    validateSeatReference(s, e, use);
    var row = ResidentRoutines.require(s, e);
    boolean done = ResidentRoutines.finishing(s, row, tick);
    if (use.phase() == SeatUse.Phase.SEATED)
      return Optional.of(done || !s.actorLocations().get(e.actorId()).supportingSurface().equals(use.place().contact())
          ? SeatUse.Action.EXIT : SeatUse.Action.RESTORE);
    return row.phase() == ResidentRoutine.Phase.SEATING && !done && permitsSeat(s, e, use)
        ? Optional.of(SeatUse.Action.ENTER) : Optional.empty();
  }

  public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(
      FrontierWorldState s, ActorExecutionId e) {
    var row = ResidentRoutines.require(s, e);
    return Optional.of(
        new ActorActivityAmbientTarget(
            AmbientGoalKind.PATROL,
            row.seat().isPresent() && ResidentRoutines.use(s, row).phase() == SeatUse.Phase.SEATED
                ? ResidentRoutines.use(s, row).place().contact()
                : row.phase() == ResidentRoutine.Phase.PAUSING ? row.goal()
                : s.actorLocations().get(e.actorId()).supportingSurface()));
  }

  public boolean permitsAmbientMotion(
      FrontierWorldState s, ActorExecutionId e, AmbientActorLease lease) {
    var row = ResidentRoutines.require(s, e);
    if (row.seat().isPresent() && (ResidentRoutines.use(s, row).pendingBody().isPresent() || ResidentRoutines.use(s, row).phase() == SeatUse.Phase.SEATED)) return false;
    return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT
            && navigationOrder(s, e)
                .filter(o -> o.legalStations().contains(lease.goalBody().supportingSurface()))
                .isPresent()
        || row.phase() == ResidentRoutine.Phase.PAUSING
            && lease.goal() == AmbientGoalKind.PATROL
            && lease.goalBody().supportingSurface().equals(row.goal());
  }
}
