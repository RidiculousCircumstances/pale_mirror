package io.farfrontier.palemirror.visuals.genesis;

/** Terrain requirements for the community footprint, independent of its authored layout grammar. */
public record SettlementTerrainPolicy(
        LandscapeAffinity landscapeAffinity,
        int surveyRadius,
        boolean requireDryFootprint,
        int preferredMaximumRelief,
        int acceptableMaximumRelief,
        int minimumLandscapeScore) {
    public SettlementTerrainPolicy {
        if (landscapeAffinity == null) throw new IllegalArgumentException("landscapeAffinity is required");
        if (surveyRadius < 1) throw new IllegalArgumentException("surveyRadius must be positive");
        if (preferredMaximumRelief < 0) throw new IllegalArgumentException("preferred relief must be non-negative");
        if (acceptableMaximumRelief < preferredMaximumRelief) {
            throw new IllegalArgumentException("acceptable relief must include preferred relief");
        }
        if (minimumLandscapeScore < 0) throw new IllegalArgumentException("landscape score must be non-negative");
    }

    public boolean acceptsBiome(FrontierSiteSelector.BiomeSample biome) {
        return biome.suitable() && (!requireDryFootprint || !biome.water());
    }

    public boolean preferred(TerrainCandidate candidate) {
        return (!requireDryFootprint || candidate.waterSamples() == 0)
                && candidate.relief() <= preferredMaximumRelief;
    }

    public boolean acceptable(TerrainCandidate candidate) {
        return (!requireDryFootprint || candidate.waterSamples() == 0)
                && candidate.relief() <= acceptableMaximumRelief;
    }

    public enum LandscapeAffinity {
        /** A buildable shelf with multiple mountain masses in the configured site-distance bands. */
        MOUNTAIN_FOOTHILL
    }
}
