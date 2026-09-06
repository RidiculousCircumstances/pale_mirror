package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable settlement policy change caused by known canonical health conditions. */
public record SettlementQuarantineTransition(SubjectId settlementId, SettlementQuarantineStatus status, long atTick) implements FrontierPayload {
    public SettlementQuarantineTransition {
        Objects.requireNonNull(settlementId, "quarantine settlement");
        Objects.requireNonNull(status, "quarantine status");
    }

    @Override public String type() { return "frontier.settlement_quarantine_transition"; }
}
