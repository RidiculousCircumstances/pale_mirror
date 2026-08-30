package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable ownership of the next exact logistics segment before either COLD or HOT advances it. */
public record OperationTravelStarted(SubjectId operationId, OperationTravel travel) implements FrontierPayload {
    public OperationTravelStarted {
        Objects.requireNonNull(operationId, "operation travel operation");
        Objects.requireNonNull(travel, "operation travel state");
    }

    @Override public String type() { return "frontier.operation_travel_started"; }
}
