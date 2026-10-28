package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only complete material backing; no claim and no admission. */
final class ProductionIngredientAvailability {
    static int batches(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe recipe) {
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        var ledger = state.inventory().fungibleResources(); var available = new TreeMap<String, Integer>();
        var kinds = new TreeSet<String>(); recipe.ingredients().forEach(input -> kinds.addAll(input.acceptedItems()));
        for (var kind : kinds) {
            var containers = new HashSet<SubjectId>(ResourceStorageSelection.containers(state, owner, kind, Set.of(ResourceStorageRole.WAREHOUSE)));
            int quantity = 0;
            for (var container : containers) quantity = Math.addExact(quantity,
                    FungibleResourceCustodySupport.accountAtContainer(state, container, ContainerSectionId.STOCK)
                            .map(account -> ledger.unclaimedQuantity(account.id(), owner, kind)).orElse(0));
            quantity = Math.addExact(quantity, FungibleResourceCustodySupport.accountAtContainer(state, station.containerId(), ContainerSectionId.INPUT)
                    .map(account -> ledger.unclaimedQuantity(account.id(), owner, kind)).orElse(0));
            for (var shipment : state.shipments().receiving(station.containerId(), kind))
                if (!shipment.terminal() && shipment.receiver() instanceof ShipmentEndpoint.ProductionPort port
                        && port.port() == ActorContainerItemOrder.StationPort.INPUT)
                    quantity = Math.addExact(quantity, shipment.quantity());
            available.put(kind, quantity);
        }
        for (int count = ProductionBatchSizing.maximumRecipeBatches(recipe); count > 0; count--)
            if (recipe.allocate(available, count).isPresent()) return count;
        return 0;
    }
    private ProductionIngredientAvailability() { }
}
