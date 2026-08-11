package io.farfrontier.palemirror.domain;

import java.util.Objects;

public record SettlementDevelopmentPolicy(WorldObjectId communityId, String version, int stockPercent,
                                          int minimumDefence, int pressureSteps, int investmentIron,
                                          int growthSteps, int autonomousInvestmentSteps) {
    public static SettlementDevelopmentPolicy defaults(WorldObjectId communityId) {
        return new SettlementDevelopmentPolicy(communityId, "settlement-development-v2", 75, 50, 6, 24, 8, 24);
    }
    public SettlementDevelopmentPolicy(WorldObjectId communityId, String version, int stockPercent,
                                       int minimumDefence, int pressureSteps, int investmentIron, int growthSteps) {
        this(communityId, version, stockPercent, minimumDefence, pressureSteps, investmentIron, growthSteps, 24);
    }
    public SettlementDevelopmentPolicy {
        Objects.requireNonNull(communityId, "communityId");
        if (version == null || version.isBlank() || stockPercent < 0 || stockPercent > 100
                || minimumDefence < 0 || minimumDefence > 100 || pressureSteps < 1
                || investmentIron < 0 || growthSteps < 1 || autonomousInvestmentSteps < 1) {
            throw new IllegalArgumentException("Invalid development policy");
        }
    }
}
