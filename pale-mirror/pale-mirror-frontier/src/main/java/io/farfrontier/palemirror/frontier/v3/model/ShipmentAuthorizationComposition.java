package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Closed declared permission providers. No inspection of a claimant ID to find its family. */
final class ShipmentAuthorizationComposition {
    private static final Map<ResourceClaimDelegation.Kind, ShipmentAuthorizationPort> PORTS = Map.of(
            ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT, new GoodsShipmentAuthorization(),
            ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT, new InternalShipmentAuthorization(),
            ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT, new OwnedGoodsShipmentAuthorization());
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
    static void validateMission(FrontierWorldState state, Shipment shipment, TransportOrganization organization) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment mission permission provider");
        port.validateMission(state, shipment, organization);
    }
    static boolean departureReady(FrontierWorldState state, Shipment shipment, TransportOrganization organization) {
        var port = PORTS.get(shipment.authorization().kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment departure permission provider");
        port.validate(state, shipment, false);
        return port.departureReady(shipment, organization);
    }
    private ShipmentAuthorizationComposition() { }
    static boolean recallable(FrontierWorldState state, Shipment shipment) {
        return PORTS.get(shipment.authorization().kind()).recallable(state, shipment);
    }
    static FrontierWorldStateUpdate cancelledAtSource(FrontierWorldState state, Shipment shipment,
            ShipmentReception receipt, FrontierWorldStateUpdate transferred) {
        var owned = java.util.EnumSet.of(FrontierWorldStateUpdate.Component.INVENTORY, FrontierWorldStateUpdate.Component.COMPANIES);
        if (!owned.containsAll(transferred.changedComponents()))
            throw new IllegalArgumentException("source cancellation cannot discard unrelated transfer contributions");
        var result = PORTS.get(shipment.authorization().kind()).cancelledAtSource(state, shipment, receipt,
                transferred.inventory(state), transferred.companies(state));
        if (!owned.equals(result.changedComponents())) throw new IllegalArgumentException("return issuer omitted custody or competed with logistics authority");
        return result;
    }
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
        if (shipment.status() == Shipment.Status.RETURNING) return java.util.Optional.empty();
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
