package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import java.util.Objects;
import java.util.Optional;

/** Saved physical departure: supported position and physical absence commit atomically. */
public record ActorBodyUnloaded(ActorBodyId body, BodyPosition expectedBody, FixedScalar expectedHealth,
                                BodyPosition observedBody, FixedScalar observedHealth,
                                Optional<ActorExecutionId> expectedExecution) implements FrontierPayload {
    public ActorBodyUnloaded {
        Objects.requireNonNull(body); Objects.requireNonNull(expectedBody); Objects.requireNonNull(expectedHealth);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(observedHealth); Objects.requireNonNull(expectedExecution);
        if (expectedHealth.compareTo(FixedScalar.ZERO) <= 0 || observedHealth.compareTo(FixedScalar.ZERO) <= 0
                || expectedExecution.filter(id -> !id.actorId().equals(body.actorId())).isPresent())
            throw new IllegalArgumentException("body unload requires one exact living actor");
    }
    @Override public String type() { return "frontier.actor_body_unloaded"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
