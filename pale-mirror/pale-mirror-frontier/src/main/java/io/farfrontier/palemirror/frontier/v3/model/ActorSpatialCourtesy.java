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
        // Inventory interactions can belong to a separate registered owner (for example
        // expedition provisioning). A local detour cannot invalidate its physical receipt.
        var pendingInventory = ActorInventoryInteractionFences.pendingOwner(state, execution.actorId());
        if (checkpoint.ready() && pendingInventory.isPresent())
            return new ActorActivityCheckpoint(state, execution, java.util.Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, pendingInventory.orElseThrow())));
        return checkpoint;
    }
}
