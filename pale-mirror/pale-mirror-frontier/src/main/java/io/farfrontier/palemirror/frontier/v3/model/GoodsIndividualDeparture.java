package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Observed external removal closes the promise, never deletes or recreates the actual item. */
public final class GoodsIndividualDeparture {
    private GoodsIndividualDeparture() { }
    public static boolean committed(FrontierWorldState state, SubjectId item) {
        return contract(state, item) != null || shipment(state, item) != null;
    }
    private static GoodsTradeContract contract(FrontierWorldState state, SubjectId item) {
        return state.companies().goodsTrade().contracts().values().stream()
                .filter(c -> c.individualAllocations().values().stream().anyMatch(a -> a.itemId().equals(item)))
                .reduce((a, b) -> { throw new IllegalArgumentException("individual item has competing commercial claims"); }).orElse(null);
    }
    private static Shipment shipment(FrontierWorldState state, SubjectId item) {
        return state.shipments().shipments().values().stream().filter(s -> !s.terminal() || s.reception().isPresent())
                .filter(s -> s.cargo() instanceof ShipmentCargo.Individual cargo && cargo.itemId().equals(item))
                .reduce((a, b) -> { throw new IllegalArgumentException("individual item has competing transport promises"); }).orElse(null);
    }
    public static FrontierWorldState observe(FrontierWorldState state, SubjectId itemId, InventoryCustody source, ExactInventory inventory) {
        var before = state.inventory().items().get(itemId); var after = inventory.items().get(itemId);
        var contract = contract(state, itemId); var shipment = shipment(state, itemId);
        if (before == null || !before.custody().equals(source) || contract == null && shipment == null)
            throw new IllegalArgumentException("individual goods departure lacks its exact predecessor");
        if (shipment != null && shipment.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("individual goods departure cannot cross an unresolved physical transfer");
        // Rearranging one warehouse does not revoke the promise or retain a permanent source slot.
        if (after != null && after.economicOwnerId().equals(before.economicOwnerId()) && after.itemKind().equals(before.itemKind())
                && after.condition().equals(before.condition()) && before.custody() instanceof InventoryCustody.ContainerSlot a
                && after.custody() instanceof InventoryCustody.ContainerSlot b && a.containerId().equals(b.containerId()))
            return state.withInventory(inventory);
        var companies = state.companies();
        if (contract != null) {
            var allocation = contract.individualAllocations().values().stream().filter(a -> a.itemId().equals(itemId)).findFirst().orElseThrow();
            var receipt = new SubjectId("receipt:individual-loss/" + UUID.nameUUIDFromBytes((contract.id().value() + "|" + contract.revision()
                    + "|" + itemId.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var disposition = new GoodsTradeDisposition(receipt, contract.id(), contract.revision(), allocation.id(), 1,
                    GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED);
            companies = companies.withGoodsTrade(companies.goodsTrade().dispose(disposition));
            inventory = inventory.withEconomics(inventory.economics().releasePortion(contract.financialReservationId(), contract.deliveredUnitPrice()));
        }
        var changes = FrontierWorldStateUpdate.begin().inventory(inventory).companies(companies);
        if (shipment != null) {
            var replacement = shipment.reception().isPresent() ? shipment.receivedItemLost()
                    : shipment.withStatus(shipment.carrying() ? Shipment.Status.CARGO_DISPOSED : Shipment.Status.ALLOCATION_WITHDRAWN);
            var movements = new LinkedHashMap<>(state.actorMovements());
            var movement = movements.get(shipment.execution().actorId());
            if (movement != null && movement.order().ownerId().equals(shipment.id())) movements.remove(shipment.execution().actorId());
            changes.shipments(state.shipments().replace(shipment, replacement)).actorMovements(movements);
            if (!shipment.terminal()) changes.actorExecutions(TransportCargoExecution.afterParcelClosed(state, shipment));
        }
        return state.withChanges(changes);
    }
}
