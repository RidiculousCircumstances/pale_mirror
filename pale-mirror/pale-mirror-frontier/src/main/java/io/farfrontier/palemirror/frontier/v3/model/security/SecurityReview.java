package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
/** One addressed, coalesced allocation/expiry review, not a per-actor poll. */
public final class SecurityReview {
    public static final String KIND="frontier.security.review";
    public static ScheduledAction at(SubjectId home,long tick) {
        return new ScheduledAction(new ScheduleId("schedule:security-review-"+home.value().replace(':','-')),
                new SimInstant(tick),2,home,KIND,1);
    }
    public static ProposedEvent wake(SubjectId home,long tick) {
        return new ProposedEvent(home,new ScheduleEffect.ReconsiderationRequested(at(home,tick+1)));
    }
    private SecurityReview() { }
}
