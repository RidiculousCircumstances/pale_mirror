package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable preparation of a cargo-free HOT scene owned by one settlement assault. */
public record SettlementAssaultSceneLeasePrepared(SceneLease lease) implements FrontierPayload {
    public SettlementAssaultSceneLeasePrepared {
        Objects.requireNonNull(lease, "assault scene lease");
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) {
            throw new IllegalArgumentException("assault scene preparation requires an assault cause");
        }
    }
    @Override public String type() { return "frontier.settlement_assault_scene_lease_prepared"; }
}
