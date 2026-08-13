package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Local terrain edit; settlement genesis never grades an entire enclosing shape. */
public record SettlementFoundationPlan(String id, VisualBounds footprint, int targetY, int apron,
                                       int maximumCut, int maximumFill, String surface) {
    public SettlementFoundationPlan {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        Objects.requireNonNull(footprint, "footprint");
        if (apron < 0 || maximumCut < 0 || maximumFill < 0) {
            throw new IllegalArgumentException("foundation limits must be non-negative");
        }
        if (surface == null || surface.isBlank()) throw new IllegalArgumentException("surface is required");
    }
}
