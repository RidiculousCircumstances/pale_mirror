package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record SettlementProvisionConsumed(SubjectId settlementId, SubjectId itemId, int count) implements FrontierPayload {
    public SettlementProvisionConsumed {
        Objects.requireNonNull(settlementId, "provision settlement"); Objects.requireNonNull(itemId, "provision item");
        if (count < 1 || count > 64) throw new IllegalArgumentException("provision count must be 1..64");
    }
    @Override public String type() { return "frontier.settlement_provision_consumed"; }
}
