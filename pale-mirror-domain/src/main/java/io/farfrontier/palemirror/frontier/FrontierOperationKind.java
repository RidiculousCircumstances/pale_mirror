package io.farfrontier.palemirror.frontier;

/** Functional job of a facility; the graybox renderer uses it to make work legible. */
public enum FrontierOperationKind {
    HOUSING, FARMING, MINING, FORESTRY, POWER_GENERATION, CRAFTING, MEDICAL, WAREHOUSING, MARKET, DEFENCE;

    static FrontierOperationKind forFacility(FrontierFacilityKind kind) {
        return switch (kind) {
            case HOUSING -> HOUSING;
            case FARM -> FARMING;
            case MINE -> MINING;
            case FOREST -> FORESTRY;
            case POWER -> POWER_GENERATION;
            case WORKSHOP -> CRAFTING;
            case CLINIC -> MEDICAL;
            case WAREHOUSE -> WAREHOUSING;
            case MARKET -> MARKET;
            case GUARD_POST -> DEFENCE;
        };
    }
}
