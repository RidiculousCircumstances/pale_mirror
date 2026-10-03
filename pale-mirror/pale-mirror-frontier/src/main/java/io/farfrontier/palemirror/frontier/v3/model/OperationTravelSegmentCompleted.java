package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Atomically closes one arrived exact travel segment and advances its strategic route cursor. */
public record OperationTravelSegmentCompleted(SubjectId operationId, ActorExecutionGroup executions) implements FrontierPayload {
    public OperationTravelSegmentCompleted {
        operationId = Objects.requireNonNull(operationId, "operation travel operation id");
        Objects.requireNonNull(executions, "operation participant executions").requireDeclaration(ActorActivityKind.LOGISTICS,
                operationId, executions.members().stream().map(ActorExecutionId::actorId).toList());
    }
    @Override public String type() { return "frontier.operation_travel_segment_completed"; }
}
