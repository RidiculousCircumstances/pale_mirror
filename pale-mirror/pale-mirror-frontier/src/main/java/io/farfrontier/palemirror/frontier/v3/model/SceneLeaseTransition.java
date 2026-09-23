package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Objects;

/** Durable lifecycle transition for one exclusive scene lease. */
public record SceneLeaseTransition(SceneLeaseId leaseId, SceneLeaseStatus status) implements FrontierPayload {
    public SceneLeaseTransition {
        Objects.requireNonNull(leaseId, "scene lease id");
        Objects.requireNonNull(status, "scene lease status");
        if (status == SceneLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("scene closure requires its explicit release or recovery disposition");
        }
    }
    /** PREPARED creates no new lease here: only declared recovery edges may target it. */
    public boolean appliesTo(SceneLease lease) {
        return lease != null && lease.id().equals(leaseId) && lease.status().canTransitionTo(status);
    }
    @Override public String type() { return "frontier.scene_lease_transition"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
