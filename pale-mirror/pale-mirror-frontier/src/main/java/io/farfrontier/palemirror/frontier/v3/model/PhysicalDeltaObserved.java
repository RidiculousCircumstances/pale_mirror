package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Immutable observation of a real block loss or an unowned physical scar. */
public record PhysicalDeltaObserved(PhysicalDelta delta) implements FrontierPayload {
    public PhysicalDeltaObserved {
        Objects.requireNonNull(delta, "physical delta");
    }

    @Override public String type() { return "frontier.physical_delta_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
