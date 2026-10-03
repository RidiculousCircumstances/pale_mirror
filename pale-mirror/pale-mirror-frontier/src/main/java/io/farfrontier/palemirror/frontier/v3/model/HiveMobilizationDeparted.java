package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One atomic custody transfer from a complete cocoon assembly to its exact operation. */
public record HiveMobilizationDeparted(SubjectId mobilizationId, SettlementAssault assault,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup assaultExecutions) implements FrontierPayload {
    public HiveMobilizationDeparted {
        assaultExecutions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SETTLEMENT_ASSAULT, assault.id(), SettlementAssaultExecutionAuthority.participants(assault));
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(assault, "departed settlement assault");
        Objects.requireNonNull(executions).requireDeclaration(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.HIVE_TASK_ASSEMBLY,
                mobilizationId, assault.attackerIds());
    }
    @Override public String type() { return "frontier.hive_mobilization_departed"; }
}
