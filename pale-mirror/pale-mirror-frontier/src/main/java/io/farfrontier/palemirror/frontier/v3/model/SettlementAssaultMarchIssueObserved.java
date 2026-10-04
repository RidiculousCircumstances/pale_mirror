package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A loaded scene's exact typed obstruction or owned-body observation. */
public record SettlementAssaultMarchIssueObserved(SubjectId assaultId, SceneLeaseId leaseId, long leaseRevision,
                                                  ExpeditionMarchStep predecessor,
                                                  io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup actuations,
                                                  ExpeditionMarchIssue issue,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public SettlementAssaultMarchIssueObserved {
        SettlementAssaultExecutionAuthority.requireOwner(executions, assaultId);
        assaultId = Objects.requireNonNull(assaultId, "assault id"); leaseId = Objects.requireNonNull(leaseId, "assault lease");
        issue = Objects.requireNonNull(issue, "expedition march issue");
        SettlementAssaultFormationObserved.requireCaptured(assaultId, leaseRevision, predecessor, actuations, executions);
        if (!predecessor.members().containsKey(issue.memberId())) throw new IllegalArgumentException("expedition issue changes its captured member");
    }
    @Override public String type() { return "frontier.settlement_assault_march_issue_observed"; }
}
