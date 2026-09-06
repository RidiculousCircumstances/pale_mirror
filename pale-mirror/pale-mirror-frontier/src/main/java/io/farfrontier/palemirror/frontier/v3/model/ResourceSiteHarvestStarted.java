package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable assignment of one exact farmer and output slot to one mature field. */
public record ResourceSiteHarvestStarted(ResourceSiteHarvestJob job) implements FrontierPayload {
    public ResourceSiteHarvestStarted { Objects.requireNonNull(job, "resource-site harvest job"); }
    @Override public String type() { return "frontier.resource_site_harvest_started"; }
}
