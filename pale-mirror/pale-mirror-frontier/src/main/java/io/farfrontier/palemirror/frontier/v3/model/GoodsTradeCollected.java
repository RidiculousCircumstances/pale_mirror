package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Commercial acceptance of a physically picked-up load; not an invented arrival receipt. */
public record GoodsTradeCollected(SubjectId shipmentId, long expectedShipmentRevision,
                                  GoodsTradeAcceptance receipt) implements FrontierPayload {
    public GoodsTradeCollected {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(receipt);
        if (expectedShipmentRevision < 1) throw new IllegalArgumentException("invalid collection predecessor");
    }
    @Override public String type() { return "frontier.goods_trade_collected"; }
}
