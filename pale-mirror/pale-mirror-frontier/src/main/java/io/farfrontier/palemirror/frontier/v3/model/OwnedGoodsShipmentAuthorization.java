package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Paid pickup cargo: the buyer owns the allocation, while its expedition owns transport. */
final class OwnedGoodsShipmentAuthorization extends SameOwnerShipmentAuthorization {
    @Override public boolean departureReady(Shipment shipment, TransportOrganization organization) {
        return organization.itinerary() == TransportOrganization.Itinerary.DESTINATION_COLLECTION;
    }
    @Override public void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        var grant = shipment.authorization();
        var claim = state.inventory().fungibleResources().claims().get(grant.claimId());
        var mission = shipment.transportMissionId().map(state.shipments().missions()::get).orElse(null);
        var buyer = state.companies().goodsTrade().participants().participants().get(grant.claimantId());
        if (admission || grant.kind() != ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT || grant.authorizationRevision() != 1
                || mission == null || mission.organization().itinerary() != TransportOrganization.Itinerary.DESTINATION_COLLECTION
                || !mission.shipmentIds().contains(shipment.id()) || buyer == null
                || !buyer.settlementId().equals(shipment.receiver().settlementId())
                || !mission.organization().home().equals(shipment.receiver())
                || claim == null || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS
                || !claim.claimantId().equals(grant.claimantId()) || !claim.economicOwnerId().equals(grant.claimantId())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(shipment.lotQuantities()))
            throw new IllegalArgumentException("owned goods transport lacks its paid buyer allocation and exact collection mission");
    }
}
