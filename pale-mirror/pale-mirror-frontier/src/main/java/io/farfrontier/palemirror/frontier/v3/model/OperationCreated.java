package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable admission of a named COLD operation after its cargo is in canonical custody. */
public record OperationCreated(RouteOperation operation, ActorExecutionGroup executions) implements FrontierPayload {
    public OperationCreated {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(executions, "operation assembly executions")
                .requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operation.id(), operation.participantIds());
    }
    @Override public String type() { return "frontier.operation_created"; }
}
