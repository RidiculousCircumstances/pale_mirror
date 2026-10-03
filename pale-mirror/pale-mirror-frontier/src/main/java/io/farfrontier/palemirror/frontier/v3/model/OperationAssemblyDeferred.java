package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable observation that one exact HOT assembly target is presently obstructed. */
public record OperationAssemblyDeferred(SubjectId operationId, OperationAssemblyDeferral deferral, ActorExecutionGroup executions) implements FrontierPayload {
    public OperationAssemblyDeferred {
        Objects.requireNonNull(executions, "operation participant executions")
                .requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operationId, executions.members().stream().map(ActorExecutionId::actorId).toList());
        operationId = Objects.requireNonNull(operationId, "deferred assembly operation");
        deferral = Objects.requireNonNull(deferral, "assembly deferral");
    }
    @Override public String type() { return "frontier.operation_assembly_deferred"; }
}
