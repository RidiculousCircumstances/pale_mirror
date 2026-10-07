package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Objects;

/** Requests reconsideration, never awards route acceptance, execution authority or arrival. */
public record PedestrianPlanningReady(ScheduledAction expected) implements FrontierPayload {
    public PedestrianPlanningReady { Objects.requireNonNull(expected); }
    @Override public String type() { return "frontier.pedestrian_planning_ready"; }
}
