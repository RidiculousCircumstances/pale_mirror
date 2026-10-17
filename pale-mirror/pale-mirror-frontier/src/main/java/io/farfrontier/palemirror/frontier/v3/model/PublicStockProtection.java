package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Common read-only reserve policy; domain owners supply food/fuel requirements. */
public final class PublicStockProtection {
    public static int minimum(FrontierWorldState state, SubjectId home, String itemKind) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().isPresent()) {
            var participant = state.companies().goodsTrade().participants().participants().get(home);
            var stock = GoodsParticipantView.read(state, participant).stocks().get(itemKind);
            return stock == null ? 0 : stock.protectedMinimum();
        }
        return initialMinimum(state, home, itemKind);
    }
    /** Raw owner facts used while composing the economic view, never a competing sale policy. */
    static int initialMinimum(FrontierWorldState state, SubjectId home, String itemKind) {
        if (SettlementReservePlanning.enabled(state)) {
            var reserve = SettlementReservePlanning.read(state, home).get(itemKind);
            return reserve == null ? 0 : Math.toIntExact(reserve.protectedQuantity());
        }
        return Math.max(itemKind.equals(SettlementFoodPolicy.BREAD) ? SettlementFoodPolicy.reserveRequirement(state, home) : 0,
                ProductionFuelPolicy.reserveRequirement(state, home, itemKind));
    }
    private PublicStockProtection() { }
}
