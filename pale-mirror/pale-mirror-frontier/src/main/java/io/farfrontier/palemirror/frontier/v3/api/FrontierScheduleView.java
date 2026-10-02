package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.List;

/** Immutable execution metadata, independent of encoded persistence snapshots. */
public interface FrontierScheduleView {
    WorldId worldId();
    Revision revision();
    SimInstant instant();
    List<ScheduledAction> schedules();
}
