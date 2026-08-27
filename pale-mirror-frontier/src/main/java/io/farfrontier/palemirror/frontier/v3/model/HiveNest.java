package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One seed nest in a distributed hive; it never owns an independent economy. */
public record HiveNest(SubjectId id, SubjectId hiveId, BlockPosition anchor) {
    public HiveNest {
        Objects.requireNonNull(id, "nest id");
        Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(anchor, "nest anchor");
    }
}
