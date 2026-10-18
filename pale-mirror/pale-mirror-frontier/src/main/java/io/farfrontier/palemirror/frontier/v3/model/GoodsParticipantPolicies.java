package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed composition: only the producer-declared policy key selects a strategy. */
public final class GoodsParticipantPolicies {
    private static final Map<GoodsPolicyKind, GoodsParticipantPolicy> POLICIES = register(List.of(
            new StockTargetPolicy(GoodsPolicyKind.PUBLIC_SETTLEMENT), new StockTargetPolicy(GoodsPolicyKind.OWN_ACCOUNT_COMPANY)));
    private GoodsParticipantPolicies() { }
    public static Map<GoodsPolicyKind, GoodsParticipantPolicy> register(List<GoodsParticipantPolicy> values) {
        var map = new EnumMap<GoodsPolicyKind, GoodsParticipantPolicy>(GoodsPolicyKind.class);
        for (var value : values) if (map.put(value.kind(), value) != null) throw new IllegalArgumentException("duplicate participant strategy");
        if (map.size() != GoodsPolicyKind.values().length) throw new IllegalArgumentException("missing participant strategy");
        return Map.copyOf(map);
    }
    public static GoodsParticipantPolicy require(GoodsParticipant participant) {
        participant.policy().validate(participant.party());
        return Objects.requireNonNull(POLICIES.get(participant.policy()), "unregistered participant strategy");
    }
    /** Both initial policies use target stock; their independently configured catalogs determine their decisions. */
    private record StockTargetPolicy(GoodsPolicyKind kind) implements GoodsParticipantPolicy {
        @Override public String explain(GoodsParticipantView view, GoodsTradeRules rules) {
            var result = new StringBuilder("moneyRaw=" + view.availableMoney().raw());
            view.supplyPlan().flatMap(plan -> plan.economy()).ifPresent(plan -> result.append("; economyHorizon=")
                    .append(plan.horizonTicks()).append("; liquidityRaw=").append(plan.retainedLiquidity())
                    .append("; unfundedRaw=").append(plan.unfundedRequirements()).append("; constraints=")
                    .append(plan.constraints().stream().sorted().toList()));
            for (var commodity : rules.policies().get(kind)) {
                var stock = view.stocks().get(commodity.itemKind());
                int target = view.target(commodity);
                result.append("; ").append(commodity.itemKind()).append(" unclaimed=").append(stock.unclaimedInStorage())
                        .append(" target=").append(target).append(" incoming=").append(stock.expectedIncoming())
                        .append(" askRaw=").append(GoodsParticipantPricing.ask(view.participant(), commodity).raw())
                        .append(" bidRaw=").append(GoodsParticipantPricing.bid(view.participant(), commodity).raw());
                view.supplyPlan().ifPresent(plan -> {
                    var demand = plan.require(commodity.itemKind());
                    result.append(" buy=").append(demand.purchase()).append(" produce=").append(demand.production())
                            .append(" recover=").append(demand.recoveryProduction())
                            .append(" deadline=").append(demand.deadlineTicks()).append(" causes=")
                            .append(demand.reasons().stream().sorted().toList());
                });
                if (stock.unclaimedInStorage() + stock.expectedIncoming() < target
                        && view.availableMoney().raw() == 0) result.append(" NO_FUNDS");
            }
            return result.toString();
        }
        @Override public List<Intent> decide(GoodsParticipantView view, GoodsTradeRules rules) {
            if (view.participant().policy() != kind) throw new IllegalArgumentException("wrong participant policy view");
            var result = new ArrayList<Intent>();
            for (var commodity : rules.policies().get(kind)) {
                var stock = Objects.requireNonNull(view.stocks().get(commodity.itemKind()));
                var buyPrice = GoodsParticipantPricing.bid(view.participant(), commodity);
                var sellPrice = GoodsParticipantPricing.ask(view.participant(), commodity);
                int target = view.target(commodity);
                int missing = view.supplyPlan().map(plan -> Math.toIntExact(plan.require(commodity.itemKind()).purchase()))
                        .orElseGet(() -> target - Math.addExact(stock.unclaimedInStorage(), stock.expectedIncoming()));
                int surplus = view.saleable(commodity);
                if (surplus > 0) {
                    result.add(new Intent(commodity.itemKind(), GoodsTradeOrder.Side.SELL,
                            Math.min(view.supplyPlan().isPresent() ? rules.supplyPlanning().maximumOrderItems() : rules.maximumBatch(), surplus), sellPrice));
                } else if (missing > 0) {
                    int quantity = Math.min(view.supplyPlan().isPresent() ? rules.supplyPlanning().maximumOrderItems() : rules.maximumBatch(), Math.min(missing,
                            view.purchaseLimits().getOrDefault(commodity.itemKind(), Integer.MAX_VALUE)));
                    // A quote is interest, not a financial reservation. Preserve the
                    // desired quantity; matching caps it at the actual seller price
                    // against the remaining budget after each admitted allocation.
                    var limit = new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(
                            Math.min(buyPrice.raw(), GoodsParticipantFunding.purchase(view, rules, commodity.itemKind()).raw()));
                    if (quantity > 0 && limit.raw() > 0) result.add(new Intent(commodity.itemKind(), GoodsTradeOrder.Side.BUY, quantity, limit));
                }
            }
            return List.copyOf(result);
        }
    }
}
