package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Closed declared permission providers. No inspection of a claimant ID to find its family. */
final class ShipmentAuthorizationComposition {
    private static final Map<ResourceClaimDelegation.Kind, ShipmentAuthorizationPort> PORTS = Map.of(
            ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT, new GoodsShipmentAuthorization());
    static {
        if (!PORTS.keySet().equals(java.util.EnumSet.allOf(ResourceClaimDelegation.Kind.class)))
            throw new IllegalArgumentException("shipment authorization registry is incomplete");
    }
    static FrontierWorldStateUpdate allocationDisposed(FrontierWorldState state, Shipment shipment, ExactInventory inventory) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment disposition owner");
        var changes = port.allocationDisposed(state, shipment, inventory);
        if (!java.util.EnumSet.of(FrontierWorldStateUpdate.Component.INVENTORY, FrontierWorldStateUpdate.Component.COMPANIES)
                .containsAll(changes.changedComponents()))
            throw new IllegalArgumentException("shipment disposition owner competed with transport/body authority");
        return changes;
    }
    static void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        ShipmentAuthorizationPort port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment permission provider");
        port.validate(state, shipment, admission);
    }
    private ShipmentAuthorizationComposition() { }
    static FrontierWorldStateUpdate allocationPartitioned(FrontierWorldState state, Shipment shipment, ResourceClaimPartition partition) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment allocation owner");
        return port.allocationPartitioned(state, shipment, partition);
    }
    static boolean receptionAccepted(FrontierWorldState state, Shipment shipment, ShipmentReception reception) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment acknowledgement owner");
        return port.receptionAccepted(state, shipment, reception);
    }
    static java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> capacityCompletionOwner(Shipment shipment) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment storage permission provider");
        return port.capacityCompletionOwner(shipment);
    }
    static void collect(Shipment shipment, java.util.List<FrontierDomainRelationships.Edge> edges) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment relationship provider");
        port.collect(shipment, edges);
    }
}
