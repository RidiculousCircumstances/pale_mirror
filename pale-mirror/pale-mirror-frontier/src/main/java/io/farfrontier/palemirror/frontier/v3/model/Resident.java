package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One canonical person, later represented by exactly one managed Villager while HOT. */
public record Resident(SubjectId id, SubjectId settlementId, ResidentRole role, BlockPosition home) {
    public Resident {
        Objects.requireNonNull(id, "resident id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(role, "resident role");
        Objects.requireNonNull(home, "resident home");
    }
}
