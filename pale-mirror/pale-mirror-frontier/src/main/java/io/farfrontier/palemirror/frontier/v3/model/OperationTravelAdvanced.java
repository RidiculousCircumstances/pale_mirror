package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Exact provider evidence advances a route or its owner-local approach, never an uninspected HOT pose. */
public record OperationTravelAdvanced(SubjectId operationId, OperationTravel travel, OperationTravelObservation observation) implements FrontierPayload {
    public OperationTravelAdvanced {
        Objects.requireNonNull(observation, "operation travel evidence").executions()
                .requireDeclaration(ActorActivityKind.LOGISTICS, operationId, travel.formation().keySet());
        Objects.requireNonNull(operationId, "operation travel operation");
        Objects.requireNonNull(travel, "operation travel state");
    }
    public ActorExecutionGroup executions() { return observation.executions(); }
    @Override public String type() { return "frontier.operation_travel_advanced"; }
}
