package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;

/** Exact durable continuation identity shared by the group owner and its declared capabilities. */
public final class UnitGroupContinuation {
    public static final String PROGRESS = "frontier.unit_group.progress";
    private UnitGroupContinuation() { }
    public static ScheduledAction at(SubjectId group, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:unit-group/" + group.value().replace(':', '/')),
                new SimInstant(tick), 12, group, PROGRESS, 1);
    }
    public static ProposedEvent wake(SubjectId group, long tick) {
        var action = at(group, tick + 1);
        return new ProposedEvent(group, new ScheduleEffect.Rescheduled(action.id(), action));
    }
}
