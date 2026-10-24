package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only material backing for a station, never a claim or a production admission. */
final class ProductionIngredientAvailability {
    static long forStation(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe recipe) {
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        var ledger = state.inventory().fungibleResources();
        long quantity = 0;
        for (var kind : recipe.ingredient().acceptedItems()) {
            var containers = new HashSet<SubjectId>(ResourceStorageSelection.containers(state, owner, kind,
                    Set.of(ResourceStorageRole.WAREHOUSE)));
            containers.add(station.containerId());
            for (var container : containers) quantity = Math.addExact(quantity,
                    FungibleResourceCustodySupport.accountAtContainer(state, container)
                            .map(account -> (long) ledger.unclaimedQuantity(account.id(), owner, kind)).orElse(0L));
        }
        for (var shipment : state.shipments().shipments().values()) {
            if (!shipment.terminal() && shipment.receiver().containerId().equals(station.containerId())
                    && recipe.ingredient().accepts(shipment.itemKind()))
                quantity = Math.addExact(quantity, shipment.quantity());
        }
        return quantity;
    }
    private ProductionIngredientAvailability() { }
}
