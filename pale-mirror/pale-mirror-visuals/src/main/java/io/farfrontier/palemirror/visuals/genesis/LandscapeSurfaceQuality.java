package io.farfrontier.palemirror.visuals.genesis;

/** Deterministic measurements of local variation after removing broad terrain slope. */
public record LandscapeSurfaceQuality(
        int maximumLocalGrade,
        int maximumRoughness,
        int depressionDepth,
        int buildablePercent,
        int cliffEdges) {
    public LandscapeSurfaceQuality {
        if (maximumLocalGrade < 0 || maximumRoughness < 0 || depressionDepth < 0
                || buildablePercent < 0 || buildablePercent > 100 || cliffEdges < 0) {
            throw new IllegalArgumentException("landscape surface quality is invalid");
        }
    }

    public static LandscapeSurfaceQuality pristine() {
        return new LandscapeSurfaceQuality(0, 0, 0, 100, 0);
    }
}
