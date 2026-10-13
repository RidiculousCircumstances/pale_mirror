package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** An installed machine may be replaced after witnessed retirement, never by a warehouse or another facility. */
final class ProductionShipmentReceiverReplacement implements ShipmentReceiverReplacement {
    static final ProductionShipmentReceiverReplacement INSTANCE = new ProductionShipmentReceiverReplacement();
    private ShipmentEndpoint.ProductionPort receiver(Shipment shipment) {
        if (!(shipment.receiver() instanceof ShipmentEndpoint.ProductionPort port))
            throw new IllegalArgumentException("machine substitution has a foreign receiver declaration");
        return port;
    }
    @Override public boolean retainedAvailable(FrontierWorldState state, Shipment shipment) {
        var old = receiver(shipment);
        // Fuel shortage, capacity or transient physical custody are waits at this exact instance,
        // not permission to move the promised resource to a different destination.
        return state.inventory().surfaces().get(old.containerId()).status() != ContainerSurfaceStatus.RETIRED;
    }
    @Override public List<ShipmentEndpoint> candidates(FrontierWorldState state, Shipment shipment, SurfaceAnchor origin) {
        if (retainedAvailable(state, shipment)) return List.of();
        var old = receiver(shipment);
        return state.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream())
                .filter(spec -> spec.facilityId().equals(old.spec().facilityId()) && spec.capability() == old.spec().capability())
                .sorted(Comparator.comparing(ProductionStationSpec::id))
                .map(spec -> (ShipmentEndpoint) new ShipmentEndpoint.ProductionPort(old.settlementId(), spec, old.port(), old.exteriorApproach()))
                .filter(endpoint -> admits(state, shipment, endpoint)).toList();
    }
    @Override public boolean admits(FrontierWorldState state, Shipment shipment, ShipmentEndpoint replacement) {
        var old = receiver(shipment);
        if (retainedAvailable(state, shipment) || !(replacement instanceof ShipmentEndpoint.ProductionPort next)
                || !old.settlementId().equals(next.settlementId()) || old.port() != next.port()
                || old.spec().id().equals(next.spec().id()) || !old.spec().facilityId().equals(next.spec().facilityId())
                || old.spec().capability() != next.spec().capability() || !old.exteriorApproach().equals(next.exteriorApproach())
                || !state.productionStations().machines().containsKey(next.spec().id())) return false;
        var record = state.inventory().containers().get(next.containerId());
        if (record == null || !record.ownerId().equals(next.settlementId()) || !record.productionStation().equals(Optional.of(next.spec()))) return false;
        ShipmentEndpointComposition.validate(state, next);
        try {
            if (ProductionStationRecipe.portForItemKind(state.bootstrap().ruleset().production(), next.spec(), shipment.itemKind())
                    != next.spec().slot(next.port())) return false;
        } catch (IllegalArgumentException unsupported) { return false; }
        return ContainerStorageAdmission.receive(state, next.containerId(), shipment.itemKind(), shipment.quantity(), Optional.empty());
    }
    private ProductionShipmentReceiverReplacement() { }
}
