package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Same-owner logistics permission. Neither source commodity nor source facility defines this protocol. */
final class InternalShipmentAuthorization extends SameOwnerShipmentAuthorization {
    @Override public boolean departureReady(Shipment shipment,TransportOrganization organization) {
        return organization.itinerary()==TransportOrganization.Itinerary.SOURCE_DELIVERY
                && organization.sponsor().id().equals(shipment.authorization().claimantId());
    }
    @Override public void validateMission(FrontierWorldState state,Shipment shipment,TransportOrganization organization) {
        organization.require(state);
        if(!organization.home().equals(shipment.sender()) || !organization.sponsor().id().equals(shipment.authorization().claimantId())
                || organization.itinerary()!=TransportOrganization.Itinerary.SOURCE_DELIVERY)
            throw new IllegalArgumentException("internal manifest has foreign transport organization");
    }
    @Override public List<ImmutableReferenceIndex.Read<FrontierWorldState>> continuationValidationReads(FrontierWorldState state, Shipment shipment) {
        return List.of(ShipmentValidationReads.CLAIMS.at(shipment.authorization().claimId()));
    }
    @Override public void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        var grant = shipment.authorization();
        var claim = state.inventory().fungibleResources().claims().get(grant.claimId());
        if (grant.kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || grant.authorizationRevision() != 1
                || (shipment.execution().activityKind()==io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER
                    ? shipment.transportMissionId().isEmpty() || shipment.mobileContainerId().isEmpty()
                    : shipment.execution().activityKind()!=io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.COURIER
                        || shipment.transportMissionId().isPresent() || shipment.mobileContainerId().isPresent())
                || !shipment.sender().settlementId().equals(grant.claimantId())
                || !shipment.receiver().settlementId().equals(grant.claimantId())
                || claim == null || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS
                || !claim.claimantId().equals(grant.claimantId()) || !claim.economicOwnerId().equals(grant.claimantId())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(shipment.lotQuantities()))
            throw new IllegalArgumentException("internal shipment lost its exact same-owner allocation or endpoints");
        if (admission && !ContainerStorageAdmission.receive(state, (ResourceCustody.Container) shipment.receiver().custody(), shipment.itemKind(),
                shipment.quantity(), Optional.empty()))
            throw new IllegalArgumentException("internal shipment has no receiving storage capacity for its exact load");
    }
}
