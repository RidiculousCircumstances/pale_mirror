package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable-before-effect admission for one exact cocoon opening. */
public record HiveMobilizationReleaseStarted(SubjectId mobilizationId) implements FrontierPayload {
    public HiveMobilizationReleaseStarted { Objects.requireNonNull(mobilizationId, "hive mobilization id"); }
    @Override public String type() { return "frontier.hive_mobilization_release_started"; }
}
