package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect admission of the retained service worker into one typed scene. */
public record SettlementServiceWorkSceneLeasePrepared(SceneLease lease) implements FrontierPayload, SceneLeaseAdmission {
    public SettlementServiceWorkSceneLeasePrepared {
        Objects.requireNonNull(lease, "service-work scene lease");
        if (!FrontierSceneBehaviors.isServiceWork(lease)) throw new IllegalArgumentException("service-work lease requires its typed cause");
    }
    @Override public String type() { return "frontier.settlement_service_work_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
