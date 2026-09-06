package io.farfrontier.palemirror.domain;

public enum ScenarioArchetype {
    INVESTIGATION_RECOVERY,
    SETTLEMENT_SUPPLY_CRISIS,
    DEVELOPMENT_OPPORTUNITY,
    RESETTLEMENT_OPPORTUNITY;

    public static ScenarioArchetype parse(String value) {
        return switch (value) {
            case "investigation_recovery" -> INVESTIGATION_RECOVERY;
            case "settlement_supply_crisis" -> SETTLEMENT_SUPPLY_CRISIS;
            case "development_opportunity" -> DEVELOPMENT_OPPORTUNITY;
            case "resettlement_opportunity" -> RESETTLEMENT_OPPORTUNITY;
            default -> throw new IllegalArgumentException("Unknown scenario archetype " + value);
        };
    }
}
