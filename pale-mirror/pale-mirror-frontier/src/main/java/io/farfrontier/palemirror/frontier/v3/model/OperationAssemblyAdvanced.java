package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable bounded COLD/HOT-neutral progress of a named operation's assembly approaches. */
public record OperationAssemblyAdvanced(SubjectId operationId, OperationAssembly assembly, ActorExecutionGroup executions,
                                       java.util.Optional<HotArrival> hotArrival) implements FrontierPayload {
    /** A scheduled COLD transition, never a loaded physical arrival. */
    public OperationAssemblyAdvanced(SubjectId operationId, OperationAssembly assembly, ActorExecutionGroup executions) {
        this(operationId, assembly, executions, java.util.Optional.empty());
    }
    public record HotArrival(ActorBodyId body, long leaseRevision) {
        public HotArrival { Objects.requireNonNull(body); if (leaseRevision < 1) throw new IllegalArgumentException("arrival requires its captured lease revision"); }
    }
    public OperationAssemblyAdvanced {
        Objects.requireNonNull(executions, "operation participant executions")
                .requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operationId, assembly.members().keySet());
        operationId = Objects.requireNonNull(operationId, "operation assembly operation id");
        assembly = Objects.requireNonNull(assembly, "operation assembly");
        Objects.requireNonNull(hotArrival);
        if (hotArrival.isPresent() && !assembly.members().containsKey(hotArrival.orElseThrow().body().actorId()))
            throw new IllegalArgumentException("assembly arrival has a foreign actor");
    }
    @Override public String type() { return "frontier.operation_assembly_advanced"; }
}
