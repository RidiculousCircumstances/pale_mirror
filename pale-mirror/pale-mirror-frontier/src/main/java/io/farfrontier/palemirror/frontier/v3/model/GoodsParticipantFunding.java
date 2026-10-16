package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import java.util.List;

/** Economic policy translates its supply advice into spending priority, never new money or holds. */
public final class GoodsParticipantFunding {
    private GoodsParticipantFunding() { }
    public static FixedScalar purchase(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        return ProcurementBudget.forPurchase(view.availableMoney(), needs(view, rules), item);
    }
    public static boolean fundsRestart(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        return ProcurementBudget.fundsRestart(view.availableMoney(), needs(view, rules), item);
    }
    public static FixedScalar optional(FrontierWorldState state, SubjectId sponsor) {
        var participant = state.companies().goodsTrade().participants().participants().get(sponsor);
        if (participant == null) return state.inventory().economics().availableToReserve(sponsor);
        var view = GoodsParticipantView.read(state, participant);
        return ProcurementBudget.forOptionalSpending(view.availableMoney(), needs(view, state.bootstrap().ruleset().goodsTrade()));
    }
    private static List<ProcurementBudget.Need> needs(GoodsParticipantView view, GoodsTradeRules rules) {
        return view.supplyPlan().map(plan -> rules.policies().get(view.participant().policy()).stream().map(commodity -> {
            var demand = plan.require(commodity.itemKind());
            var stock = view.stocks().get(commodity.itemKind());
            long restart = Math.min(demand.purchase(), Math.max(0,
                    demand.protectedInput() - stock.unclaimedInStorage() - (long) stock.expectedIncoming()));
            return new ProcurementBudget.Need(commodity.itemKind(), demand.purchase(),
                    GoodsParticipantPricing.bid(view.participant(), commodity), demand.deadlineTicks(), restart);
        }).toList()).orElse(List.of());
    }
}
