package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.OptionalLong;

/** Exact cargo attempt identity; scene revision alone survives conflict re-preparation. */
final class FrontierV3CargoCarrierAuthority {
    private FrontierV3CargoCarrierAuthority() { }

    static OptionalLong currentEpoch(FencedRecoveryState recovery, SceneLease lease) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return OptionalLong.empty();
        var binding = recovery.current().get(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(
                FrontierSceneBehaviors.logistics(lease).cargoId()));
        return binding != null && binding.asset() == FencedRecoveryAsset.CARGO
                && binding.ownerId().equals(FrontierSceneLeaseStateSupport.recoveryOwner(lease))
                && binding.ownerRevision() == lease.revision()
                ? OptionalLong.of(binding.authorityEpoch()) : OptionalLong.empty();
    }

    static boolean matches(FencedRecoveryState recovery, SceneLease lease, long declaredEpoch) {
        if (declaredEpoch < 1 || !FrontierSceneBehaviors.isLogistics(lease)) return false;
        if (lease.status() != SceneLeaseStatus.CLOSED) {
            return currentEpoch(recovery, lease).stream().anyMatch(epoch -> epoch == declaredEpoch);
        }
        var retained = recovery.cargoRetirements().pending().get(CargoCarrierIdentity.id(lease));
        if (retained != null && retained.worldId().equals(lease.worldId())
                && retained.leaseId().equals(lease.id())
                && retained.cargoId().equals(FrontierSceneBehaviors.logistics(lease).cargoId())
                && retained.authorization().ownerRevision() == lease.revision()) {
            return retained.authorization().retiredEpoch() == declaredEpoch;
        }
        // Closed-projection cleanup needs exact retired evidence, not current authority
        // belonging to a successor. An absent/compacted tombstone grants no deletion.
        var tombstone = recovery.tombstones().get(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(
                FrontierSceneBehaviors.logistics(lease).cargoId()));
        return tombstone != null && tombstone.asset() == FencedRecoveryAsset.CARGO
                && tombstone.ownerId().equals(FrontierSceneLeaseStateSupport.recoveryOwner(lease))
                && tombstone.ownerRevision() == lease.revision() && tombstone.retiredEpoch() == declaredEpoch;
    }
}
