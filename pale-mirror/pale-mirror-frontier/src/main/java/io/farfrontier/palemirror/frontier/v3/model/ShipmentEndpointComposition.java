package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.function.BiConsumer;

/** Facility capabilities validate a declared physical station; carriers do not guess geometry. */
public final class ShipmentEndpointComposition {
    private static final Map<ShipmentEndpoint.Kind, BiConsumer<FrontierWorldState, ShipmentEndpoint>> PORTS = Map.of(
            ShipmentEndpoint.Kind.SETTLEMENT_DEPOT, ShipmentEndpointComposition::depot);
    public static void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var port = PORTS.get(endpoint.kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment endpoint provider");
        port.accept(state, endpoint);
    }
    private static void depot(FrontierWorldState state, ShipmentEndpoint endpoint) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), endpoint.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(s -> s.id().equals(endpoint.facilityId()) && s.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("shipment declared an unknown settlement depot"));
        if (!FrontierWorldState.depotId(endpoint.settlementId()).equals(endpoint.containerId())
                || !state.inventory().containers().containsKey(endpoint.containerId())
                || !SettlementDepotServicePort.forDepot(depot).ownedAccessSurfaces().contains(endpoint.station()))
            throw new IllegalArgumentException("shipment endpoint is not a declared depot service station");
    }
    private ShipmentEndpointComposition() { }
}
