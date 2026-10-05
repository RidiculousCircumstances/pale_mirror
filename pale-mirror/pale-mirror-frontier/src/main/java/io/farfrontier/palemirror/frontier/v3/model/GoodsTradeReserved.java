package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.List;
import java.util.Objects;
public record GoodsTradeReserved(GoodsTradeContract contract, List<GoodsTradeStockAllocation> allocations) implements FrontierPayload {
    public GoodsTradeReserved {
        Objects.requireNonNull(contract); allocations = List.copyOf(allocations);
        if (allocations.isEmpty() || allocations.size() > 64) throw new IllegalArgumentException("invalid goods allocation count");
    }
    @Override public String type() { return "frontier.goods_trade_reserved"; }
}
