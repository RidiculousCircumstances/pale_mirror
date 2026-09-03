package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Exact ambient-to-field-work transfer; it preserves the observed farmer body and never respawns it. */
public record ResourceSiteHarvestSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload {
    public ResourceSiteHarvestSceneLeaseHandoff {
        Objects.requireNonNull(lease, "resource-site harvest scene lease");
        ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "resource-site harvest ambient members"));
        if (!FrontierSceneBehaviors.isResourceSiteHarvest(lease)) {
            throw new IllegalArgumentException("resource-site harvest hand-off requires a field-work cause");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
