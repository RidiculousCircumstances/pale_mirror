package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit terminal owner result; it cannot be inferred from absent bodies or unloaded chunks. */
record SettlementAssaultResolved(SubjectId assaultId, SettlementAssaultOutcome outcome) implements FrontierPayload {
    SettlementAssaultResolved {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(outcome, "settlement assault outcome");
    }
    @Override public String type() { return "frontier.settlement_assault_resolved"; }
}
