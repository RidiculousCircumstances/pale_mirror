package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** Terrain-validation policy for a physical route between two typed regional endpoints. */
public record RoutePlacementRequirement(
        String originRole,
        String destinationRole,
        int intermediateControlCount,
        List<Integer> lateralControlOffsets,
        boolean rejectWater,
        int horizontalBlocksPerVerticalBlock) {
    public RoutePlacementRequirement {
        if (originRole == null || originRole.isBlank() || destinationRole == null || destinationRole.isBlank()) {
            throw new IllegalArgumentException("route endpoint roles are required");
        }
        if (originRole.equals(destinationRole)) throw new IllegalArgumentException("route endpoints must differ");
        if (intermediateControlCount < 0) throw new IllegalArgumentException("control count must be non-negative");
        lateralControlOffsets = List.copyOf(lateralControlOffsets);
        if (lateralControlOffsets.isEmpty()) throw new IllegalArgumentException("route control offsets are required");
        if (horizontalBlocksPerVerticalBlock < 1) throw new IllegalArgumentException("route grade must be positive");
    }
}
