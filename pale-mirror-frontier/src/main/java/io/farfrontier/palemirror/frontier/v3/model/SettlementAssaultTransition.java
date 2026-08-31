package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit lifecycle transition; scene ownership is introduced only in a later HOT slice. */
record SettlementAssaultTransition(SubjectId assaultId, SettlementAssaultStatus status) implements FrontierPayload {
    SettlementAssaultTransition {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(status, "settlement assault status");
    }
    @Override public String type() { return "frontier.settlement_assault_transition"; }
}
