package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One shared COLD formation step under its exact Overseer, including owner-retained rejoin. */
public record SettlementAssaultAttackerAdvanced(SubjectId assaultId, SubjectId attackerId, ExpeditionMarchStep predecessor,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultAttackerAdvanced {
        SettlementAssaultExecutionAuthority.requireOwner(executions, assaultId);
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(attackerId, "assault attacker");
        Objects.requireNonNull(predecessor, "assault spatial predecessor");
    }
    @Override public String type() { return "frontier.settlement_assault_attacker_advanced"; }
}
