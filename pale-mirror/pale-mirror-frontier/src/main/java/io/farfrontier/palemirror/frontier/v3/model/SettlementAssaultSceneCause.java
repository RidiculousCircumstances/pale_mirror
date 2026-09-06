package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Reserved typed binding for a settlement assault. Its validator arrives with the assault state machine. */
public record SettlementAssaultSceneCause(SubjectId assaultId, SubjectId settlementId) implements SceneCause {
    public SettlementAssaultSceneCause {
        Objects.requireNonNull(assaultId, "assault id");
        Objects.requireNonNull(settlementId, "assault settlement");
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.SETTLEMENT_ASSAULT; }
}
