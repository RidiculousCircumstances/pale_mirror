package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact resident-owned settlement service work temporarily held by a HOT scene. */
public record SettlementServiceWorkSceneCause(SubjectId workId) implements SceneCause {
    public SettlementServiceWorkSceneCause {
        workId = Objects.requireNonNull(workId, "service-work scene id");
        if (!workId.value().startsWith("service:")) throw new IllegalArgumentException("service-work scene requires a settlement service work");
    }
    @Override public SceneCauseKind kind() { return SceneCauseKind.SERVICE_WORK; }
}
