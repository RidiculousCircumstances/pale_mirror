package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStrikeReceiptBinding;

/** Exact, non-causal identity binding for one physical settlement-assault receipt. */
final class FrontierV3SettlementAssaultReceiptBinding {
    private FrontierV3SettlementAssaultReceiptBinding() { }

    static PhysicalIntentId intentId(FrontierWorldState state, SceneLease lease, SubjectId cause) {
        return SettlementAssaultStrikeReceiptBinding.intentId(state, lease, cause);
    }

    /**
     * Content-addresses a framed tuple rather than concatenating rewritten identifiers.  The
     * lease id is deliberately included: a revision alone is not an ownership identity.
     */
    static PhysicalIntentId intentId(WorldId world, SubjectId cause, SceneLeaseId leaseId, long revision) {
        return SettlementAssaultStrikeReceiptBinding.intentId(world, cause, leaseId, revision);
    }

    static boolean belongsToLease(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        return SettlementAssaultStrikeReceiptBinding.belongsToLease(state, lease, intent);
    }
}
