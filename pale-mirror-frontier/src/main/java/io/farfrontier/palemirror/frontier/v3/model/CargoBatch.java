package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Identified in-transit batch. Its item IDs are exact, not a duplicated quantity summary. */
public record CargoBatch(SubjectId id, SubjectId ownerId, List<SubjectId> itemIds) {
    public CargoBatch {
        Objects.requireNonNull(id, "cargo id"); Objects.requireNonNull(ownerId, "cargo owner");
        itemIds = List.copyOf(itemIds);
        if (itemIds.isEmpty() || itemIds.stream().distinct().count() != itemIds.size()) throw new IllegalArgumentException("cargo must own a non-empty unique item list");
    }
}
