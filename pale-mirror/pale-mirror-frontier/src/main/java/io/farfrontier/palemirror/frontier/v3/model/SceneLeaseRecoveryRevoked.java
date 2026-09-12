package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Objects;

/**
 * No-visit recovery decision for a lease whose only unfinished projections are reversible.
 *
 * <p>This is not a synthetic loaded-world observation: it retires the old projection fence
 * and leaves the retained canonical COLD checkpoint in place. A future natural entity load can
 * therefore only be rejected as stale.</p>
 */
public record SceneLeaseRecoveryRevoked(SceneLeaseId leaseId) implements FrontierPayload {
    public SceneLeaseRecoveryRevoked { Objects.requireNonNull(leaseId, "scene recovery revoke lease"); }
    @Override public String type() { return "frontier.scene_lease_recovery_revoked"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
