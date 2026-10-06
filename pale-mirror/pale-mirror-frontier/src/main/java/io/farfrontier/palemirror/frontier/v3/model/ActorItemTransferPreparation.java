package io.farfrontier.palemirror.frontier.v3.model;
import java.util.Objects;

/** Owner-approved allocation preparation, committed only together with the common custody transfer. */
record ActorItemTransferPreparation(ExactInventory inventory, ActorContainerItemOrder order, FrontierWorldStateUpdate ownerChanges) {
    ActorItemTransferPreparation {
        Objects.requireNonNull(inventory); Objects.requireNonNull(order); Objects.requireNonNull(ownerChanges);
        if (ownerChanges.changedComponents().contains(FrontierWorldStateUpdate.Component.INVENTORY)
                || ownerChanges.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_LOCATIONS)
                || ownerChanges.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_EXECUTIONS))
            throw new IllegalArgumentException("allocation preparation competes with stock or body authority");
    }
    static ActorItemTransferPreparation unchanged(FrontierWorldState state, ActorContainerItemOrder order) {
        return new ActorItemTransferPreparation(state.inventory(), order, FrontierWorldStateUpdate.begin());
    }
}
