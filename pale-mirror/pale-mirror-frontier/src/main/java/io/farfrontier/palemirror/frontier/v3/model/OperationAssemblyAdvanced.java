package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable bounded COLD/HOT-neutral progress of a named operation's assembly approaches. */
public record OperationAssemblyAdvanced(SubjectId operationId, OperationAssembly assembly, ActorExecutionGroup executions) implements FrontierPayload {
    public OperationAssemblyAdvanced {
        Objects.requireNonNull(executions, "operation participant executions")
                .requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operationId, assembly.members().keySet());
        operationId = Objects.requireNonNull(operationId, "operation assembly operation id");
        assembly = Objects.requireNonNull(assembly, "operation assembly");
    }
    @Override public String type() { return "frontier.operation_assembly_advanced"; }
}
