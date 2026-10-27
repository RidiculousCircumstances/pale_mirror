package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Set;

/** Retires terminal contracts and unreferenced exhausted/expired orders independently. */
public record GoodsTradeRetired(Set<SubjectId> orderIds, Set<SubjectId> contractIds) implements FrontierPayload {
    public GoodsTradeRetired {
        orderIds = Set.copyOf(orderIds); contractIds = Set.copyOf(contractIds);
        if (orderIds.isEmpty() && contractIds.isEmpty() || orderIds.size() > GoodsTradeState.MAX_ORDERS || contractIds.size() > GoodsTradeState.MAX_CONTRACTS) {
            throw new IllegalArgumentException("invalid bounded goods retirement");
        }
    }
    @Override public String type() { return "frontier.goods_trade_retired"; }
}
