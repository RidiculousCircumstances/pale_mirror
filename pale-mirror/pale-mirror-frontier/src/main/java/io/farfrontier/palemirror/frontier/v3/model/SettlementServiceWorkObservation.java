package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Captured service predecessor fences receipts from another leg of the same execution. */
public record SettlementServiceWorkObservation(ActorHotObservation authority, SettlementServiceWorkPhase phase,
                                                int inputCursor, int workCursor, int completedWorkTicks) {
    public SettlementServiceWorkObservation {
        Objects.requireNonNull(authority, "service physical authority");
        Objects.requireNonNull(phase, "service observed phase");
        if (authority.actuation().execution().activityKind() != ActorActivityKind.SETTLEMENT_SERVICE
                || inputCursor < 0 || workCursor < 0 || completedWorkTicks < 0
                || completedWorkTicks > SettlementServiceWork.REQUIRED_WORK_TICKS)
            throw new IllegalArgumentException("service observation has foreign kind or invalid predecessor");
    }
    public void requireOwner(SubjectId workId) {
        SettlementServiceExecutionAuthority.requireDeclaration(authority.actuation().execution(), workId);
    }
    public void require(FrontierWorldState state, SettlementServiceWork work, SceneLease lease, BodyPosition observed) {
        requireOwner(work.id());
        SettlementServiceExecutionAuthority.requireCurrent(state, work, authority.actuation().execution());
        if (phase != work.phase() || inputCursor != work.inputTraversalCursor()
                || workCursor != work.workTraversalCursor() || completedWorkTicks != work.completedWorkTicks())
            throw new IllegalArgumentException("service observation has stale leg, cursor or labour predecessor");
        authority.require(state, authority.actuation().execution(), lease.revision(), observed);
    }
}
