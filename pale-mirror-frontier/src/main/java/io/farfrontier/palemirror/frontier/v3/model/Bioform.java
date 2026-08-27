package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One canonical hive creature, later represented by one managed graybox Zombie while HOT. */
public record Bioform(SubjectId id, SubjectId hiveId, SubjectId nestId, BioformRole role, BlockPosition position) {
    public Bioform {
        Objects.requireNonNull(id, "bioform id");
        Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(nestId, "nest id");
        Objects.requireNonNull(role, "bioform role");
        Objects.requireNonNull(position, "bioform position");
    }
}
