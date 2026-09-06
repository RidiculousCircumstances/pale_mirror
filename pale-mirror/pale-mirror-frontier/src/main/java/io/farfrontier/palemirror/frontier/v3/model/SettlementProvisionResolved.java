package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record SettlementProvisionResolved(SubjectId settlementId, SettlementProvisionStatus status) implements FrontierPayload {
    public SettlementProvisionResolved {
        Objects.requireNonNull(settlementId, "provision settlement");
        if (status != SettlementProvisionStatus.SHORTAGE && status != SettlementProvisionStatus.RATIONED && status != SettlementProvisionStatus.CONFLICT) {
            throw new IllegalArgumentException("provision resolution must be a terminal shortage state");
        }
    }
    @Override public String type() { return "frontier.settlement_provision_resolved"; }
}
