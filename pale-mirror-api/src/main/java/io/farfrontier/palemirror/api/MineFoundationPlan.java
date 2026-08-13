package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Immutable local terrain contract for one surface MineSite module. */
public record MineFoundationPlan(String id, VisualBounds footprint, int targetY, int apron,
                                 int maximumCut, int maximumFill) {
    public MineFoundationPlan {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("foundation id is required");
        Objects.requireNonNull(footprint, "footprint");
        if (apron < 0 || maximumCut < 0 || maximumFill < 0) {
            throw new IllegalArgumentException("foundation terrain limits must be non-negative");
        }
        if (footprint.min().y() != targetY || footprint.max().y() != targetY) {
            throw new IllegalArgumentException("foundation footprint must be a horizontal target-Y plane");
        }
    }
}
