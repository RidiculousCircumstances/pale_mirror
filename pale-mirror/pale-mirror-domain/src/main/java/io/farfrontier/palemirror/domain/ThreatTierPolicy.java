package io.farfrontier.palemirror.domain;

/** Immutable deterministic thresholds authored outside the domain and injected by the composition root. */
public record ThreatTierPolicy(long infestedAtActiveSteps, long siegeAtActiveSteps, long apexAtActiveSteps) {
    public static final ThreatTierPolicy DEFAULT = new ThreatTierPolicy(12, 36, 72);

    public ThreatTierPolicy {
        if (infestedAtActiveSteps < 1 || siegeAtActiveSteps <= infestedAtActiveSteps
                || apexAtActiveSteps <= siegeAtActiveSteps) {
            throw new IllegalArgumentException("Threat tier thresholds must be positive and strictly increasing");
        }
    }

    public ThreatTier next(ThreatTier current, long activeSteps) {
        return switch (current) {
            case DORMANT, FOOTHOLD -> activeSteps >= infestedAtActiveSteps ? ThreatTier.INFESTED : current;
            case INFESTED -> activeSteps >= siegeAtActiveSteps ? ThreatTier.SIEGE : current;
            case SIEGE -> activeSteps >= apexAtActiveSteps ? ThreatTier.APEX : current;
            case APEX -> ThreatTier.APEX;
        };
    }
}
