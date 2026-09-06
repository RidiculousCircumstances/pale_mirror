package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact assault after local perception validation. */
public record SettlementAssaultStarted(SettlementAssault assault) implements FrontierPayload {
    public SettlementAssaultStarted { Objects.requireNonNull(assault, "settlement assault"); }
    @Override public String type() { return "frontier.settlement_assault_started"; }
}
