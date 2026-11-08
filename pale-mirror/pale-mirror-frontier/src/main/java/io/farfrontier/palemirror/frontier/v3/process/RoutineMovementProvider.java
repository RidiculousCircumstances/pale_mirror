package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;

/** Public optional stroll over the sole common movement owner. */
public final class RoutineMovementProvider implements ActorMovementProvider {
  public ActorMovementContext.Provider key() {
    return ActorMovementContext.Provider.RESIDENT_ROUTINE;
  }

  public static ActorMovement movement(ResidentRoutine row) {
    var order =
        new MovementOrder(
            row.actor(),
            row.actor(),
            1,
            row.sequence(),
            List.of(row.goal()),
            TraversalCapability.PEDESTRIAN,
            MovementOrder.ArrivalPolicy.EXACT_STATION);
    return new ActorMovement(
        order,
        row.startedAt(),
        new ActorMovementContext.RoutineLeg(row.sequence()),
        row.execution().orElseThrow());
  }

  public void validate(FrontierWorldState s, ActorMovement movement) {
    var row = ResidentRoutines.require(s, movement.executionId());
    s.actorExecutions().requireCurrent(movement.executionId());
    if (row.phase() != ResidentRoutine.Phase.WALKING
        || !movement.context().equals(new ActorMovementContext.RoutineLeg(row.sequence()))
        || !movement.order().equals(movement(row).order()))
      throw new IllegalArgumentException("routine movement lost its exact owner/order/sequence");
  }

  public FrontierWorldState start(
      FrontierWorldState s, ActorMovement movement, FrontierWorldStateUpdate update) {
    validate(s, movement);
    return s.withChanges(update);
  }

  public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState s, ActorMovement m) {
    validate(s, m);
    return ResidentRoutines.knowledge(
        s, m.order().actorId(), s.actorLocations().get(m.order().actorId()).supportingSurface());
  }

  public List<SurfaceAnchor> route(FrontierWorldState s, ActorMovement m, SurfaceAnchor start) {
    validate(s, m);
    return ResidentRoutines.knowledge(s, m.order().actorId(), start).plannedPath(start, m.order());
  }

  public void requireRoute(FrontierWorldState s, ActorMovement m, List<SurfaceAnchor> route) {
    validate(s, m);
    ResidentRoutines.knowledge(s, m.order().actorId(), route.getFirst()).requireRoute(route);
    if (!m.order().arrivedAt(route.getLast()))
      throw new IllegalArgumentException("stroll route missed its retained public destination");
  }

  public List<SurfaceAnchor> coldSegment(
      FrontierWorldState s, ActorMovement m, List<SurfaceAnchor> route) {
    validate(s, m);
    return route;
  }

  public ActorExecutionState arrivalAuthority(FrontierWorldState s, ActorMovement m) {
    validate(s, m);
    return s.actorExecutions();
  }

  public Optional<BodyPosition> interruptionCheckpoint(
      FrontierWorldState s, ActorMovement m, long tick) {
    validate(s, m);
    return Optional.of(ActorMovementProcess.bodyAt(s, m.order().actorId(), tick));
  }

  public ActorExecutionState interruptionAuthority(FrontierWorldState s, ActorMovement m) {
    validate(s, m);
    return s.actorExecutions();
  }

  public boolean permitsReplacement(ResidentActivityChoice.Kind kind) {
    return kind == ResidentActivityChoice.Kind.EAT
        || kind == ResidentActivityChoice.Kind.WORK
        || kind == ResidentActivityChoice.Kind.SAFETY
        || kind == ResidentActivityChoice.Kind.CARE;
  }

  public List<ProposedEvent> arrived(FrontierWorldState s, ActorMovement m, long tick) {
    var row = ResidentRoutines.require(s, m.executionId());
    return List.of(
        new ProposedEvent(row.actor(), new RoutineEvent.Arrived(m.executionId(), row.sequence())),
        ResidentRoutineProcess.wake(row, tick + (row.kind() == ResidentRoutine.Kind.SIT ? s.bootstrap().ruleset().residentLife().routines().admissionInterval() : row.pauseTicks())));
  }

  public List<ProposedEvent> interrupted(FrontierWorldState s, ActorMovement m, long tick) {
    return List.of();
  }
}
