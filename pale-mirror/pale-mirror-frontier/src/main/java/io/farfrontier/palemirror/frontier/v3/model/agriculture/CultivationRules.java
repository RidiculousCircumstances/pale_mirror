package io.farfrontier.palemirror.frontier.v3.model.agriculture;

/** Economic adjustment cadence and graybox development limits, not crop physics. */
public record CultivationRules(int stableDemandDays, int areaDeadbandPermille, int maximumNewCells,
                              int searchRadius, int maximumFields, int maximumCells) {
    public CultivationRules {
        if (stableDemandDays < 1 || areaDeadbandPermille < 0 || areaDeadbandPermille > 1000
                || maximumNewCells < 1 || searchRadius < 1 || searchRadius > 128
                || maximumFields < 1 || maximumFields > 16 || maximumCells < maximumNewCells || maximumCells > 4096)
            throw new IllegalArgumentException("invalid cultivation development policy");
    }
    public static CultivationRules graybox() { return new CultivationRules(2, 100, 64, 64, 4, 1024); }
    public String canonicalText() {
        return stableDemandDays + ":" + areaDeadbandPermille + ":" + maximumNewCells + ":" + searchRadius
                + ":" + maximumFields + ":" + maximumCells;
    }
}
