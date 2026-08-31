package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** One exact Scout has locally observed a named settlement; it is not global target discovery. */
record HiveSettlementObserved(HiveSettlementKnowledge.Sighting sighting) implements FrontierPayload {
    HiveSettlementObserved { Objects.requireNonNull(sighting, "hive settlement sighting"); }
    @Override public String type() { return "frontier.hive_settlement_observed"; }
}
