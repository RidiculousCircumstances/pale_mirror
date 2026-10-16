package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Objects;

/** Requests reconsideration, never awards route acceptance, execution authority or arrival. */
public record PedestrianPlanningReady(ScheduledAction expected) implements FrontierPayload {
    public PedestrianPlanningReady { Objects.requireNonNull(expected); }
    public ScheduledAction continuationAt(io.farfrontier.palemirror.frontier.v3.api.SimInstant submittedAt) {
        return new ScheduledAction(expected.id(), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(
                Math.addExact(submittedAt.ticks(), 1L)), expected.priority(), expected.subject(), expected.kind(), expected.weight());
    }
    @Override public String type() { return "frontier.pedestrian_planning_ready"; }
}
