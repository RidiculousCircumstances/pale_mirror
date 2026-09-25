package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Objects;

/**
 * Legacy no-visit recovery event retained solely for replaying existing WAL.
 *
 * <p>New commands are rejected: a scene body can retain uncheckpointed injury, so absence
 * of demand is not proof that closing its projection preserves physical consequences.</p>
 */
public record SceneLeaseRecoveryRevoked(SceneLeaseId leaseId) implements FrontierPayload {
    public SceneLeaseRecoveryRevoked { Objects.requireNonNull(leaseId, "scene recovery revoke lease"); }
    @Override public String type() { return "frontier.scene_lease_recovery_revoked"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
