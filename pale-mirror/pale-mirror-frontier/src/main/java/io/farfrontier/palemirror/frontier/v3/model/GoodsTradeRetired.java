package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Set;

/** Explicitly retires an expired, commercially closed order/contract component. */
public record GoodsTradeRetired(Set<SubjectId> orderIds, Set<SubjectId> contractIds) implements FrontierPayload {
    public GoodsTradeRetired {
        orderIds = Set.copyOf(orderIds); contractIds = Set.copyOf(contractIds);
        if (orderIds.isEmpty() || orderIds.size() > GoodsTradeState.MAX_ORDERS || contractIds.size() > GoodsTradeState.MAX_CONTRACTS) {
            throw new IllegalArgumentException("invalid bounded goods retirement");
        }
    }
    @Override public String type() { return "frontier.goods_trade_retired"; }
}
