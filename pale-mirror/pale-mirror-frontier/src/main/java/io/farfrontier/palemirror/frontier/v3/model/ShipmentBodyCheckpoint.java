package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;

/** Saved departure must settle the exact hand before its old route clock can be relinquished. */
final class ShipmentBodyCheckpoint implements ActorActivityBodyCheckpoint {
    @Override public Acknowledgement acknowledge(Request request) {
        var shipment = request.expectedState().shipments().shipments().get(request.execution().activityOwnerId());
        if (shipment == null || !shipment.execution().equals(request.execution())
                || shipment.pendingPhysicalStep().isPresent()
                || request.expectedState().inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(shipment.carriedAccountId())))
            throw new IllegalArgumentException("courier body departure retains an unsettled hand/effect");
        return new ActorMovementBodyCheckpoint().acknowledge(request);
    }
}
