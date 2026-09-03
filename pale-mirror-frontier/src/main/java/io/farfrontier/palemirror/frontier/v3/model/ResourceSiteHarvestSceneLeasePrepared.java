package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect admission of the exact assigned farmer at one retained field cursor. */
public record ResourceSiteHarvestSceneLeasePrepared(SceneLease lease) implements FrontierPayload {
    public ResourceSiteHarvestSceneLeasePrepared {
        Objects.requireNonNull(lease, "resource-site harvest scene lease");
        if (!FrontierSceneBehaviors.isResourceSiteHarvest(lease)) {
            throw new IllegalArgumentException("resource-site harvest scene preparation requires its typed cause");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
