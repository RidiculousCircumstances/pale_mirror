package io.farfrontier.palemirror.api;

import java.util.Objects;

public record VisualModulePlacement(String templateId, String role, VisualPoint origin, int quarterTurns,
                                    VisualBounds footprint) {
    public VisualModulePlacement {
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId is required");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(footprint, "footprint");
        quarterTurns = Math.floorMod(quarterTurns, 4);
    }
}
