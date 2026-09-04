package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Atomic canonical admission of one exact resident-owned settlement service work.
 *
 * <p>The work and both already-declared physical boundaries become durable in the same event.
 * This deliberately prevents the former decontamination shortcut from reserving a reagent or
 * creating an endpoint before an exact worker, source station and retained traversal exist.</p>
 */
public record SettlementServiceWorkStarted(SubjectId taskId, SettlementServiceWork work,
                                           PhysicalIntent inputIssueIntent, PhysicalIntent endpointIntent) implements FrontierPayload {
    public SettlementServiceWorkStarted {
        taskId = Objects.requireNonNull(taskId, "service work task");
        work = Objects.requireNonNull(work, "service work");
        inputIssueIntent = Objects.requireNonNull(inputIssueIntent, "service input issue intent");
        endpointIntent = Objects.requireNonNull(endpointIntent, "service endpoint intent");
        if (!taskId.value().startsWith("task:") || !inputIssueIntent.id().equals(work.inputIssueIntentId())
                || !endpointIntent.id().equals(work.endpointIntentId())) {
            throw new IllegalArgumentException("service-work admission must retain one task and both exact intents");
        }
    }

    @Override public String type() { return "frontier.settlement_service_work_started"; }
}
