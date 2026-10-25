package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;

/** Sparse registered semantic continuation, not a per-tree ticker. */
public final class ForestryContinuation {
    public static final String PROGRESS = "frontier.forestry.progress";
    public static final String REVIEW = "frontier.forestry.review";
    private ForestryContinuation() { }
    public static ScheduledAction progress(SubjectId job, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:forestry-progress/" + job.value().replace(':', '/')), new SimInstant(tick), 12, job, PROGRESS, 1);
    }
    public static ScheduledAction review(SubjectId home, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:forestry-review/" + home.value().replace(':', '/')), new SimInstant(tick), 13, home, REVIEW, 1);
    }
    public static ProposedEvent wake(SubjectId job, long tick) {
        var next = progress(job, Math.addExact(tick, 1)); return new ProposedEvent(job, new ScheduleEffect.Rescheduled(next.id(), next));
    }
}
