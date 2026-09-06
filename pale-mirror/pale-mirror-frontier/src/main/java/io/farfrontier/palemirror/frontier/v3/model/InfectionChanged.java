package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Canonical sparse-cell infection mutation; materialization later observes this exact fact. */
public record InfectionChanged(InfectionCell cell, FixedRatio intensity) implements FrontierPayload {
    public InfectionChanged { Objects.requireNonNull(cell, "cell"); Objects.requireNonNull(intensity, "intensity"); }
    @Override public String type() { return "frontier.infection_changed"; }
}
