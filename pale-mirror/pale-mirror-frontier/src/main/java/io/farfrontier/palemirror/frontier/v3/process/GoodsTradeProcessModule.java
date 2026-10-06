package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Set;

/** The registered commercial owner; no concrete producer, carrier or physical actuator. */
final class GoodsTradeProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.goods_order_placed", "frontier.goods_trade_reserved", "frontier.goods_trade_accepted",
            "frontier.goods_trade_cancelled", "frontier.goods_trade_claim_partitioned", "frontier.goods_trade_retired", "frontier.goods_participant_reviewed");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor(
            "goods-trade", Set.of(), Set.of(GoodsTradeReceiptProcess.REVIEW, GoodsParticipantProcess.REVIEW, GoodsParticipantWakeup.OPPORTUNITY), TYPES,
            java.util.stream.Stream.concat(TYPES.stream(), Set.of("kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled",
                    "frontier.shipment_receipt_acknowledged", "frontier.shipment_dispatched", "frontier.shipment_retired", "frontier.transport_mission_started").stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()), TYPES);
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return apply(state, event.subject(), event.instant().ticks(), event.payload());
    }
    private static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, long now, FrontierPayload payload) {
        if (payload instanceof GoodsParticipantReviewed reviewed) {
            if (reviewed.atTick() > now) throw new IllegalArgumentException("participant review is in the future");
            var trade = state.companies().goodsTrade();
            return state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(
                    trade.participants().reviewed(subject, reviewed.expectedRevision(), reviewed.decision(), reviewed.atTick()))));
        }
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
