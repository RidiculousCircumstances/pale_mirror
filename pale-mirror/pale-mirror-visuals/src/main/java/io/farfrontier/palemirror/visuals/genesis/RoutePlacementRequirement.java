package io.farfrontier.palemirror.visuals.genesis;

/** Terrain-validation policy for a physical route between two typed regional endpoints. */
public record RoutePlacementRequirement(
        String originRole,
        String destinationRole,
        boolean preferDryLand,
        int horizontalBlocksPerVerticalBlock,
        int searchCorridorHalfWidth,
        int searchBudget,
        int maximumWaterSpan,
        int bridgeClearance,
        int waterPenalty) {
    public RoutePlacementRequirement {
        if (originRole == null || originRole.isBlank() || destinationRole == null || destinationRole.isBlank()) {
            throw new IllegalArgumentException("route endpoint roles are required");
        }
        if (originRole.equals(destinationRole)) throw new IllegalArgumentException("route endpoints must differ");
        if (horizontalBlocksPerVerticalBlock < 1) throw new IllegalArgumentException("route grade must be positive");
        if (searchCorridorHalfWidth < 4 || searchBudget < 1 || maximumWaterSpan < 0
                || bridgeClearance < 1 || waterPenalty < 1) {
            throw new IllegalArgumentException("route search and bridge limits are invalid");
        }
    }
}
