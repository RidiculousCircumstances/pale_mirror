package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;

/** Read-only exact-tombstone guard for naturally returned closed scene projections. */
final class FrontierV3ClosedProjectionFence {
    private FrontierV3ClosedProjectionFence() { }

    static boolean bodyIsStale(FrontierWorldState state, SceneLease lease, SceneMember member) {
        // A body also carries exact immutable actor/lease tags at its caller. A compacted body
        // tombstone therefore remains fail-closed and may be discarded, never re-admitted.
        return rejects(state, FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId()), FencedRecoveryAsset.BODY, lease, true);
    }

    static boolean cargoIsStale(FrontierWorldState state, SceneLease lease) {
        return rejects(state, FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(FrontierSceneBehaviors.logistics(lease).cargoId()),
                FencedRecoveryAsset.CARGO, lease, false);
    }

    private static boolean rejects(FrontierWorldState state, SubjectId bindingId, FencedRecoveryAsset asset, SceneLease lease,
                                   boolean rejectMissingTombstone) {
        var tombstone = state.fencedRecovery().tombstones().get(bindingId);
        if (tombstone == null) return rejectMissingTombstone;
        return state.fencedRecovery().lateLoad(bindingId, asset,
                FrontierSceneLeaseStateSupport.recoveryOwner(lease), tombstone.retiredEpoch()) == FencedRecoveryDisposition.REJECT_STALE;
    }
}
