package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import java.util.Objects;
import java.util.Optional;

/** Physical fatality of one exact incarnation, not a scene or activity completion. */
public record ActorBodyDied(ActorBodyId body, BodyPosition expectedBody, FixedScalar expectedHealth,
                            Optional<BodyPosition> observedBody, Optional<ActorExecutionId> expectedExecution,
                            String cause) implements FrontierPayload {
    public ActorBodyDied {
        Objects.requireNonNull(body); Objects.requireNonNull(expectedBody); Objects.requireNonNull(expectedHealth);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(expectedExecution); Objects.requireNonNull(cause);
        if (expectedHealth.compareTo(FixedScalar.ZERO) <= 0 || cause.isBlank() || cause.length() > 512
                || expectedExecution.filter(id -> !id.actorId().equals(body.actorId())).isPresent())
            throw new IllegalArgumentException("body death requires one exact living actor and a bounded cause");
    }
    // No supporting surface may exist at an airborne fatality. Empty observation explicitly
    // retains the last known supported pose; it does not invent a floor or certify an arrival.
    @Override public String type() { return "frontier.actor_body_died"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
