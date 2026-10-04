package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;

/** One physically observed complete expedition edge; partial arrival has no canonical effect. */
public record SettlementAssaultFormationObserved(SubjectId assaultId, SceneLeaseId leaseId, long leaseRevision,
                                                 ExpeditionMarchStep predecessor,
                                                 io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup actuations,
                                                 Map<SubjectId, BodyPosition> bodies,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultFormationObserved {
        SettlementAssaultExecutionAuthority.requireOwner(executions, assaultId);
        assaultId = Objects.requireNonNull(assaultId, "assault id"); leaseId = Objects.requireNonNull(leaseId, "assault lease");
        bodies = Map.copyOf(Objects.requireNonNull(bodies, "expedition bodies"));
        requireCaptured(assaultId, leaseRevision, predecessor, actuations, executions);
        if (!predecessor.members().keySet().equals(bodies.keySet())) throw new IllegalArgumentException("expedition observation changes its captured cohort");
        if (bodies.size() < 2 || bodies.values().stream().distinct().count() != bodies.size()) {
            throw new IllegalArgumentException("expedition observation requires a distinct complete formation");
        }
    }
    static void requireCaptured(SubjectId owner, long revision, ExpeditionMarchStep step,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup actuations,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) {
        if (revision < 0L) throw new IllegalArgumentException("expedition scope revision is invalid");
        Objects.requireNonNull(actuations, "expedition captured bodies").executions().requireDeclaration(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SETTLEMENT_ASSAULT, owner,
                Objects.requireNonNull(step, "expedition spatial predecessor").members().keySet());
        for (var actuation : actuations.members()) if (!actuation.execution().equals(executions.requireMember(actuation.body().actorId())))
            throw new IllegalArgumentException("expedition body belongs to another captured execution");
    }
    @Override public String type() { return "frontier.settlement_assault_formation_observed"; }
}
