package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit lifecycle transition; scene ownership is introduced only in a later HOT slice. */
public record SettlementAssaultTransition(SubjectId assaultId, SettlementAssaultStatus status, java.util.Optional<DiagnosticTuple> diagnostic) implements FrontierPayload {
    public SettlementAssaultTransition {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(status, "settlement assault status");
        diagnostic = java.util.Objects.requireNonNull(diagnostic, "assault diagnostic");
        if (status == SettlementAssaultStatus.CONFLICT != diagnostic.isPresent()) throw new IllegalArgumentException("only assault conflict retains a diagnostic tuple");
        if (diagnostic.isPresent() && (diagnostic.orElseThrow().reason() != DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT
                || !diagnostic.orElseThrow().owner().id().equals(assaultId) || !diagnostic.orElseThrow().subject().id().equals(assaultId))) throw new IllegalArgumentException("assault conflict has a foreign diagnostic tuple");
    }
    @Override public String type() { return "frontier.settlement_assault_transition"; }
    public SettlementAssaultTransition(SubjectId assaultId, SettlementAssaultStatus status) { this(assaultId, status, java.util.Optional.empty()); }
}
