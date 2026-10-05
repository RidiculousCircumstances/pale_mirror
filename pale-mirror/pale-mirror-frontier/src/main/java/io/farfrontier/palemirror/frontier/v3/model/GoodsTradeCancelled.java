package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

public record GoodsTradeCancelled(GoodsTradeDisposition disposition) implements FrontierPayload {
    public GoodsTradeCancelled { Objects.requireNonNull(disposition); }
    @Override public String type() { return "frontier.goods_trade_cancelled"; }
}
