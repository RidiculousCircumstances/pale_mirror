package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable plant-clock turn for one prepared field; work-stage fields fence the retained owner. */
public record ResourceSiteGrowthAdvanced(SubjectId siteId, long growthEpoch, int growthStage) implements FrontierPayload {
    public ResourceSiteGrowthAdvanced {
        Objects.requireNonNull(siteId, "resource-site growth site id");
        if (!siteId.value().startsWith("site:") || growthEpoch <= 0L || growthStage < 0 || growthStage > ResourceSiteLifecycle.MATURE_STAGE) {
            throw new IllegalArgumentException("resource-site growth transition is invalid");
        }
    }
    @Override public String type() { return "frontier.resource_site_growth_advanced"; }
}
