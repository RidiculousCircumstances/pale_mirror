package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one field preparation before a loaded-chunk executor may touch its cells. */
public record ResourceSitePreparationStarted(ResourceSitePreparationJob job) implements FrontierPayload {
    public ResourceSitePreparationStarted { Objects.requireNonNull(job, "resource-site preparation job"); }
    @Override public String type() { return "frontier.resource_site_preparation_started"; }
}
