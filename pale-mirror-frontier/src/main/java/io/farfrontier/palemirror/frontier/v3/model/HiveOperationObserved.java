package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** A single exact Scout saw one en-route caravan at its current position. */
record HiveOperationObserved(HiveOperationKnowledge.Sighting sighting) implements FrontierPayload {
    HiveOperationObserved { Objects.requireNonNull(sighting, "hive operation sighting"); }
    @Override public String type() { return "frontier.hive_operation_observed"; }
}
