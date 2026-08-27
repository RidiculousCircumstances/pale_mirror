package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact canonical block position of a resident or bioform, independent of HOT materialization. */
public record ActorLocation(BlockPosition position) {
    public ActorLocation { Objects.requireNonNull(position, "actor position"); }
}
