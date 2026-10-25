package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Supplier-owned price/capability advice. Not sell stock, a promise of arrival,
 * cash, a reservation, worker authority or consent to a trade. */
public record ConditionalProductionQuote(GoodsTradeParty supplier, GoodsTradeParty recipient, String itemKind,
        int quantity, long goodsUnitPrice, long resourceFreight, long cashFreight,
        GoodsTradeFulfillment fulfillment, long capacityWindowTicks, long deliveryTicks,
        long sourceInputRevision, long sourcePlanRevision, long assessedAtTick, long expiresAtTick) {
    public record Key(GoodsTradeParty supplier, GoodsTradeParty recipient, String itemKind) { }
    public ConditionalProductionQuote {
        Objects.requireNonNull(supplier); Objects.requireNonNull(recipient); Objects.requireNonNull(fulfillment);
        if (supplier.id().equals(recipient.id()) || itemKind == null
                || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || quantity < 1 || quantity > ResourceLot.MAX_QUANTITY || goodsUnitPrice < 1
                || cashFreight < 0 || resourceFreight < cashFreight || capacityWindowTicks < 1
                || deliveryTicks < 0 || sourceInputRevision < 0 || sourcePlanRevision < 1
                || assessedAtTick < 0 || expiresAtTick <= assessedAtTick)
            throw new IllegalArgumentException("invalid conditional production quote");
        fulfillment.require(supplier, recipient);
        Math.addExact(Math.multiplyExact(goodsUnitPrice, quantity), resourceFreight);
    }
    public Key key() { return new Key(supplier, recipient, itemKind); }
    public long resourceUnitExpense(int requested) {
        if (requested < 1) throw new IllegalArgumentException("empty conditional acquisition");
        return Math.addExact(goodsUnitPrice, Math.ceilDiv(resourceFreight, Math.min(requested, quantity)));
    }
    public long cashUnitExpense(int requested) {
        if (requested < 1) throw new IllegalArgumentException("empty conditional acquisition");
        return Math.addExact(goodsUnitPrice, Math.ceilDiv(cashFreight, Math.min(requested, quantity)));
    }
}
