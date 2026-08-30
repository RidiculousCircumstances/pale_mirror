package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact COLD consumption of hive biomass when no owned physical store is active. */
public record HiveGrowthBiomassConsumed(SubjectId jobId, SubjectId itemId) implements FrontierPayload {
    public HiveGrowthBiomassConsumed { Objects.requireNonNull(jobId, "job id"); Objects.requireNonNull(itemId, "item id"); }
    @Override public String type() { return "frontier.hive_growth_biomass_consumed"; }
}
