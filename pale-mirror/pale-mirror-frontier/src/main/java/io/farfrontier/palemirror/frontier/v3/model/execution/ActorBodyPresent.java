package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import java.util.Objects;

/** Exact physical admission/inspection, independent of activity or scene readiness. */
public record ActorBodyPresent(ActorBodyId body, BodyPosition expectedBody, FixedScalar expectedHealth,
                               BodyPosition observedBody, FixedScalar observedHealth) implements FrontierPayload {
    public ActorBodyPresent {
        Objects.requireNonNull(body); Objects.requireNonNull(expectedBody); Objects.requireNonNull(expectedHealth);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(observedHealth);
        if (expectedHealth.compareTo(FixedScalar.ZERO) <= 0 || observedHealth.compareTo(FixedScalar.ZERO) <= 0)
            throw new IllegalArgumentException("body presence requires one living physical actor");
    }
    @Override public String type() { return "frontier.actor_body_present"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
