package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit terminal owner result; it cannot be inferred from absent bodies or unloaded chunks. */
public record SettlementAssaultResolved(SubjectId assaultId, SettlementAssaultOutcome outcome,
                                        HiveReturnAdmission returnAdmission,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultResolved {
        SettlementAssaultExecutionAuthority.requireOwner(executions, assaultId);
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(outcome, "settlement assault outcome");
        Objects.requireNonNull(returnAdmission, "explicit assault aftermath admission");
    }
    @Override public String type() { return "frontier.settlement_assault_resolved"; }
}
