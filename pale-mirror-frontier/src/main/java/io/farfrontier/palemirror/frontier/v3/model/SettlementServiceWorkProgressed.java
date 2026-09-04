package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed bounded work progress at the retained service-work station. */
public record SettlementServiceWorkProgressed(SubjectId workId, SceneLeaseId leaseId, BodyPosition observedWorker,
                                              SettlementServiceWorkPhase nextPhase, int completedWorkTicks) implements FrontierPayload {
    public SettlementServiceWorkProgressed {
        Objects.requireNonNull(workId, "service-work progress work");
        Objects.requireNonNull(leaseId, "service-work progress lease");
        Objects.requireNonNull(observedWorker, "service-work observed worker");
        Objects.requireNonNull(nextPhase, "service-work next phase");
        if (completedWorkTicks < 0 || completedWorkTicks > SettlementServiceWork.REQUIRED_WORK_TICKS) {
            throw new IllegalArgumentException("service-work progress is outside its retained bound");
        }
    }
    @Override public String type() { return "frontier.settlement_service_work_progressed"; }
}
