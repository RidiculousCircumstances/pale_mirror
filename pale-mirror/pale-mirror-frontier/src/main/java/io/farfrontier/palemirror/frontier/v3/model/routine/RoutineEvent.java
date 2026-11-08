package io.farfrontier.palemirror.frontier.v3.model.routine;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Explicit declared routine owner and continuation, never selected from an identifier. */
public sealed interface RoutineEvent extends FrontierPayload {
  SubjectId subject();
  private static void require(ActorExecutionId e,long sequence) {
    Objects.requireNonNull(e);
    if(sequence<1||e.activityKind()!=ActorActivityKind.LEISURE||!e.activityOwnerId().equals(e.actorId()))
      throw new IllegalArgumentException("routine continuation requires its complete nominal resident owner");
  }


  record Requested(ActorActuationId actor, ResidentRoutine.Kind kind, SurfaceAnchor goal)
      implements RoutineEvent {
    public static final String TYPE = "frontier.resident_routine_requested";

    public Requested {
      Objects.requireNonNull(actor);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(goal);
    }

    public SubjectId subject() {
      return actor.body().actorId();
    }

    public String type() {
      return TYPE;
    }
  }

  record Started(ResidentRoutine routine) implements RoutineEvent {
    public static final String TYPE = "frontier.resident_routine_started";
    public Started { Objects.requireNonNull(routine); }

    public SubjectId subject() {
      return routine.actor();
    }

    public String type() {
      return TYPE;
    }
  }

  record Arrived(ActorExecutionId execution, long sequence) implements RoutineEvent {
    public static final String TYPE = "frontier.resident_routine_arrived";
    public Arrived { require(execution,sequence); }

    public SubjectId subject() {
      return execution.actorId();
    }

    public String type() {
      return TYPE;
    }
  }

  record Ended(ActorExecutionId execution, long sequence) implements RoutineEvent {
    public static final String TYPE = "frontier.resident_routine_ended";
    public Ended { require(execution,sequence); }

    public SubjectId subject() {
      return execution.actorId();
    }

    public String type() {
      return TYPE;
    }
  }
}
