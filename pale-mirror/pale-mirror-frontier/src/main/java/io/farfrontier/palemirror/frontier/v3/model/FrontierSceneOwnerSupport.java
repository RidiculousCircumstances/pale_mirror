package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Resolves a scene's durable owner without leaking cargo assumptions into other scene families. */
public final class FrontierSceneOwnerSupport {
    private FrontierSceneOwnerSupport() { }

    public static SubjectId owner(FrontierWorldState state, SceneLease lease) {
        return FrontierSceneBehaviors.owner(state, lease);
    }

    static boolean isAssault(SceneLease lease) { return FrontierSceneBehaviors.isSettlementAssault(lease); }
}
