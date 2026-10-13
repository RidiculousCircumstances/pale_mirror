package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Withdraws only unmatched consent; accepted terms and obligations remain immutable. */
public record GoodsTradeOrderWithdrawn(SubjectId orderId, int expectedCommittedQuantity) implements FrontierPayload {
    public GoodsTradeOrderWithdrawn {
        Objects.requireNonNull(orderId);
        if (expectedCommittedQuantity < 0) throw new IllegalArgumentException("invalid order withdrawal predecessor");
    }
    @Override public String type() { return "frontier.goods_order_withdrawn"; }
}
