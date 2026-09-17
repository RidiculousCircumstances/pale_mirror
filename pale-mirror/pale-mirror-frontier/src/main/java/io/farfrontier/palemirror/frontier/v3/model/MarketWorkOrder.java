package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One accepted quote bound to the producing task and its exact financial reservation. */
public record MarketWorkOrder(SubjectId id, SubjectId demandId, SubjectId quoteId, SubjectId sellerId, SubjectId taskId, SubjectId jobId,
                       SubjectId reservationId, FixedScalar acceptedTotalPrice, MarketWorkOrderStatus status,
                       Optional<TerminalProductionReceipt> terminalReceipt, Optional<RelationshipIncident> relationshipIncident) {
    public MarketWorkOrder(SubjectId id, SubjectId demandId, SubjectId quoteId, SubjectId sellerId, SubjectId taskId, SubjectId jobId,
                           SubjectId reservationId, FixedScalar acceptedTotalPrice, MarketWorkOrderStatus status) {
        this(id, demandId, quoteId, sellerId, taskId, jobId, reservationId, acceptedTotalPrice, status, Optional.empty(), Optional.empty());
    }
    public MarketWorkOrder {
        Objects.requireNonNull(id, "market work order id"); Objects.requireNonNull(demandId, "market work order demand");
        Objects.requireNonNull(quoteId, "market work order quote"); Objects.requireNonNull(sellerId, "market work order seller");
        Objects.requireNonNull(taskId, "market work order task"); Objects.requireNonNull(jobId, "market work order job");
        Objects.requireNonNull(reservationId, "market work order reservation");
        Objects.requireNonNull(acceptedTotalPrice, "market work order total price"); Objects.requireNonNull(status, "market work order status");
        terminalReceipt = Objects.requireNonNull(terminalReceipt, "market work order terminal receipt");
        relationshipIncident = Objects.requireNonNull(relationshipIncident, "market work order relationship incident");
        if (!id.value().startsWith("order:") || !sellerId.value().startsWith("company:") || !reservationId.value().startsWith("reservation:")
                || acceptedTotalPrice.raw() <= 0L) {
            throw new IllegalArgumentException("market work order must retain named seller, reservation and positive total");
        }
    }

    MarketWorkOrder withStatus(MarketWorkOrderStatus next) {
        return new MarketWorkOrder(id, demandId, quoteId, sellerId, taskId, jobId, reservationId, acceptedTotalPrice, next, terminalReceipt, relationshipIncident);
    }
    MarketWorkOrder fulfilled(TerminalProductionReceipt receipt) {
        if (!jobId.equals(receipt.jobId())) throw new IllegalArgumentException("terminal receipt must retain this order job");
        return new MarketWorkOrder(id, demandId, quoteId, sellerId, taskId, jobId, reservationId, acceptedTotalPrice,
                MarketWorkOrderStatus.FULFILLED, Optional.of(receipt), Optional.empty());
    }
    MarketWorkOrder conflict(RelationshipIncident incident) {
        if (!id.equals(incident.ownerId()) || !id.equals(incident.sourceId())) throw new IllegalArgumentException("relationship incident owner mismatch");
        return new MarketWorkOrder(id, demandId, quoteId, sellerId, taskId, jobId, reservationId, acceptedTotalPrice,
                MarketWorkOrderStatus.CONFLICT, Optional.empty(), Optional.of(incident));
    }
}
