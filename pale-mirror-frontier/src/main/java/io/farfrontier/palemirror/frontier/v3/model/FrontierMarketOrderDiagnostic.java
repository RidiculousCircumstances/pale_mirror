package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only, bounded projection of one exact market-backed production commitment. */
public record FrontierMarketOrderDiagnostic(SubjectId orderId, String status, SubjectId jobId, boolean jobActive,
                                            SubjectId reservationId, boolean reservationActive, String taskStatus) {
    public FrontierMarketOrderDiagnostic {
        Objects.requireNonNull(orderId, "order id"); Objects.requireNonNull(status, "status"); Objects.requireNonNull(jobId, "job id");
        Objects.requireNonNull(reservationId, "reservation id"); Objects.requireNonNull(taskStatus, "task status");
    }

    public static Optional<FrontierMarketOrderDiagnostic> inspect(FrontierWorldState state, SubjectId orderId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(orderId, "order id");
        MarketWorkOrder order = state.companies().market().workOrders().get(orderId);
        if (order == null) return Optional.empty();
        StrategicTask task = state.strategicPlans().tasks().get(order.taskId());
        return Optional.of(new FrontierMarketOrderDiagnostic(order.id(), order.status().name(), order.jobId(), state.productionJobs().containsKey(order.jobId()),
                order.reservationId(), state.inventory().economics().reservations().containsKey(order.reservationId()), task == null ? "MISSING" : task.status().name()));
    }
}
