package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;

/** Captured first service exit permits consumption, never a return-to-origin prerequisite. */
public record ResidentMealHotAccessCleared(SubjectId residentId, long ambientRevision,
                                           BodyPosition observedBody, ActorHotObservation observation) implements FrontierPayload {
    public ResidentMealHotAccessCleared {
        Objects.requireNonNull(residentId, "service resident");
        Objects.requireNonNull(observation, "captured meal clearance authority");
        if (!residentId.equals(observation.actuation().execution().actorId())
                || ambientRevision != observation.scopeRevision())
            throw new IllegalArgumentException("meal clearance has foreign actor or scope");
        Objects.requireNonNull(observedBody, "service exit body");
        if (ambientRevision < 1) throw new IllegalArgumentException("service exit needs a HOT lease revision");
    }

    @Override public String type() { return "frontier.resident_meal_hot_access_cleared"; }
    public ActorExecutionId executionId() { return observation.actuation().execution(); }
}
