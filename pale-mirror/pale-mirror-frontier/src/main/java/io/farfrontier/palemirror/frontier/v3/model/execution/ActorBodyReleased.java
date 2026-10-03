package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Exact physical absence after retained departure/removal evidence, not an activity outcome. */
public record ActorBodyReleased(ActorBodyId body) implements FrontierPayload {
    public ActorBodyReleased { Objects.requireNonNull(body, "released incarnation"); }
    @Override public String type() { return "frontier.actor_body_released"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
