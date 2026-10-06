package io.farfrontier.palemirror.frontier.v3.model;

/** The authorizing family owns permission/terms; logistics only consumes its exact declaration. */
interface ShipmentAuthorizationPort {
    void validate(FrontierWorldState state, Shipment shipment, boolean admission);
    boolean receptionAccepted(FrontierWorldState state, Shipment shipment, ShipmentReception reception);
    FrontierWorldStateUpdate allocationPartitioned(FrontierWorldState state, Shipment shipment, ResourceClaimPartition partition);
    FrontierWorldStateUpdate allocationDisposed(FrontierWorldState state, Shipment shipment, ExactInventory observedInventory);
    void collect(Shipment shipment, java.util.List<FrontierDomainRelationships.Edge> edges);
    java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> capacityCompletionOwner(Shipment shipment);
}
