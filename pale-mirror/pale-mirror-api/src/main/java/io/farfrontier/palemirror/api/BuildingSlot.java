package io.farfrontier.palemirror.api;

import java.util.Objects;

public record BuildingSlot(String id, BuildingSlotKind kind, VisualPoint position, int capacity) {
    public BuildingSlot {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("building slot id is required");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        if (capacity < 1) throw new IllegalArgumentException("building slot capacity must be positive");
    }
}
