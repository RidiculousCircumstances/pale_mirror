package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Warehouse substitutions retain the declared storage filter, capacity and settlement owner. */
final class StorageShipmentReceiverReplacement implements ShipmentReceiverReplacement {
    static final StorageShipmentReceiverReplacement INSTANCE = new StorageShipmentReceiverReplacement();
    @Override public boolean retainedAvailable(FrontierWorldState state, Shipment shipment) {
        var container = state.inventory().containers().get(shipment.receiver().containerId());
        return container != null && container.storage().map(policy -> policy.endpoint().equals(shipment.receiver())
                && policy.filter().accepts(shipment.itemKind())).orElse(false)
                && !ReferenceContainerCustody.blocksCanonicalUse(state, container.id())
                && ContainerStorageAdmission.receive(state, container.id(), shipment.itemKind(), shipment.quantity(), Optional.of(shipment.id()));
    }
    @Override public List<ShipmentEndpoint> candidates(FrontierWorldState state, Shipment shipment, SurfaceAnchor origin) {
        return ResourceStorageSelection.receivingCandidates(state, shipment.receiver().settlementId(), shipment.itemKind(),
                shipment.quantity(), origin, Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty());
    }
    @Override public boolean admits(FrontierWorldState state, Shipment shipment, ShipmentEndpoint replacement) {
        return ResourceStorageSelection.receivesAt(state, replacement, shipment.receiver().settlementId(), shipment.itemKind(),
                shipment.quantity(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty());
    }
    private StorageShipmentReceiverReplacement() { }
}
