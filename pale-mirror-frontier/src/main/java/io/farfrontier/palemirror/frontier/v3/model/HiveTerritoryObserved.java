package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** A local biological sensor has observed one nonzero infection cell. */
record HiveTerritoryObserved(HiveTerritoryKnowledge.Belief belief) implements FrontierPayload {
    HiveTerritoryObserved { Objects.requireNonNull(belief, "hive territory belief"); }
    @Override public String type() { return "frontier.hive_territory_observed"; }
}
