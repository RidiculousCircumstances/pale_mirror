package io.farfrontier.palemirror.domain;

import java.util.Objects;

public record SettlementDevelopmentPolicy(WorldObjectId communityId, String version, int stockPercent,
                                          int minimumDefence, int pressureSteps, int investmentIron,
                                          int growthSteps) {
    public static SettlementDevelopmentPolicy defaults(WorldObjectId communityId) {
        return new SettlementDevelopmentPolicy(communityId, "settlement-development-v1", 75, 50, 6, 24, 8);
    }
    public SettlementDevelopmentPolicy {
        Objects.requireNonNull(communityId, "communityId");
        if (version == null || version.isBlank() || stockPercent < 0 || stockPercent > 100
                || minimumDefence < 0 || minimumDefence > 100 || pressureSteps < 1
                || investmentIron < 0 || growthSteps < 1) throw new IllegalArgumentException("Invalid development policy");
    }
}
