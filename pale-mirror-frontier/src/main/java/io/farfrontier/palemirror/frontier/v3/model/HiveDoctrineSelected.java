package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Persisted utility result before the hive admits a new durable operation graph. */
record HiveDoctrineSelected(HiveDoctrineState state) implements FrontierPayload {
    HiveDoctrineSelected { Objects.requireNonNull(state, "hive doctrine state"); }
    @Override public String type() { return "frontier.hive_doctrine_selected"; }
}
