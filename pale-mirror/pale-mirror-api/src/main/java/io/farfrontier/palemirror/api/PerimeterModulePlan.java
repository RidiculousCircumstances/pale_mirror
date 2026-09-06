package io.farfrontier.palemirror.api;

import java.util.Objects;

/** One socket-checked structural module in a continuous authored perimeter. */
public record PerimeterModulePlan(String moduleId, PerimeterModuleKind kind, VisualPoint anchor,
                                  int quarterTurns, int length, VisualBounds footprint) {
    public PerimeterModulePlan {
        if (moduleId == null || moduleId.isBlank()) throw new IllegalArgumentException("moduleId is required");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(footprint, "footprint");
        quarterTurns = Math.floorMod(quarterTurns, 4);
        if (length < 1 || length > 13) throw new IllegalArgumentException("invalid perimeter module length");
    }
}
