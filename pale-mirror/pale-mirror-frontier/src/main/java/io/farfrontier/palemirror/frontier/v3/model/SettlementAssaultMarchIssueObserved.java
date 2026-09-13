package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A loaded scene's exact typed obstruction or owned-body observation. */
public record SettlementAssaultMarchIssueObserved(SubjectId assaultId, SceneLeaseId leaseId,
                                                  ExpeditionMarchIssue issue) implements FrontierPayload {
    public SettlementAssaultMarchIssueObserved {
        assaultId = Objects.requireNonNull(assaultId, "assault id"); leaseId = Objects.requireNonNull(leaseId, "assault lease");
        issue = Objects.requireNonNull(issue, "expedition march issue");
    }
    @Override public String type() { return "frontier.settlement_assault_march_issue_observed"; }
}
