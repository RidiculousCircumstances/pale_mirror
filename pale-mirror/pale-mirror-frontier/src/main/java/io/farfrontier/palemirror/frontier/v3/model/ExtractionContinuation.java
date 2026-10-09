package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;

/** Family-owned wake addresses shared by movement, labour and UAE resumption. */
public final class ExtractionContinuation {
    public static final String PROGRESS = "frontier.extraction.progress";
    public static final String REVIEW = "frontier.extraction.review";
    private ExtractionContinuation() { }
    public static ScheduledAction at(SubjectId job, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:extraction/work/" + job.value().replace(':', '/')),
                new SimInstant(tick), 12, job, PROGRESS, 1);
    }
    public static ScheduledAction review(SubjectId site, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:extraction/site/" + site.value().replace(':', '/')),
                new SimInstant(tick), 13, site, REVIEW, 1);
    }
    public static ProposedEvent wake(SubjectId job, long tick) {
        var action = at(job, Math.addExact(tick, 1));
        return new ProposedEvent(job, new ScheduleEffect.Rescheduled(action.id(), action));
    }
}
