package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Canonical COLD harvest result; a loaded field is later reconciled to this exact result. */
public record ResourceSiteHarvested(ResourceSiteHarvestJob job, ExactItemStack output) implements FrontierPayload {
    public ResourceSiteHarvested {
        Objects.requireNonNull(job, "resource-site harvest job");
        Objects.requireNonNull(output, "resource-site harvest output");
    }
    @Override public String type() { return "frontier.resource_site_harvested"; }
}
