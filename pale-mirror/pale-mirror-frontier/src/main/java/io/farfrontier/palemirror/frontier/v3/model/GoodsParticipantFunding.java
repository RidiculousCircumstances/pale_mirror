package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import java.util.List;

/** Economic policy translates its supply advice into spending priority, never new money or holds. */
public final class GoodsParticipantFunding {
    private GoodsParticipantFunding() { }
    public static FixedScalar purchase(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        var economy = view.supplyPlan().flatMap(plan -> plan.economy());
        if (economy.isPresent()) return new FixedScalar(Math.min(view.availableMoney().raw(),
                economy.orElseThrow().purchaseBudgets().getOrDefault(item, 0L)));
        var preferred = view.supplyPlan().map(plan -> plan.preferredPurchaseSpending()).orElse(java.util.Map.of());
        if (preferred.isEmpty()) return ProcurementBudget.forPurchase(view.availableMoney(), needs(view, rules), item);
        long other = preferred.entrySet().stream().filter(e -> !e.getKey().equals(item)).mapToLong(java.util.Map.Entry::getValue)
                .reduce(0L, Math::addExact);
        var remainder = new FixedScalar(Math.max(0, view.availableMoney().raw() - other));
        // These are bounded affordable alternatives selected under the shared budget,
        // not reservations. A changed draft state recomputes the advice before matching.
        if (preferred.containsKey(item)) return new FixedScalar(Math.min(remainder.raw(), preferred.get(item)));
        var unselected = needs(view, rules).stream().filter(need -> !preferred.containsKey(need.item())).toList();
        return ProcurementBudget.forPurchase(remainder, unselected, item);
    }
    public static boolean fundsRestart(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        var economy = view.supplyPlan().flatMap(plan -> plan.economy());
        if (economy.isPresent()) {
            var restart = needs(view, rules).stream().filter(need -> need.restartQuantity() > 0).toList();
            // A priority hint cannot borrow another item's economic envelope.
            return restart.stream().anyMatch(need -> need.item().equals(item))
                    && restart.stream().allMatch(need -> need.unitLimit().multiply(need.restartQuantity()).raw()
                        <= economy.orElseThrow().purchaseBudgets().getOrDefault(need.item(), 0L));
        }
        return ProcurementBudget.fundsRestart(view.availableMoney(), needs(view, rules), item);
    }
    public static FixedScalar optional(FrontierWorldState state, SubjectId sponsor) {
        var participant = state.companies().goodsTrade().participants().participants().get(sponsor);
        if (participant == null) return state.inventory().economics().availableToReserve(sponsor);
        var view = GoodsParticipantView.read(state, participant);
        var economy = view.supplyPlan().flatMap(plan -> plan.economy());
        if (economy.isPresent()) return new FixedScalar(Math.max(0, view.availableMoney().raw()
                - economy.orElseThrow().purchaseBudgets().values().stream().reduce(0L, Math::addExact)));
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
