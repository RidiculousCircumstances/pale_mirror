package io.farfrontier.palemirror.frontier.v3.model.routine;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/**
 * One optional activity or its finite per-person cooldown. No inventory, body or social outcome.
 */
public record ResidentRoutine(
    SubjectId actor,
    SubjectId home,
    long sequence,
    Kind kind,
    Phase phase,
    Optional<ActorExecutionId> execution,
    SurfaceAnchor goal,
    Optional<io.farfrontier.palemirror.frontier.v3.model.seating.SeatPlace.Id> seat,
    long startedAt,
    long phaseAt,
    long pauseTicks,
    long eligibleAt) {
  public enum Kind {
    STROLL(1),
    LOOK_AROUND(2),
    GREET(3),
    STRETCH(4),
    SIT(5);
    private final int tag;

    Kind(int tag) {
      this.tag = tag;
    }

    public int wireTag() {
      return tag;
    }

    public static Kind fromWireTag(int tag) {
      for (var v : values()) if (v.tag == tag) return v;
      throw new IllegalArgumentException("unknown routine kind");
    }
  }

  public enum Phase {
    WALKING(1),
    PAUSING(2),
    CLOSED(3),
    SEATING(4);
    private final int tag;

    Phase(int tag) {
      this.tag = tag;
    }

    public int wireTag() {
      return tag;
    }

    public static Phase fromWireTag(int tag) {
      for (var v : values()) if (v.tag == tag) return v;
      throw new IllegalArgumentException("unknown routine phase");
    }
  }

  public ResidentRoutine {
    Objects.requireNonNull(actor);
    Objects.requireNonNull(home);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(phase);
    Objects.requireNonNull(execution);
    Objects.requireNonNull(goal);
    Objects.requireNonNull(seat);
    if (sequence < 1
        || startedAt < 0
        || phaseAt < startedAt
        || eligibleAt <= startedAt
        || pauseTicks < 20
        || pauseTicks > 1200
        || execution.isPresent() != (phase != Phase.CLOSED)
        || execution
            .filter(
                e ->
                    !e.actorId().equals(actor)
                        || !e.activityOwnerId().equals(actor)
                        || e.activityKind() != ActorActivityKind.LEISURE)
            .isPresent()
        || phase == Phase.WALKING && kind != Kind.STROLL && kind != Kind.SIT
        || seat.isPresent() != (kind == Kind.SIT)
        || phase == Phase.SEATING && kind != Kind.SIT)
      throw new IllegalArgumentException("invalid declared resident routine");
  }

  public ResidentRoutine(SubjectId actor, SubjectId home, long sequence, Kind kind, Phase phase,
      Optional<ActorExecutionId> execution, SurfaceAnchor goal, long startedAt, long phaseAt,
      long pauseTicks, long eligibleAt) {
    this(actor, home, sequence, kind, phase, execution, goal, Optional.empty(), startedAt, phaseAt, pauseTicks, eligibleAt);
  }

  public ResidentRoutine seated(long tick) {
    if (phase != Phase.SEATING) throw new IllegalArgumentException("routine is not awaiting its seat receipt");
    return new ResidentRoutine(actor, home, sequence, kind, Phase.PAUSING, execution, goal, seat,
        startedAt, tick, pauseTicks, eligibleAt);
  }

  public ResidentRoutine arrived(long tick) {
    if (phase != Phase.WALKING) throw new IllegalArgumentException("routine is not walking");
    return new ResidentRoutine(
        actor,
        home,
        sequence,
        kind,
        kind == Kind.SIT ? Phase.SEATING : Phase.PAUSING,
        execution,
        goal,
        seat,
        startedAt,
        tick,
        pauseTicks,
        eligibleAt);
  }

  public ResidentRoutine closed() {
    return new ResidentRoutine(
        actor,
        home,
        sequence,
        kind,
        Phase.CLOSED,
        Optional.empty(),
        goal,
        seat,
        startedAt,
        phaseAt,
        pauseTicks,
        eligibleAt);
  }
}
