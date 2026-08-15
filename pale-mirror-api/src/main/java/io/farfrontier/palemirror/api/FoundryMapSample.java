package io.farfrontier.palemirror.api;

/** One plan-owned surface column used by deterministic Foundry maps. */
public record FoundryMapSample(int x, int z, int targetY, Integer observedY, Integer expectedTopY,
                               String ownerId, boolean loaded) {
    public FoundryMapSample {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        if (!loaded && observedY != null) throw new IllegalArgumentException("unloaded sample cannot be observed");
    }
}
