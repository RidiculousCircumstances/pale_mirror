package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of a named COLD operation after its cargo is in canonical custody. */
public record OperationCreated(RouteOperation operation) implements FrontierPayload {
    public OperationCreated { Objects.requireNonNull(operation, "operation"); }
    @Override public String type() { return "frontier.operation_created"; }
}
