package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Canonical COLD completion of one field's initial preparation; projection is deliberately separate. */
public record ResourceSitePrepared(ResourceSitePreparationJob job) implements FrontierPayload {
    public ResourceSitePrepared { Objects.requireNonNull(job, "resource-site preparation job"); }
    @Override public String type() { return "frontier.resource_site_prepared"; }
}
