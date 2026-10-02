package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.List;
import java.util.Objects;

/** A current owning-thread schedule image; never serializes the canonical world. */
public record FrontierExecutionView(WorldId worldId, Revision revision, SimInstant instant,
                                    List<ScheduledAction> schedules) implements FrontierScheduleView {
    public FrontierExecutionView {
        Objects.requireNonNull(worldId); Objects.requireNonNull(revision); Objects.requireNonNull(instant);
        schedules = List.copyOf(schedules);
    }
}
