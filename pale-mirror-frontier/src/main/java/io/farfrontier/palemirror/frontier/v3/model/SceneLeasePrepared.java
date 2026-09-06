package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect admission of an exclusive scene lease. */
public record SceneLeasePrepared(SceneLease lease) implements FrontierPayload, SceneLeaseAdmission {
    public SceneLeasePrepared { Objects.requireNonNull(lease, "scene lease"); }
    @Override public String type() { return "frontier.scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
