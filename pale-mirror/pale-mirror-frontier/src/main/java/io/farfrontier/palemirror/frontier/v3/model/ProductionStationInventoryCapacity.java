package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Semantic port capacity belongs to the machine, not each recipe/hauler caller. */
final class ProductionStationInventoryCapacity {
    static boolean receive(FrontierWorldState state, ProductionStationSpec station, String kind,
                           int quantity, ContainerIncomingStock incoming) {
        if (!incoming.individual().isEmpty()) return false;
        int slot;
        try { slot = ProductionStationRecipe.portForItemKind(state.bootstrap().ruleset().production(), station, kind); }
        catch (IllegalArgumentException unsupported) { return false; }
        if (quantity < 1 || quantity > 64 || state.inventory().occupiedSlots().containsKey(
                new InventoryCustody.ContainerSlot(station.containerId(), slot))) return false;
        var existing = ReferenceContainerCustody.expectedFungibleSlot(state, station.containerId(), slot);
        if (existing.filter(value -> !value.itemKind().equals(kind)).isPresent()) return false;
        long committed = quantity + (long) existing.map(ReferenceContainerCustody.ProjectedFungibleSlot::quantity).orElse(0);
        for (var demand : incoming.fungible().entrySet()) {
            int port;
            try { port = ProductionStationRecipe.portForItemKind(state.bootstrap().ruleset().production(), station, demand.getKey()); }
            catch (IllegalArgumentException unsupported) { return false; }
            if (port == slot) {
                if (!demand.getKey().equals(kind)) return false;
                committed = Math.addExact(committed, demand.getValue());
            }
        }
        return committed <= 64;
    }
    private ProductionStationInventoryCapacity() { }
}
