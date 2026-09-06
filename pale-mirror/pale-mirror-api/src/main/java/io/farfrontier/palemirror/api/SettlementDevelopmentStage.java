package io.farfrontier.palemirror.api;

/** Authored composition snapshots. Runtime stage transitions are intentionally outside schema v40. */
public enum SettlementDevelopmentStage {
    PROSPECTING_POST(12),
    MINING_CAMP(24),
    TOWNSHIP(48),
    MINING_TOWN(72);

    private final int population;

    SettlementDevelopmentStage(int population) { this.population = population; }

    public int population() { return population; }
}
