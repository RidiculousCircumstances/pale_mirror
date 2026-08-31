package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Atomic ambient-to-assault scene capture; it intentionally carries no logistics cargo. */
public record SettlementAssaultSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload {
    public SettlementAssaultSceneLeaseHandoff {
        Objects.requireNonNull(lease, "assault scene lease");
        ambientMembers = List.copyOf(ambientMembers);
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) {
            throw new IllegalArgumentException("assault scene hand-off requires an assault cause");
        }
    }
    @Override public String type() { return "frontier.settlement_assault_scene_lease_handoff"; }
}
