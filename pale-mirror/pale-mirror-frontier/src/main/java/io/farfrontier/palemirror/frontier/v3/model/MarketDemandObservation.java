package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory;
import java.util.*;

/** Market adapter deduplicates buyer intent across suppliers/warehouses and spends its budget only once. */
public final class MarketDemandObservation {
    public static MarketDemandHistory read(FrontierWorldState state, SubjectId buyerId, long now) {
        var trade = state.companies().goodsTrade();
        var buyer = Objects.requireNonNull(trade.participants().participants().get(buyerId), "unknown market buyer");
        var candidates = new TreeMap<String, MarketDemandHistory.Quote>();
        for (var order : trade.orders().values()) {
            if (order.side() != GoodsTradeOrder.Side.BUY || !order.party().id().equals(buyerId)
                    || order.expiresAtTick() <= now || order.availableQuantity() == 0
                    || !GoodsParticipantStorage.eligible(state, buyer, order.containerId(), order.itemKind())) continue;
            var quote = new MarketDemandHistory.Quote(order.availableQuantity(), order.unitPriceLimit().raw());
            candidates.merge(order.itemKind(), quote, (a, b) -> new MarketDemandHistory.Quote(
                    Math.max(a.quantity(), b.quantity()), Math.min(a.unitPrice(), b.unitPrice())));
        }
        long budget = state.inventory().economics().availableToReserve(buyerId).raw();
        var funded = new TreeMap<String, MarketDemandHistory.Quote>();
        for (var entry : candidates.entrySet()) {
            var quote = entry.getValue(); long quantity = Math.min(quote.quantity(), budget / quote.unitPrice());
            if (quantity == 0) continue;
            funded.put(entry.getKey(), new MarketDemandHistory.Quote(quantity, quote.unitPrice()));
            budget -= Math.multiplyExact(quantity, quote.unitPrice());
        }
        return trade.demandHistory().observe(buyerId, now,
                Math.multiplyExact(state.humanPopulation().schedule(buyer.settlementId()).dayTicks(),
                        state.bootstrap().ruleset().fields().cultivation().map(value -> value.stableDemandDays()).orElse(1)), funded);
    }
    private MarketDemandObservation() { }
}
