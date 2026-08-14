package io.farfrontier.palemirror.visuals.genesis;

/**
 * Reusable natural-surface contract for an authored settlement footprint.
 * Values describe terrain before PM grading; a layout is allowed to follow a
 * broad slope, but it may not hide a canyon, cliff or heavily broken shelf.
 */
public record LandscapeSuitabilityProfile(
        int sampleStep,
        int buildableHeightTolerance,
        int preferredMaximumLocalGrade,
        int acceptableMaximumLocalGrade,
        int preferredMaximumRoughness,
        int acceptableMaximumRoughness,
        int acceptableMaximumDepressionDepth,
        int preferredMinimumBuildablePercent,
        int acceptableMinimumBuildablePercent,
        int maximumVegetationBurden) {
    public LandscapeSuitabilityProfile {
        if (sampleStep < 8 || sampleStep > 64) {
            throw new IllegalArgumentException("landscape sample step must be between 8 and 64 blocks");
        }
        if (buildableHeightTolerance < 0 || preferredMaximumLocalGrade < 0
                || acceptableMaximumLocalGrade < preferredMaximumLocalGrade
                || preferredMaximumRoughness < 0
                || acceptableMaximumRoughness < preferredMaximumRoughness
                || acceptableMaximumDepressionDepth < 0) {
            throw new IllegalArgumentException("landscape relief limits are inconsistent");
        }
        if (preferredMinimumBuildablePercent < 0 || preferredMinimumBuildablePercent > 100
                || acceptableMinimumBuildablePercent < 0 || acceptableMinimumBuildablePercent > 100
                || acceptableMinimumBuildablePercent > preferredMinimumBuildablePercent) {
            throw new IllegalArgumentException("landscape buildable percentages are inconsistent");
        }
        if (maximumVegetationBurden < 0 || maximumVegetationBurden > 3) {
            throw new IllegalArgumentException("vegetation burden must be between 0 and 3");
        }
    }

    public boolean preferred(LandscapeSurfaceQuality quality) {
        return quality.maximumLocalGrade() <= preferredMaximumLocalGrade
                && quality.maximumRoughness() <= preferredMaximumRoughness
                && quality.depressionDepth() <= acceptableMaximumDepressionDepth
                && quality.buildablePercent() >= preferredMinimumBuildablePercent;
    }

    public boolean acceptable(LandscapeSurfaceQuality quality) {
        return quality.maximumLocalGrade() <= acceptableMaximumLocalGrade
                && quality.maximumRoughness() <= acceptableMaximumRoughness
                && quality.depressionDepth() <= acceptableMaximumDepressionDepth
                && quality.buildablePercent() >= acceptableMinimumBuildablePercent;
    }
}
