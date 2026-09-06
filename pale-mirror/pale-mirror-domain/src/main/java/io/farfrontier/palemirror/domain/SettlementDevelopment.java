package io.farfrontier.palemirror.domain;

import java.util.Objects;

public final class SettlementDevelopment {
    private final WorldObjectId communityId;
    private int prosperity;
    private int developmentPressure;
    private int housingCapacity;
    private int labourCapacity;
    private int stableGrowthSteps;

    public SettlementDevelopment(WorldObjectId communityId, int prosperity, int developmentPressure,
                                 int housingCapacity, int labourCapacity, int stableGrowthSteps) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        if (prosperity < 0 || prosperity > 100 || developmentPressure < 0 || housingCapacity < 0
                || labourCapacity < 0 || stableGrowthSteps < 0) throw new IllegalArgumentException("Invalid settlement development");
        this.prosperity = prosperity;
        this.developmentPressure = developmentPressure;
        this.housingCapacity = housingCapacity;
        this.labourCapacity = labourCapacity;
        this.stableGrowthSteps = stableGrowthSteps;
    }
    public WorldObjectId communityId() { return communityId; }
    public int prosperity() { return prosperity; }
    public int developmentPressure() { return developmentPressure; }
    public int housingCapacity() { return housingCapacity; }
    public int labourCapacity() { return labourCapacity; }
    public int stableGrowthSteps() { return stableGrowthSteps; }
    void qualify() { developmentPressure++; }
    void decay() { developmentPressure = Math.max(0, developmentPressure - 1); stableGrowthSteps = 0; }
    void resetPressure() { developmentPressure = 0; }
    void advanceGrowth() { stableGrowthSteps++; }
    void resetGrowth() { stableGrowthSteps = 0; }
    void storehouseCompleted() {
        housingCapacity += 8;
        prosperity = Math.min(100, prosperity + 10);
        developmentPressure = 0;
        stableGrowthSteps = 0;
    }
}
