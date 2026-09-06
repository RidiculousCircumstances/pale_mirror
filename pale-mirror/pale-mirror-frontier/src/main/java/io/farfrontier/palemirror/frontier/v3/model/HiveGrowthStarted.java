package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable fact that the named exact biomass stack was consumed for one hive growth job. */
public record HiveGrowthStarted(HiveGrowthJob job) implements FrontierPayload {
    public HiveGrowthStarted { Objects.requireNonNull(job, "hive growth job"); }
    @Override public String type() { return "frontier.hive_growth_started"; }
}
