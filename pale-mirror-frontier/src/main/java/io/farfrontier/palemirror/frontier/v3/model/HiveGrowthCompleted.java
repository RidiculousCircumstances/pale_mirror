package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable fact that the one named hive growth job completed without generating substitute IDs. */
public record HiveGrowthCompleted(SubjectId jobId) implements FrontierPayload {
    public HiveGrowthCompleted { Objects.requireNonNull(jobId, "hive growth job id"); }
    @Override public String type() { return "frontier.hive_growth_completed"; }
}
