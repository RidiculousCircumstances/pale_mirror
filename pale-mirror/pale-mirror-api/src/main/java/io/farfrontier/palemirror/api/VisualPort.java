package io.farfrontier.palemirror.api;

import java.util.Objects;

public record VisualPort(String id, VisualPortKind kind, VisualPoint position, int outwardQuarterTurns) {
    public VisualPort {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        outwardQuarterTurns = Math.floorMod(outwardQuarterTurns, 4);
    }
}
