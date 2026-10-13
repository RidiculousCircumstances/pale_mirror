package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Common read-only reserve policy; domain owners supply food/fuel requirements. */
public final class PublicStockProtection {
    public static int minimum(FrontierWorldState state, SubjectId home, String itemKind) {
        return Math.max(itemKind.equals(SettlementFoodPolicy.BREAD) ? SettlementFoodPolicy.reserveRequirement(state, home) : 0,
                ProductionFuelPolicy.reserveRequirement(state, home, itemKind));
    }
    private PublicStockProtection() { }
}
