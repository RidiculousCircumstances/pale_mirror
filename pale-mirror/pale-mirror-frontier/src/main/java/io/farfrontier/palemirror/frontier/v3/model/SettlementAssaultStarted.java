package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact assault after local perception validation. */
public record SettlementAssaultStarted(SettlementAssault assault,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultStarted { Objects.requireNonNull(assault, "settlement assault"); executions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SETTLEMENT_ASSAULT,
            assault.id(), SettlementAssaultExecutionAuthority.participants(assault)); }
    @Override public String type() { return "frontier.settlement_assault_started"; }
}
