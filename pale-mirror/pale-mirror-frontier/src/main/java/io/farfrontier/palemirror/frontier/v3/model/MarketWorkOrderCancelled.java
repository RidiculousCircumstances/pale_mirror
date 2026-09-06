package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Durable release of an accepted order before its work can have a physical
 * effect. The reducer proves that no production intent exists before it frees
 * either the exact input hold or the named financial reservation.
 */
public record MarketWorkOrderCancelled(SubjectId orderId, SubjectId jobId, ProductionBlockReason reason) implements FrontierPayload {
    public MarketWorkOrderCancelled {
        Objects.requireNonNull(orderId, "market order id");
        Objects.requireNonNull(jobId, "market job id");
        Objects.requireNonNull(reason, "market cancellation reason");
    }

    @Override public String type() { return "frontier.market_work_order_cancelled"; }
}
