package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Canonical economic facts -> pure sourcing advice; no recursive participant views or ledger writes. */
final class SettlementSourcingMarket {
    private static final ImmutableInputView<List<GoodsTradeOrder>> ORDERED = new ImmutableInputView<>();
    static SourcingMarket read(FrontierWorldState state, GoodsParticipant buyer, List<SupplyPlanner.Process> capacities, long now) {
        if (now < 0) throw new IllegalArgumentException("negative sourcing instant");
        var tradeRules = state.bootstrap().ruleset().goodsTrade();
        var selection = tradeRules.supplyPlanning().sourcing();
        if (selection.isEmpty()) return SourcingMarket.none();
        var policy = selection.orElseThrow();
        var trade = state.companies().goodsTrade();
        var references = new TreeMap<String, Long>();
        tradeRules.policies().get(buyer.policy()).forEach(commodity -> {
            references.put(commodity.itemKind(), trade.priceHistory().reference(commodity.itemKind(), tradeRules.pricing().recencyTicks())
                    .orElse(commodity.minimumSellPrice()).raw());
        });
        var offers = new TreeMap<String, SourcingMarket.Offer>();
        var ordered = ORDERED.get(List.of(trade.orders()), () -> trade.orders().values().stream()
                .sorted(Comparator.comparing(GoodsTradeOrder::unitPriceLimit).thenComparing(GoodsTradeOrder::id)).toList());
        for (var order : ordered) {
            if (order.side() != GoodsTradeOrder.Side.SELL || !order.counterparty().equals(buyer.party())
                    || order.expiresAtTick() <= now || order.availableQuantity() == 0 || !references.containsKey(order.itemKind())
                    || buyer.known().stream().noneMatch(known -> known.party().equals(order.party()))) continue;
            var seller = trade.participants().participants().get(order.party().id());
            if (seller == null || !GoodsParticipantStorage.eligible(state, seller, order.containerId(), order.itemKind())) continue;
            String pool = order.party().id().value() + "|" + order.containerId().value() + "|" + order.itemKind();
            if (offers.containsKey(pool)) continue; // One physical stock pool, not duplicate quotes of it.
            int quantity = Math.min(GoodsTransportOptions.maximumQuantity(state, order.itemKind()), Math.min(order.availableQuantity(),
                    GoodsParticipantStorage.unclaimed(state, seller, order.containerId(), order.itemKind())));
            if (quantity == 0) continue;
            var timing = ProcurementTravelForecast.read(state, order.containerId(), order.itemKind(), buyer,
                    TransportOrganization.Itinerary.DESTINATION_COLLECTION);
            if (timing.isEmpty()) continue;
            var transport = GoodsTransportOptions.costCurve(state, buyer, timing.orElseThrow().journeyTicks(), order.itemKind());
            offers.put(pool, new SourcingMarket.Offer(pool, order.itemKind(), quantity, order.unitPriceLimit().raw(),
                    order.unitPriceLimit().raw(), timing.orElseThrow().deliveryTicks(), transport));
        }
        var available = List.copyOf(offers.values());
        return new SourcingMarket(state.inventory().economics().availableToReserve(buyer.party().id()).raw(),
                policy.switchingSavingsPermille(), SourcingValuation.processes(capacities, references, available,
                        policy.labourValuePerTick()), available);
    }
    private SettlementSourcingMarket() { }
}
