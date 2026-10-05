package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Set;

/** The registered commercial owner; no concrete producer, carrier or physical actuator. */
final class GoodsTradeProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.goods_order_placed", "frontier.goods_trade_reserved", "frontier.goods_trade_accepted",
            "frontier.goods_trade_cancelled", "frontier.goods_trade_claim_partitioned", "frontier.goods_trade_retired");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor(
            "goods-trade", Set.of(), Set.of(), TYPES, TYPES, TYPES);
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return apply(state, event.subject(), event.instant().ticks(), event.payload());
    }
    private static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, long now, FrontierPayload payload) {
        if (payload instanceof GoodsTradeOrderPlaced placed) return GoodsTradeStateSupport.place(state, subject, placed.order(), now);
        if (payload instanceof GoodsTradeReserved reserved) return GoodsTradeStateSupport.reserve(state, subject, reserved.contract(), reserved.allocations(), now);
        if (payload instanceof GoodsTradeAccepted accepted) return GoodsTradeStateSupport.accept(state, subject, accepted.receipt());
        if (payload instanceof GoodsTradeCancelled cancelled) return GoodsTradeStateSupport.cancelBeforeLoading(state, subject, cancelled.disposition());
        if (payload instanceof GoodsTradeClaimPartitioned partitioned) return GoodsTradeStateSupport.partition(state, subject,
                partitioned.contractId(), partitioned.partition());
        if (payload instanceof GoodsTradeRetired retired) return GoodsTradeStateSupport.retire(state, subject, retired, now);
        throw new IllegalArgumentException("unknown goods-trade payload: " + payload.type());
    }
}
