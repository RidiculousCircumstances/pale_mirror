package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;

/** One physically observed complete expedition edge; partial arrival has no canonical effect. */
public record SettlementAssaultFormationObserved(SubjectId assaultId, SceneLeaseId leaseId,
                                                 Map<SubjectId, BodyPosition> bodies,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultFormationObserved {
        SettlementAssaultExecutionAuthority.requireOwner(executions, assaultId);
        assaultId = Objects.requireNonNull(assaultId, "assault id"); leaseId = Objects.requireNonNull(leaseId, "assault lease");
        bodies = Map.copyOf(Objects.requireNonNull(bodies, "expedition bodies"));
        if (bodies.size() < 2 || bodies.values().stream().distinct().count() != bodies.size()) {
            throw new IllegalArgumentException("expedition observation requires a distinct complete formation");
        }
    }
    @Override public String type() { return "frontier.settlement_assault_formation_observed"; }
}
