package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One accepted quote bound to the producing task and its exact financial reservation. */
public record MarketWorkOrder(SubjectId id, SubjectId demandId, SubjectId quoteId, SubjectId sellerId, SubjectId taskId, SubjectId jobId,
                       SubjectId reservationId, FixedScalar acceptedTotalPrice, MarketWorkOrderStatus status) {
    public MarketWorkOrder {
        Objects.requireNonNull(id, "market work order id"); Objects.requireNonNull(demandId, "market work order demand");
        Objects.requireNonNull(quoteId, "market work order quote"); Objects.requireNonNull(sellerId, "market work order seller");
        Objects.requireNonNull(taskId, "market work order task"); Objects.requireNonNull(jobId, "market work order job");
        Objects.requireNonNull(reservationId, "market work order reservation");
        Objects.requireNonNull(acceptedTotalPrice, "market work order total price"); Objects.requireNonNull(status, "market work order status");
        if (!id.value().startsWith("order:") || !sellerId.value().startsWith("company:") || !reservationId.value().startsWith("reservation:")
                || acceptedTotalPrice.raw() <= 0L) {
            throw new IllegalArgumentException("market work order must retain named seller, reservation and positive total");
        }
    }

    MarketWorkOrder withStatus(MarketWorkOrderStatus next) {
        return new MarketWorkOrder(id, demandId, quoteId, sellerId, taskId, jobId, reservationId, acceptedTotalPrice, next);
    }
}
