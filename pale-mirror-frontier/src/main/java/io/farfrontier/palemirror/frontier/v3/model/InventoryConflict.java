package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Bounded durable physical-delta record; it reports drift but never restores a player's world. */
public record InventoryConflict(SubjectId id, SubjectId subjectId, SubjectId containerId, int slot, InventoryConflictKind kind) {
    public InventoryConflict {
        Objects.requireNonNull(id, "inventory conflict id"); Objects.requireNonNull(subjectId, "inventory conflict subject");
        Objects.requireNonNull(containerId, "inventory conflict container"); Objects.requireNonNull(kind, "inventory conflict kind");
        if (slot < 0 || slot > 53) throw new IllegalArgumentException("inventory conflict slot must be 0..53");
    }
}
