package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect preparation of the exact engineering work-site scene. */
public record EngineeringWorkSceneLeasePrepared(SceneLease lease) implements FrontierPayload, SceneLeaseAdmission {
    public EngineeringWorkSceneLeasePrepared {
        Objects.requireNonNull(lease, "engineering scene lease");
        if (!FrontierSceneBehaviors.isEngineeringWorksite(lease)) {
            throw new IllegalArgumentException("engineering scene preparation requires an engineering cause");
        }
    }
    @Override public String type() { return "frontier.engineering_work_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
