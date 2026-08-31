package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Resolves a scene's durable owner without leaking cargo assumptions into other scene families. */
final class FrontierSceneOwnerSupport {
    private FrontierSceneOwnerSupport() { }

    static SubjectId owner(FrontierWorldState state, SceneLease lease) {
        if (lease.cause() instanceof SettlementAssaultSceneCause) return FrontierSettlementAssaultSceneSupport.owner(state, lease);
        RouteOperation operation = state.operations().get(lease.operationId());
        if (operation == null) throw new IllegalArgumentException("scene lease has no owning operation");
        return operation.settlementId();
    }

    static boolean isAssault(SceneLease lease) { return lease.cause() instanceof SettlementAssaultSceneCause; }
}
