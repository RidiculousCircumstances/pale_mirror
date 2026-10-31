package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
/** Owner-addressed guard work, not a per-tick actor poll. */
public final class GuardContinuation {
    public static final String REVIEW = "frontier.guard.review", PROGRESS = "frontier.guard.progress";
    public static ScheduledAction review(SubjectId home, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:guard-review-" + home.value().replace(':', '-')),
                new SimInstant(tick), 1, home, REVIEW, 1);
    }
    public static ScheduledAction progress(SubjectId duty, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:guard-progress-" + duty.value().replace(':', '-')),
                new SimInstant(tick), 1, duty, PROGRESS, 1);
    }
    public static ProposedEvent wake(SubjectId duty, long tick) {
        return new ProposedEvent(duty, new ScheduleEffect.ReconsiderationRequested(progress(duty, tick + 1)));
    }
    private GuardContinuation() { }
}
