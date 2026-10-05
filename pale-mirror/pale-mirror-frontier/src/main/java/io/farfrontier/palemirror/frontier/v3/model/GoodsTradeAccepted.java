package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;
public record GoodsTradeAccepted(GoodsTradeAcceptance receipt) implements FrontierPayload {
    public GoodsTradeAccepted { Objects.requireNonNull(receipt); }
    @Override public String type() { return "frontier.goods_trade_accepted"; }
}
