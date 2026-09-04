package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Durable capture of the exact ambient service worker body by its retained scene. */
public record SettlementServiceWorkSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload {
    public SettlementServiceWorkSceneLeaseHandoff {
        Objects.requireNonNull(lease, "service-work scene lease");
        ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "service-work ambient members"));
        if (!FrontierSceneBehaviors.isServiceWork(lease)) throw new IllegalArgumentException("service-work hand-off requires its typed cause");
    }
    @Override public String type() { return "frontier.settlement_service_work_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
