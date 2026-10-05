package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;
public record GoodsTradeOrderPlaced(GoodsTradeOrder order) implements FrontierPayload {
    public GoodsTradeOrderPlaced { Objects.requireNonNull(order); }
    @Override public String type() { return "frontier.goods_order_placed"; }
}
