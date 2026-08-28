package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.Objects;
import java.util.Optional;

/** Durable state change after executor admission, postcondition observation or restart inspection. */
public record PhysicalIntentTransition(PhysicalIntentId intentId, PhysicalIntentStatus status,
                                       Optional<CargoHandoffObservation> observation) implements FrontierPayload {
    public PhysicalIntentTransition {
        Objects.requireNonNull(intentId, "physical intent id");
        Objects.requireNonNull(status, "physical intent status");
        observation = Objects.requireNonNull(observation, "physical observation");
        if (status == PhysicalIntentStatus.PREPARED) throw new IllegalArgumentException("physical intent cannot transition to prepared");
        if (status == PhysicalIntentStatus.CONFIRMED != observation.isPresent()) throw new IllegalArgumentException("only confirmed transition has observation evidence");
    }
    @Override public String type() { return "frontier.physical_intent_transition"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
