package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import java.util.List;

/** Economic policy translates its supply advice into spending priority, never new money or holds. */
public final class GoodsParticipantFunding {
    private GoodsParticipantFunding() { }
    /** One immutable commercial input view, borrowed for one planning decision.
     * A negative answer proves that no first allocation at these quote floors
     * can be funded. It does not filter later lines of a composite purchase. */
    public static final class Purchases {
        private final GoodsParticipantView view;
        private final GoodsTradeRules rules;
        private final java.util.Map<String, FixedScalar> limits = new java.util.HashMap<>();
        private Purchases(GoodsParticipantView view, GoodsTradeRules rules) {
            this.view = java.util.Objects.requireNonNull(view); this.rules = java.util.Objects.requireNonNull(rules);
        }
        public boolean canStart(java.util.Map<String, FixedScalar> minimumPrices) {
            for (var entry : minimumPrices.entrySet()) {
                if (!view.stocks().containsKey(entry.getKey()) || entry.getValue().raw() <= 0)
                    throw new IllegalArgumentException("first purchase has an undeclared resource or nonpositive price");
                var funding = limits.computeIfAbsent(entry.getKey(), item -> purchase(view, rules, item));
                if (funding.compareTo(entry.getValue()) >= 0) return true;
            }
            return false;
        }
    }
    public static Purchases purchases(GoodsParticipantView view, GoodsTradeRules rules) { return new Purchases(view, rules); }
    public static FixedScalar purchase(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        if (view.awaitingReview().contains(item)) return FixedScalar.ZERO;
        if (view.participant().policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT && rules.supplyPlanning().enabled()
                && view.supplyPlan().isEmpty()) return FixedScalar.ZERO;
        var economy = view.supplyPlan().flatMap(plan -> plan.economy());
        if (economy.isPresent()) return io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPurchaseFunding.available(
                economy.orElseThrow(), view.availableMoney(), needs(view, rules), item,
                immediateAlternative(view, rules, item));
        var preferred = view.supplyPlan().map(plan -> plan.preferredPurchaseSpending()).orElse(java.util.Map.of());
        if (preferred.isEmpty()) return ProcurementBudget.forPurchase(view.availableMoney(), needs(view, rules), item);
        long other = preferred.entrySet().stream().filter(e -> !e.getKey().equals(item)).mapToLong(java.util.Map.Entry::getValue)
                .reduce(0L, Math::addExact);
        var remainder = new FixedScalar(Math.max(0, view.availableMoney().raw() - other));
        // These are bounded affordable alternatives selected under the shared budget,
        // not reservations. Current money and exact reservations bound every use of the retained advice.
        if (preferred.containsKey(item)) return new FixedScalar(Math.min(remainder.raw(), preferred.get(item)));
        var unselected = needs(view, rules).stream().filter(need -> !preferred.containsKey(need.item())).toList();
        return ProcurementBudget.forPurchase(remainder, unselected, item);
    }
    private static long immediateAlternative(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        var demand = view.supplyPlan().orElseThrow().require(item);
        // Dependencies inherit the parent's deadline reason; that does not turn
        // every raw material into an immediate consumption alternative.
        return rules.supplyPlanning().require(item).essential()
                && demand.reasons().contains(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Reason.CONSUMPTION_DEADLINE)
                ? demand.urgentPurchase() : 0;
    }
    public static boolean fundsRestart(GoodsParticipantView view, GoodsTradeRules rules, String item) {
        if (view.awaitingReview().contains(item) || rules.supplyPlanning().enabled() && view.supplyPlan().isEmpty()) return false;
        var economy = view.supplyPlan().flatMap(plan -> plan.economy());
        if (economy.isPresent()) {
            return economy.orElseThrow().fundedRestarts().contains(item);
        }
        return ProcurementBudget.fundsRestart(view.availableMoney(), needs(view, rules), item);
    }
    public static FixedScalar optional(FrontierWorldState state, SubjectId sponsor, long atTick) {
        var participant = state.companies().goodsTrade().participants().participants().get(sponsor);
        if (participant == null) return state.inventory().economics().availableToReserve(sponsor);
        var view = GoodsParticipantAssessment.domestic(state, participant, atTick).view();
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()
                && (view.supplyPlan().isEmpty() || !view.awaitingReview().isEmpty())) return FixedScalar.ZERO;
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
                    plan.economy().map(economy -> new FixedScalar(economy.purchasePrices().get(commodity.itemKind())))
                            .orElseGet(() -> GoodsParticipantPricing.bid(view.participant(), commodity)), demand.deadlineTicks(), restart);
        }).toList()).orElse(List.of());
    }
}
