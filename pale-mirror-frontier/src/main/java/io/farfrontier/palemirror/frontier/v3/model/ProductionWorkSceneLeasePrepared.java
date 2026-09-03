package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable-before-effect admission for the exact worker of one production job. */
public record ProductionWorkSceneLeasePrepared(SceneLease lease) implements FrontierPayload {
    public ProductionWorkSceneLeasePrepared {
        Objects.requireNonNull(lease, "production-work lease");
        if (!FrontierSceneBehaviors.isProductionWork(lease)) throw new IllegalArgumentException("production-work lease requires its typed cause");
    }
    @Override public String type() { return "frontier.production_work_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
