package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;

/** One transport owner schedule; notifications never inspect or mutate the queue. */
public final class TransportMissionContinuation {
    public static final String PROGRESS = "frontier.transport_mission.progress";
    private TransportMissionContinuation() { }
    public static ScheduledAction at(SubjectId mission, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:transport-mission/" + mission.value().replace(':', '/')),
                new SimInstant(tick), 12, mission, PROGRESS, 1);
    }
    public static ProposedEvent wake(SubjectId mission, long tick) {
        var action = at(mission, tick + 1);
        return new ProposedEvent(mission, new ScheduleEffect.Rescheduled(action.id(), action));
    }
}
