package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable selection of exact dormant bioforms for one current hive task. */
public record HiveMobilizationStarted(HiveMobilization mobilization) implements FrontierPayload {
    public HiveMobilizationStarted { Objects.requireNonNull(mobilization, "hive mobilization"); }
    @Override public String type() { return "frontier.hive_mobilization_started"; }
}
