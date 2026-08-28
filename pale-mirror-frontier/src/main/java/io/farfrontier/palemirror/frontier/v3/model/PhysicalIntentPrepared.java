package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.Objects;

/** Durable admission of one physical effect or custody hand-off before an executor may see it. */
public record PhysicalIntentPrepared(PhysicalIntent intent) implements FrontierPayload {
    public PhysicalIntentPrepared {
        Objects.requireNonNull(intent, "physical intent");
        if (intent.status() != PhysicalIntentStatus.PREPARED) {
            throw new IllegalArgumentException("new physical intent must begin prepared");
        }
    }

    @Override public String type() { return "frontier.physical_intent_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
