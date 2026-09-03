package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact conflict outcome for a physical cocoon release; it never authorizes a replacement body. */
public record HiveMobilizationConflicted(SubjectId mobilizationId, HiveMobilizationConflictReason reason) implements FrontierPayload {
    public HiveMobilizationConflicted {
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(reason, "hive mobilization conflict reason");
    }
    @Override public String type() { return "frontier.hive_mobilization_conflicted"; }
}
