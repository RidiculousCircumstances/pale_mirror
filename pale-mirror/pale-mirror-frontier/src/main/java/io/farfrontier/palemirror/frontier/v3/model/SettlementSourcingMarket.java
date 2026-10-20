package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Canonical economic facts -> pure sourcing advice; no recursive participant views or ledger writes. */
final class SettlementSourcingMarket {
    static SourcingMarket read(FrontierWorldState state, GoodsParticipant buyer, List<SupplyPlanner.Process> capacities) {
        var tradeRules = state.bootstrap().ruleset().goodsTrade();
        var selection = tradeRules.supplyPlanning().sourcing();
        if (selection.isEmpty()) return SourcingMarket.none();
        var policy = selection.orElseThrow();
        var trade = state.companies().goodsTrade();
        long now = trade.participants().participants().values().stream().mapToLong(GoodsParticipant::reviewedAtTick).max().orElse(0);
        var references = new TreeMap<String, Long>();
        var limits = new TreeMap<String, Long>();
        tradeRules.policies().get(buyer.policy()).forEach(commodity -> {
            references.put(commodity.itemKind(), trade.priceHistory().reference(commodity.itemKind(), tradeRules.pricing().recencyTicks())
                    .orElse(commodity.minimumSellPrice()).raw());
            limits.put(commodity.itemKind(), GoodsParticipantPricing.bid(buyer, commodity).raw());
        });
        var offers = new TreeMap<String, SourcingMarket.Offer>();
        for (var order : trade.orders().values().stream().sorted(Comparator.comparing(GoodsTradeOrder::unitPriceLimit)
                .thenComparing(GoodsTradeOrder::id)).toList()) {
            if (order.side() != GoodsTradeOrder.Side.SELL || !order.counterparty().equals(buyer.party())
                    || order.expiresAtTick() <= now || order.availableQuantity() == 0 || !references.containsKey(order.itemKind())
                    || order.unitPriceLimit().raw() > limits.get(order.itemKind())
                    || buyer.known().stream().noneMatch(known -> known.party().equals(order.party()))) continue;
            var seller = trade.participants().participants().get(order.party().id());
            if (seller == null || !GoodsParticipantStorage.eligible(state, seller, order.containerId(), order.itemKind())) continue;
            String pool = order.party().id().value() + "|" + order.itemKind();
            if (offers.containsKey(pool)) continue; // Alternative warehouses quote one seller stock, not independent budgets.
            int quantity = Math.min(order.availableQuantity(), GoodsParticipantStorage.unclaimed(state, seller, order.containerId(), order.itemKind()));
            if (quantity == 0) continue;
            var timing = ProcurementTravelForecast.read(state, order, buyer);
            if (timing.isEmpty()) continue;
            // The actual organizer/escrow remain contract-owned. This conservative comparison
            // charges one carrier's round trip even when the seller ultimately organizes it.
            var cost = GoodsTransportValuation.estimate(state, buyer, timing.orElseThrow().journeyTicks(), 1);
            long transport = Math.addExact(cost.resourceExpense().raw(),
                    Math.multiplyExact(timing.orElseThrow().labourTicks(), policy.labourValuePerTick()));
            offers.put(pool, new SourcingMarket.Offer(pool, order.itemKind(), quantity, order.unitPriceLimit().raw(),
                    order.unitPriceLimit().raw(), timing.orElseThrow().deliveryTicks(), transport, cost.resourceExpense().raw()));
        }
        var available = List.copyOf(offers.values());
        return new SourcingMarket(state.inventory().economics().availableToReserve(buyer.party().id()).raw(),
                policy.switchingSavingsPermille(), SourcingValuation.processes(capacities, references, available,
                        policy.labourValuePerTick()), available);
    }
    private SettlementSourcingMarket() { }
}
