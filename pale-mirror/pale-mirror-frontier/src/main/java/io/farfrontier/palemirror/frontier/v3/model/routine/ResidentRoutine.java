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
    long startedAt,
    long phaseAt,
    long pauseTicks,
    long eligibleAt) {
  public enum Kind {
    STROLL(1),
    LOOK_AROUND(2),
    GREET(3),
    STRETCH(4);
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
    CLOSED(3);
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
        || phase == Phase.WALKING && kind != Kind.STROLL)
      throw new IllegalArgumentException("invalid declared resident routine");
  }

  public ResidentRoutine arrived(long tick) {
    if (phase != Phase.WALKING) throw new IllegalArgumentException("routine is not walking");
    return new ResidentRoutine(
        actor,
        home,
        sequence,
        kind,
        Phase.PAUSING,
        execution,
        goal,
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
        startedAt,
        phaseAt,
        pauseTicks,
        eligibleAt);
  }
}
