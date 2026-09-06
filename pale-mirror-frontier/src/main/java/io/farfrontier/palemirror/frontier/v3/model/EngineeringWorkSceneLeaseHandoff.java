package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Exact ambient-to-scene hand-off for the retained engineering crew. */
public record EngineeringWorkSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload, SceneLeaseAdmission {
    public EngineeringWorkSceneLeaseHandoff {
        Objects.requireNonNull(lease, "engineering scene lease");
        ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "engineering scene ambient members"));
        if (!FrontierSceneBehaviors.isEngineeringWorksite(lease)) {
            throw new IllegalArgumentException("engineering scene hand-off requires an engineering cause");
        }
    }
    @Override public String type() { return "frontier.engineering_work_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
