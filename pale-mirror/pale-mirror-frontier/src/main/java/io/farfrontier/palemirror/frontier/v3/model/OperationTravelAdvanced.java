package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable observation that one exact operation-travel cursor advanced. */
public record OperationTravelAdvanced(SubjectId operationId, OperationTravel travel, ActorExecutionGroup executions) implements FrontierPayload {
    public OperationTravelAdvanced {
        Objects.requireNonNull(executions, "operation participant executions")
                .requireDeclaration(ActorActivityKind.LOGISTICS, operationId, travel.formation().keySet());
        Objects.requireNonNull(operationId, "operation travel operation");
        Objects.requireNonNull(travel, "operation travel state");
    }
    @Override public String type() { return "frontier.operation_travel_advanced"; }
}
