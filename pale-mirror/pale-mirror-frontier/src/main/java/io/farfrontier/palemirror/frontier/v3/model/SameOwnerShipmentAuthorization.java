package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Shared custody completion/partition/loss protocol; concrete grants declare their admission rules. */
abstract class SameOwnerShipmentAuthorization implements ShipmentAuthorizationPort {
    @Override public boolean departureReady(Shipment shipment, TransportOrganization organization) { return false; }
    @Override public void validateMission(FrontierWorldState state, Shipment shipment, TransportOrganization organization) {
        throw new IllegalArgumentException("same-owner shipments cannot originate a commercial mission");
    }
    @Override public boolean receptionAccepted(FrontierWorldState state, Shipment shipment, ShipmentReception reception) {
        return false; // Its own atomic receipt transition releases the claim and clears this exact reception.
    }
    @Override public FrontierWorldStateUpdate allocationPartitioned(FrontierWorldState state, Shipment shipment, ResourceClaimPartition partition) {
        validate(state, shipment, false);
        if (!partition.accountId().equals(shipment.carriedAccountId()) || !partition.claimId().equals(shipment.authorization().claimId()))
            throw new IllegalArgumentException("internal cargo partition has foreign authority");
        return FrontierWorldStateUpdate.begin(); // The existing inventory owns the actual pinned partition.
    }
    @Override public FrontierWorldStateUpdate allocationDisposed(FrontierWorldState state, Shipment shipment, ExactInventory inventory) {
        validate(state, shipment, false);
        if (inventory.fungibleResources().claims().containsKey(shipment.authorization().claimId()))
            throw new IllegalArgumentException("internal cargo disposition did not release its exact allocation");
        return FrontierWorldStateUpdate.begin().inventory(inventory);
    }
    @Override public void collect(Shipment shipment, List<FrontierDomainRelationships.Edge> edges) {
        var owner = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.SHIPMENT, shipment.id());
        edges.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SHIPMENT_ISSUER, owner, owner,
                new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.ECONOMIC_ACCOUNT, shipment.authorization().claimantId()),
                FrontierDomainRelationships.Lifecycle.ACTIVE, "shipment:" + shipment.id().value()));
    }
    @Override public Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> capacityCompletionOwner(Shipment shipment) {
        return Optional.of(shipment.id());
    }
}
