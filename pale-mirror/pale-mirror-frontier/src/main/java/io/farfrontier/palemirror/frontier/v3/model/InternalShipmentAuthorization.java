package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Same-owner logistics permission. Neither source commodity nor source facility defines this protocol. */
final class InternalShipmentAuthorization implements ShipmentAuthorizationPort {
    @Override public void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        var grant = shipment.authorization();
        var claim = state.inventory().fungibleResources().claims().get(grant.claimId());
        if (grant.kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || grant.authorizationRevision() != 1
                || shipment.transportMissionId().isPresent() || shipment.mobileContainerId().isPresent()
                || !shipment.sender().settlementId().equals(grant.claimantId())
                || !shipment.receiver().settlementId().equals(grant.claimantId())
                || claim == null || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS
                || !claim.claimantId().equals(grant.claimantId()) || !claim.economicOwnerId().equals(grant.claimantId())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(shipment.lotQuantities()))
            throw new IllegalArgumentException("internal shipment lost its exact same-owner allocation or endpoints");
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
        return Optional.empty();
    }
}
