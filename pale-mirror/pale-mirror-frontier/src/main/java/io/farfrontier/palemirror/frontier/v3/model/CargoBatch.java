package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Identified in-transit batch. Fungible contents live solely in a {@link ResourceCustody.Cargo} account. */
public record CargoBatch(SubjectId id, SubjectId ownerId, List<SubjectId> itemIds, boolean fungibleContents) {
    public CargoBatch {
        Objects.requireNonNull(id, "cargo id"); Objects.requireNonNull(ownerId, "cargo owner");
        itemIds = List.copyOf(itemIds);
        if (itemIds.stream().distinct().count() != itemIds.size() || fungibleContents != itemIds.isEmpty()) {
            throw new IllegalArgumentException("cargo must retain either exact items or one fungible custody account");
        }
    }
    public CargoBatch(SubjectId id, SubjectId ownerId, List<SubjectId> itemIds) { this(id, ownerId, itemIds, false); }
    public static CargoBatch fungible(SubjectId id, SubjectId ownerId) { return new CargoBatch(id, ownerId, List.of(), true); }
}
