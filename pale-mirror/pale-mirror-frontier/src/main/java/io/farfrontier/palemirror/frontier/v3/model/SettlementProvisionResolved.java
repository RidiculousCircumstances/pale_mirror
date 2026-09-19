package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record SettlementProvisionResolved(SubjectId settlementId, SettlementProvisionStatus status, java.util.Optional<DiagnosticTuple> diagnostic) implements FrontierPayload {
    public SettlementProvisionResolved {
        Objects.requireNonNull(settlementId, "provision settlement");
        if (status != SettlementProvisionStatus.SHORTAGE && status != SettlementProvisionStatus.RATIONED && status != SettlementProvisionStatus.CONFLICT) {
            throw new IllegalArgumentException("provision resolution must be a terminal shortage state");
        }
        diagnostic = java.util.Objects.requireNonNull(diagnostic, "provision diagnostic");
        if (status == SettlementProvisionStatus.CONFLICT != diagnostic.isPresent()) throw new IllegalArgumentException("only provision conflict retains a diagnostic tuple");
        if (diagnostic.isPresent() && (diagnostic.orElseThrow().reason() != DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT
                || !diagnostic.orElseThrow().owner().id().equals(settlementId) || !diagnostic.orElseThrow().subject().id().equals(settlementId))) throw new IllegalArgumentException("provision conflict has a foreign diagnostic tuple");
    }
    @Override public String type() { return "frontier.settlement_provision_resolved"; }
    public SettlementProvisionResolved(SubjectId settlementId, SettlementProvisionStatus status) { this(settlementId, status, java.util.Optional.empty()); }
}
