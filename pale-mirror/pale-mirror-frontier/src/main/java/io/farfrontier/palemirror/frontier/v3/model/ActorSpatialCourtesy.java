package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Common spatial arbitration delegates safety to the exact owner, never inspects family progress. */
public final class ActorSpatialCourtesy {
    private ActorSpatialCourtesy() { }

    public static ActorActivityCheckpoint assess(FrontierWorldState state, ActorExecutionId execution) {
        state.actorExecutions().requireCurrent(execution);
        var owner = ActorExecutionComposition.CAPABILITIES.require(execution.activityKind());
        owner.validateReference(state, execution);
        var checkpoint = owner.spatialYieldCheckpoint(state, execution);
        checkpoint.validate(state, execution);
        return checkpoint;
    }
}
