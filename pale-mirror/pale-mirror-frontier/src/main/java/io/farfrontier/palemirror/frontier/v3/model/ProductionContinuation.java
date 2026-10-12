package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

/** One production-owned completion identity, shared by ordinary scheduling and capability wakes. */
public final class ProductionContinuation {
    public static final String KIND = "frontier.settlement.production.task.complete";
    private ProductionContinuation() { }
    public static ScheduledAction at(ProductionJob job, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:production-task-complete-"
                + job.id().value().substring("job:".length())), new SimInstant(tick), 0, job.id(), KIND, 1);
    }
}
