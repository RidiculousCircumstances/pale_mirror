package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

/** Durable assignment of one exact farmer and output slot to one mature field. */
public record ResourceSiteHarvestStarted(ResourceSiteHarvestJob job, Optional<PedestrianRouteReceipt> fieldRoute,
                                         Optional<PedestrianRouteReceipt> storageRoute) implements FrontierPayload {
    public ResourceSiteHarvestStarted {
        Objects.requireNonNull(job, "resource-site harvest job");
        Objects.requireNonNull(fieldRoute, "accepted field start route");
        Objects.requireNonNull(storageRoute, "accepted warehouse start route");
    }
    @Override public String type() { return "frontier.resource_site_harvest_started"; }
}
