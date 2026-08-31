package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable preparation of a cargo-free HOT scene owned by one settlement assault. */
record SettlementAssaultSceneLeasePrepared(SceneLease lease) implements FrontierPayload {
    SettlementAssaultSceneLeasePrepared {
        Objects.requireNonNull(lease, "assault scene lease");
        if (!(lease.cause() instanceof SettlementAssaultSceneCause)) {
            throw new IllegalArgumentException("assault scene preparation requires an assault cause");
        }
    }
    @Override public String type() { return "frontier.settlement_assault_scene_lease_prepared"; }
}
