package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;

/** Read-only exact-tombstone guard for naturally returned closed scene projections. */
final class FrontierV3ClosedProjectionFence {
    private FrontierV3ClosedProjectionFence() { }


    private static boolean rejects(FrontierWorldState state, SubjectId bindingId, FencedRecoveryAsset asset, SceneLease lease,
                                   boolean rejectMissingTombstone) {
        var tombstone = state.fencedRecovery().tombstones().get(bindingId);
        if (tombstone == null) return rejectMissingTombstone;
        return state.fencedRecovery().lateLoad(bindingId, asset,
                FrontierSceneLeaseStateSupport.recoveryOwner(lease), tombstone.retiredEpoch()) == FencedRecoveryDisposition.REJECT_STALE;
    }
}
