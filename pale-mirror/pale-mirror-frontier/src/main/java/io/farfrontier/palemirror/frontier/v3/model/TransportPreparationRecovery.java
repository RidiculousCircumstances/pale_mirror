package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;

/** Unfunded material preparation has an explicit reversible outcome, not an endless cargo hold.
 * No timeout invents a delivery or destroys goods; actual unloading closes each retained parcel. */
public final class TransportPreparationRecovery {
    public static boolean needed(FrontierWorldState state, TransportMission mission, long now) {
        if (!mission.preparingSupplies() || mission.supplies().isEmpty() || mission.replenishment().isPresent()
                || !ExpeditionSupplyAuthority.mayReconsider(state, mission)) return false;
        boolean outstanding = false;
        for (var id : mission.shipmentIds()) {
            var shipment = state.shipments().shipments().get(id);
            if (shipment.terminal()) continue;
            outstanding = true;
            if (shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent()
                    || !(shipment.sender() instanceof ShipmentEndpoint)
                    || !shipment.sender().settlementId().equals(mission.organization().home().settlementId())
                    || !ShipmentAuthorizationComposition.recallable(state, shipment)
                    || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(id))) return false;
        }
        return outstanding && !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyPlanning.supplyAvailableForRetainedJourney(
                state, mission, state.unitGroups().groups().get(mission.groupId()), now, ExpeditionSupplyAuthority.releaseForAbort(state, mission));
    }
    public static FrontierWorldState recall(FrontierWorldState state, TransportMission mission, long now) {
        if (!needed(state, mission, now)) throw new IllegalArgumentException("expedition has no current unrecoverable preparation");
        var changes = FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(
                ExpeditionSupplyAuthority.releaseForAbort(state, mission)));
        var shipments = state.shipments(); var executions = state.actorExecutions();
        for (var id : mission.shipmentIds()) {
            var shipment = shipments.shipments().get(id);
            if (shipment.terminal()) continue;
            if (shipment.status() == Shipment.Status.AWAITING_LOAD) {
                changes = ShipmentAuthorizationComposition.cancelledAtSource(state, shipment,
                        ShipmentReception.unloaded(shipment, shipment.lotQuantities()), changes);
                shipments = shipments.replace(shipment, shipment.withStatus(Shipment.Status.ALLOCATION_WITHDRAWN));
                executions = TransportCargoExecution.afterParcelClosed(executions, shipment);
            } else shipments = shipments.replace(shipment, shipment.withStatus(Shipment.Status.RETURNING));
        }
        return state.withChanges(changes.shipments(shipments.recallMission(mission)).actorExecutions(executions));
    }
    private TransportPreparationRecovery() { }
}
