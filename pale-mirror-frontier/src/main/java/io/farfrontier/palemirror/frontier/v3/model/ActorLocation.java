package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact canonical state of a resident or bioform, independent of HOT materialization. */
public record ActorLocation(BlockPosition position, ActorCondition condition) {
    public ActorLocation {
        Objects.requireNonNull(position, "actor position");
        Objects.requireNonNull(condition, "actor condition");
    }

    public ActorLocation(BlockPosition position) { this(position, ActorCondition.HEALTHY); }

    public ActorLocation withPosition(BlockPosition nextPosition) { return new ActorLocation(nextPosition, condition); }

    public ActorLocation deadAt(BlockPosition nextPosition) { return new ActorLocation(nextPosition, ActorCondition.dead()); }
}
