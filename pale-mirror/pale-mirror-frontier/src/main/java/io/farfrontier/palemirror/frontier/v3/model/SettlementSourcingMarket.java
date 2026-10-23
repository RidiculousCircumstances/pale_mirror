package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Canonical economic facts -> pure sourcing advice; no recursive participant views or ledger writes. */
final class SettlementSourcingMarket {
    private record Query(GoodsParticipant buyer, long now) { }
    private record Facts(Map<String, Long> references, List<SourcingMarket.Offer> offers) {
        private Facts { references = Map.copyOf(references); offers = List.copyOf(offers); }
    }
    private static final ImmutableInputView<BoundedValueMemo<Query, Facts>> FACTS = new ImmutableInputView<>();
    static SourcingMarket read(FrontierWorldState state, GoodsParticipant buyer, List<SupplyPlanner.Process> capacities, long now) {
        if (now < 0) throw new IllegalArgumentException("negative sourcing instant");
        var tradeRules = state.bootstrap().ruleset().goodsTrade();
        var selection = tradeRules.supplyPlanning().sourcing();
        if (selection.isEmpty()) return SourcingMarket.none();
        var policy = selection.orElseThrow();
        // Equipment and production compare different capacities against the same
        // current market. Share only observed offers, never their allocation or
        // the resulting make-or-buy decision. A draft reservation changes state.
        var facts = FACTS.get(List.of(state), () -> new BoundedValueMemo<Query, Facts>(256))
                .get(new Query(buyer, now), () -> observe(state, buyer, now));
        return new SourcingMarket(state.inventory().economics().availableToReserve(buyer.party().id()).raw(),
                policy.switchingSavingsPermille(), SourcingValuation.processes(capacities, facts.references(), facts.offers(),
                        policy.labourValuePerTick()), facts.offers());
    }
    private static Facts observe(FrontierWorldState state, GoodsParticipant buyer, long now) {
        var tradeRules = state.bootstrap().ruleset().goodsTrade();
        var trade = state.companies().goodsTrade();
        var references = new TreeMap<String, Long>();
        tradeRules.policies().get(buyer.policy()).forEach(commodity -> {
            references.put(commodity.itemKind(), trade.priceHistory().reference(commodity.itemKind(), tradeRules.pricing().recencyTicks())
                    .orElse(commodity.minimumSellPrice()).raw());
        });
        var offers = new TreeMap<String, SourcingMarket.Offer>();
        for (var order : GoodsSaleQuotes.forBuyer(trade, buyer.party())) {
            if (order.expiresAtTick() <= now || order.availableQuantity() == 0 || !references.containsKey(order.itemKind())
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
        return new Facts(references, List.copyOf(offers.values()));
    }
    private SettlementSourcingMarket() { }
}
