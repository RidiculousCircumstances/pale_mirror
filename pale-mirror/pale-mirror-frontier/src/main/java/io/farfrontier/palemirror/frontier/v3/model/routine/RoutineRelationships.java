package io.farfrontier.palemirror.frontier.v3.model.routine;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import java.util.*;

/** Sparse row/performer/home/execution closure, including inverse execution references. */
public final class RoutineRelationships {
  private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorLocation>
      ACTORS = new ImmutableReferenceIndex.Source<>(FrontierWorldState::actorLocations);
  private static final ImmutableReferenceIndex.Source<
          FrontierWorldState, SubjectId, ResidentProfile>
      PEOPLE = new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().residents());
  private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorExecution>
      EXECUTIONS = new ImmutableReferenceIndex.Source<>(s -> s.actorExecutions().actors());
  private static final ImmutableReferenceIndex.Source<
          FrontierWorldState, SubjectId, ResidentRoutine>
      ROWS = new ImmutableReferenceIndex.Source<>(s -> s.routines().rows());
  private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorMovement>
      MOVEMENTS = new ImmutableReferenceIndex.Source<>(FrontierWorldState::actorMovements);
  private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse> SEATS = new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().seats().uses());
  private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, ResidentRoutine>
      CHECK =
          new ImmutableReferenceIndex<>(
              SimulationCapacity.MAX_RESIDENTS,
              6,
              List.of(ACTORS, PEOPLE, EXECUTIONS, ROWS, MOVEMENTS, SEATS),
              (s, row) ->
                  List.of(
                      ACTORS.at(row.actor()),
                      PEOPLE.at(row.actor()),
                      EXECUTIONS.at(row.actor()),
                      MOVEMENTS.at(row.actor()), SEATS.at(row.actor())),
              (s, key, row) -> {
                var person = s.humanPopulation().resident(key);
                if (!key.equals(row.actor())
                    || person == null
                    || !person.settlementId().equals(row.home()))
                  throw new IllegalArgumentException("routine lost its exact performer/home");
                if (row.phase() != ResidentRoutine.Phase.CLOSED) {
                  s.actorExecutions().requireCurrent(row.execution().orElseThrow());
                  if (s.actorLocations().get(key).condition().status() != ActorLifeStatus.ALIVE)
                    throw new IllegalArgumentException(
                        "active routine cannot retain a dead performer");
                  if (row.seat().isPresent()) ResidentRoutines.use(s, row);
                  var movement = s.actorMovements().get(key);
                  if (row.phase() == ResidentRoutine.Phase.WALKING
                          && (movement == null
                              || !movement.executionId().equals(row.execution().orElseThrow()))
                      || row.phase() != ResidentRoutine.Phase.WALKING
                          && movement != null
                          && movement.executionId().equals(row.execution().orElseThrow()))
                    throw new IllegalArgumentException(
                        "routine phase lost its common movement relationship");
                }
              });
  private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, ActorExecutionId>
      INVERSE =
          new ImmutableReferenceIndex<>(
              SimulationCapacity.MAX_RESIDENTS,
              1,
              List.of(ROWS),
              (s, e) -> List.of(ROWS.at(e.actorId())),
              (s, key, e) -> ResidentRoutines.require(s, e));

  public static void validate(FrontierWorldState s) {
    if (s.routines().active().size()
        > s.bootstrap().ruleset().residentLife().routines().activeLimit())
      throw new IllegalArgumentException(
          "routine active cohort exceeds its declared bounded policy");
    CHECK.validate(s.bootstrap(), s.routines().rows(), s);
    INVERSE.validate(s.bootstrap(), s.actorExecutions().current(ActorActivityKind.LEISURE), s);
  }

  public static void collect(FrontierWorldState s, List<FrontierDomainRelationships.Edge> target) {
    for (var row : s.routines().rows().values()) {
      var owner =
          new FrontierDomainRelationships.SubjectEndpoint(
              FrontierDomainRelationships.EntityKind.RESIDENT_ROUTINE, row.actor());
      var life =
          row.phase() == ResidentRoutine.Phase.CLOSED
              ? FrontierDomainRelationships.Lifecycle.TERMINAL_RETAINED
              : FrontierDomainRelationships.Lifecycle.ACTIVE;
      target.add(
          FrontierDomainRelationships.declaredEdge(
              FrontierDomainRelationships.Kind.ROUTINE_ACTOR,
              owner,
              owner,
              new FrontierDomainRelationships.SubjectEndpoint(
                  FrontierDomainRelationships.EntityKind.RESIDENT, row.actor()),
              life,
              "performer"));
      target.add(
          FrontierDomainRelationships.declaredEdge(
              FrontierDomainRelationships.Kind.ROUTINE_HOME,
              owner,
              owner,
              new FrontierDomainRelationships.SubjectEndpoint(
                  FrontierDomainRelationships.EntityKind.SETTLEMENT, row.home()),
              life,
              "home"));
    }
  }

  private RoutineRelationships() {}
}
