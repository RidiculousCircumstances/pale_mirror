package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.List;
import java.util.Objects;

/** Durable transfer of the observed ambient worker body into its workshop scene. */
public record ProductionWorkSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload, SceneLeaseAdmission {
    public ProductionWorkSceneLeaseHandoff {
        Objects.requireNonNull(lease, "production-work lease"); ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "production-work ambient members"));
        if (!FrontierSceneBehaviors.isProductionWork(lease)) throw new IllegalArgumentException("production-work hand-off requires its typed cause");
    }
    @Override public String type() { return "frontier.production_work_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
